// JNI bridge from Kepler Slicer to open-bamboo-networking (compiled into libbambu_networking.so, so it shares the
// plugin's C++ runtime: the plugin ABI passes std::string and std::function by value).
//
// One agent per process, as in the desktop slicers. The plugin delivers MQTT messages through callbacks; they are
// queued here per printer and read by Java with a timeout, so no Java code runs on the plugin's threads.

#include <jni.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <deque>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "obn/bambu_networking.hpp"

extern "C" {
void* bambu_network_create_agent(std::string log_dir);
int bambu_network_set_config_dir(void* agent, std::string config_dir);
int bambu_network_set_cert_file(void* agent, std::string folder, std::string filename);
int bambu_network_start(void* agent);
int bambu_network_connect_printer(void* agent, std::string dev_id, std::string dev_ip, std::string username, std::string password, bool use_ssl);
int bambu_network_disconnect_printer(void* agent);
int bambu_network_send_message_to_printer(void* agent, std::string dev_id, std::string json_str, int qos, int flag);
int bambu_network_set_on_local_connect_fn(void* agent, BBL::OnLocalConnectedFn fn);
int bambu_network_set_on_local_message_fn(void* agent, BBL::OnMessageFn fn);
int bambu_network_start_send_gcode_to_sdcard(void* agent, BBL::PrintParams params, BBL::OnUpdateStatusFn update_fn, BBL::WasCancelledFn cancel_fn, BBL::OnWaitFn wait_fn);
int bambu_network_set_country_code(void* agent, std::string country_code);
int bambu_network_change_user(void* agent, std::string user_info);
int bambu_network_connect_server(void* agent);
bool bambu_network_is_server_connected(void* agent);
int bambu_network_add_subscribe(void* agent, std::vector<std::string> dev_list);
int bambu_network_send_message(void* agent, std::string dev_id, std::string json_str, int qos, int flag);
int bambu_network_set_on_message_fn(void* agent, BBL::OnMessageFn fn);
int bambu_network_set_on_server_connected_fn(void* agent, BBL::OnServerConnectedFn fn);
}

