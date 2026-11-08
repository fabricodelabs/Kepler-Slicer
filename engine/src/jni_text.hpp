#pragma once
// Text handed to Java through JNI. NewStringUTF and ThrowNew read Modified UTF-8: a code point above U+FFFF (emoji
// in an object or profile name), a malformed byte or an embedded NUL makes CheckJNI abort the process, and the
// result is undefined without it. Engine messages and printer reports can carry such bytes (file names, 3MF metadata,
// MQTT payloads from the network), so every string is reduced to valid BMP UTF-8 first; anything else becomes '?'.

#include <jni.h>

#include <string>

namespace kepler {

inline std::string jni_safe_utf8(const char *text)
{
    std::string out;
    if (!text) return out;
    const std::string in(text);
    out.reserve(in.size());
    const size_t n = in.size();
    size_t i = 0;
    while (i < n) {
        const unsigned char c = static_cast<unsigned char>(in[i]);
        if (c < 0x80) { out += static_cast<char>(c); ++i; continue; }
        size_t len = (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 0;
        bool ok = len != 0 && i + len <= n;
        for (size_t k = 1; ok && k < len; ++k) ok = (static_cast<unsigned char>(in[i + k]) & 0xC0) == 0x80;
        if (ok && len < 4) {
            const unsigned cp = len == 2 ? ((c & 0x1Fu) << 6) | (static_cast<unsigned char>(in[i + 1]) & 0x3Fu)
                : ((c & 0x0Fu) << 12) | ((static_cast<unsigned char>(in[i + 1]) & 0x3Fu) << 6) | (static_cast<unsigned char>(in[i + 2]) & 0x3Fu);
            ok = !(len == 2 && cp < 0x80) && !(len == 3 && cp < 0x800) && !(cp >= 0xD800 && cp <= 0xDFFF);
        }
        if (ok && len < 4) { out.append(in, i, len); i += len; continue; }
        out += '?';
        i += ok ? len : 1;   // a whole supplementary character, or one bad byte
    }
    return out;
}

inline jstring jni_string(JNIEnv *env, const char *text) { return env->NewStringUTF(jni_safe_utf8(text).c_str()); }

inline jint jni_throw_new(JNIEnv *env, jclass type, const char *message)
{
    return type ? env->ThrowNew(type, jni_safe_utf8(message).c_str()) : -1;
}

} // namespace kepler