namespace {
std::mutex g_lock;
std::condition_variable g_signal;
void* g_agent = nullptr;
std::map<std::string, std::deque<std::string>> g_messages;   // serial -> reports, oldest first
std::map<std::string, int> g_connect;                        // serial -> last local connect status
std::map<std::string, std::string> g_connect_msg;              // serial -> plugin message of the last connect
std::string g_connected;                                     // serial of the open LAN session
std::atomic<bool> g_cancel{false};
bool g_connecting = false;                                    // a LAN connect thread is running
int g_server = 1;                                             // cloud broker: 0 connected
std::string g_cloud_user;                                     // user_info of the open cloud session
constexpr size_t kMaxQueued = 64;

std::string utf8(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeInit(JNIEnv* env, jclass, jstring log_dir, jstring config_dir, jstring cert_dir, jstring cert_file) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (g_agent) return JNI_TRUE;
    void* agent = bambu_network_create_agent(utf8(env, log_dir));
    if (!agent) return JNI_FALSE;
    bambu_network_set_config_dir(agent, utf8(env, config_dir));
    bambu_network_set_cert_file(agent, utf8(env, cert_dir), utf8(env, cert_file));
    bambu_network_set_on_local_connect_fn(agent, [](int status, std::string dev_id, std::string msg) {
        std::lock_guard<std::mutex> g(g_lock); g_connect[dev_id] = status; g_connect_msg[dev_id] = std::move(msg); g_signal.notify_all();
    });
    bambu_network_set_on_local_message_fn(agent, [](std::string dev_id, std::string msg) {
        std::lock_guard<std::mutex> g(g_lock);
        auto& q = g_messages[dev_id]; q.push_back(std::move(msg)); while (q.size() > kMaxQueued) q.pop_front();
        g_signal.notify_all();
    });
    // Cloud reports arrive on the account broker; they share the per-printer queue with LAN reports.
    bambu_network_set_on_message_fn(agent, [](std::string dev_id, std::string msg) {
        std::lock_guard<std::mutex> g(g_lock);
        auto& q = g_messages[dev_id]; q.push_back(std::move(msg)); while (q.size() > kMaxQueued) q.pop_front();
        g_signal.notify_all();
    });
    bambu_network_set_on_server_connected_fn(agent, [](int code, int) { std::lock_guard<std::mutex> g(g_lock); g_server = code; g_signal.notify_all(); });
    bambu_network_start(agent);
    g_agent = agent;
    return JNI_TRUE;
}

// Opens (or keeps) the LAN session to one printer. Returns the plugin status: 0 connected, <0 failure.
// The plugin connects synchronously and a TCP connect to an absent host can block for minutes, so the connect runs
// on its own thread and only the wait here is bounded. One connect at a time.
extern "C" JNIEXPORT jint JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeConnect(JNIEnv* env, jclass, jstring serial, jstring ip, jstring user, jstring code, jint timeout_ms) {
    std::string dev = utf8(env, serial), dev_ip = utf8(env, ip), name = utf8(env, user), secret = utf8(env, code);
    void* agent;
    std::unique_lock<std::mutex> guard(g_lock);
    agent = g_agent; if (!agent) return BAMBU_NETWORK_ERR_INVALID_HANDLE;
    auto it = g_connect.find(dev);
    if (g_connected == dev && it != g_connect.end() && it->second == 0) return 0;
    if (!g_signal.wait_for(guard, std::chrono::milliseconds(timeout_ms), [] { return !g_connecting; })) return BAMBU_NETWORK_ERR_TIMEOUT;
    g_connect.erase(dev); g_connect_msg.erase(dev); g_messages.erase(dev);
    g_connecting = true;
    std::string previous = g_connected; g_connected.clear();
    std::thread([agent, dev, dev_ip, name, secret, previous] {
        if (!previous.empty()) bambu_network_disconnect_printer(agent);
        int rc = bambu_network_connect_printer(agent, dev, dev_ip, name, secret, true);
        std::lock_guard<std::mutex> g(g_lock);
        if (rc != 0 && !g_connect.count(dev)) { g_connect[dev] = 1; g_connect_msg[dev] = "plugin status " + std::to_string(rc); }
        g_connecting = false;
        g_signal.notify_all();
    }).detach();
    bool done = g_signal.wait_for(guard, std::chrono::milliseconds(timeout_ms), [&] { return g_connect.count(dev) > 0; });
    if (!done) return BAMBU_NETWORK_ERR_TIMEOUT;
    int status = g_connect[dev];
    if (status == 0) g_connected = dev;
    return status == 0 ? 0 : BAMBU_NETWORK_ERR_CONNECT_FAILED;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeSend(JNIEnv* env, jclass, jstring serial, jstring json) {
    void* agent; { std::lock_guard<std::mutex> g(g_lock); agent = g_agent; }
    if (!agent) return BAMBU_NETWORK_ERR_INVALID_HANDLE;
    return bambu_network_send_message_to_printer(agent, utf8(env, serial), utf8(env, json), 0, 0);
}

// Next report of the printer, or null after the timeout.
extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeNextMessage(JNIEnv* env, jclass, jstring serial, jint timeout_ms) {
    std::string dev = utf8(env, serial);
    std::unique_lock<std::mutex> guard(g_lock);
    bool ready = g_signal.wait_for(guard, std::chrono::milliseconds(timeout_ms), [&] { auto it = g_messages.find(dev); return it != g_messages.end() && !it->second.empty(); });
    if (!ready) return nullptr;
    std::string msg = std::move(g_messages[dev].front()); g_messages[dev].pop_front();
    return env->NewStringUTF(msg.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeDrop(JNIEnv* env, jclass, jstring serial) {
    std::lock_guard<std::mutex> g(g_lock); g_messages.erase(utf8(env, serial));
}

extern "C" JNIEXPORT void JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeCancel(JNIEnv*, jclass) { g_cancel = true; }

// Uploads a local file to the printer storage over FTPS without starting it. Returns the plugin status.
extern "C" JNIEXPORT jint JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeUpload(JNIEnv* env, jclass, jstring serial, jstring ip, jstring code, jstring local_path, jstring remote_name) {
    void* agent; { std::lock_guard<std::mutex> g(g_lock); agent = g_agent; }
    if (!agent) return BAMBU_NETWORK_ERR_INVALID_HANDLE;
    g_cancel = false;
    BBL::PrintParams p{};
    p.dev_id = utf8(env, serial); p.dev_ip = utf8(env, ip); p.username = "bblp"; p.password = utf8(env, code);
    p.filename = utf8(env, local_path); p.dst_file = utf8(env, remote_name); p.ftp_file = p.dst_file; p.task_name = p.dst_file;
    p.connection_type = "lan"; p.use_ssl_for_ftp = true; p.use_ssl_for_mqtt = true; p.plate_index = 1;
    return bambu_network_start_send_gcode_to_sdcard(agent, p, [](int, int, std::string) {}, [] { return g_cancel.load(); },
        [](int, std::string) { return true; });
}

// Signs the plugin in with an account token (desktop "change_user" envelope) and connects the account MQTT broker.
// countryCode "CN" selects the China service. Returns 0 when connected.
extern "C" JNIEXPORT jint JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeCloudConnect(JNIEnv* env, jclass, jstring user_info, jstring country, jint timeout_ms) {
    void* agent; std::string info = utf8(env, user_info);
    {
        std::lock_guard<std::mutex> g(g_lock); agent = g_agent; if (!agent) return BAMBU_NETWORK_ERR_INVALID_HANDLE;
        if (g_cloud_user == info && g_server == 0 && bambu_network_is_server_connected(agent)) return 0;
        g_server = 1;
    }
    bambu_network_set_country_code(agent, utf8(env, country));
    int rc = bambu_network_change_user(agent, info);
    if (rc != 0) return rc;
    rc = bambu_network_connect_server(agent);
    if (rc != 0) return rc;
    std::unique_lock<std::mutex> guard(g_lock);
    bool done = g_signal.wait_for(guard, std::chrono::milliseconds(timeout_ms), [] { return g_server == 0; });
    if (!done) return bambu_network_is_server_connected(agent) ? 0 : BAMBU_NETWORK_ERR_TIMEOUT;
    g_cloud_user = info;
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeCloudWatch(JNIEnv* env, jclass, jstring serial) {
    void* agent; { std::lock_guard<std::mutex> g(g_lock); agent = g_agent; }
    if (!agent) return BAMBU_NETWORK_ERR_INVALID_HANDLE;
    return bambu_network_add_subscribe(agent, {utf8(env, serial)});
}

extern "C" JNIEXPORT jint JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeCloudSend(JNIEnv* env, jclass, jstring serial, jstring json) {
    void* agent; { std::lock_guard<std::mutex> g(g_lock); agent = g_agent; }
    if (!agent) return BAMBU_NETWORK_ERR_INVALID_HANDLE;
    return bambu_network_send_message(agent, utf8(env, serial), utf8(env, json), 0, 0);
}

// Plugin message of the last LAN connect attempt (empty when none), for error reports.
extern "C" JNIEXPORT jstring JNICALL
Java_com_keplerslicer_engine_BambuNetworking_nativeLastError(JNIEnv* env, jclass, jstring serial) {
    std::lock_guard<std::mutex> g(g_lock);
    auto it = g_connect_msg.find(utf8(env, serial));
    return env->NewStringUTF(it == g_connect_msg.end() ? "" : it->second.c_str());
}
