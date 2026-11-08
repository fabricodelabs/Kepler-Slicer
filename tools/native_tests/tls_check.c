// TLS check for the Bambu plugin dependencies (OpenSSL 3.5.9 + curl 8.23.0, audit SEC-08): runs on the emulator
// against a local test server (steps in audit/TEST_RESULTS.md). Trusted CA → 200; other CA → refused;
// wrong host name → refused. No external servers are contacted.
#include <curl/curl.h>
#include <openssl/opensslv.h>
#include <stdio.h>
#include <string.h>

static size_t sink(char *p, size_t s, size_t n, void *u) { (void)p; (void)u; return s * n; }

static CURLcode get(const char *url, const char *ca) {
    CURL *c = curl_easy_init(); long code = 0;
    curl_easy_setopt(c, CURLOPT_URL, url);
    curl_easy_setopt(c, CURLOPT_CAINFO, ca);
    curl_easy_setopt(c, CURLOPT_WRITEFUNCTION, sink);
    curl_easy_setopt(c, CURLOPT_TIMEOUT, 10L);
    CURLcode rc = curl_easy_perform(c);
    curl_easy_getinfo(c, CURLINFO_RESPONSE_CODE, &code);
    curl_easy_cleanup(c);
    printf("  %s with %s -> %d (%s), HTTP %ld\n", url, strrchr(ca, '/') + 1, rc, curl_easy_strerror(rc), code);
    return rc;
}

int main(int argc, char **argv) {
    if (argc < 4) return 2;
    curl_global_init(CURL_GLOBAL_DEFAULT);
    printf("%s, %s\n", curl_version(), OPENSSL_VERSION_TEXT);
    int fail = 0;
    if (get(argv[1], argv[2]) != CURLE_OK) { puts("FAIL trusted CA"); fail++; }
    if (get(argv[1], argv[3]) == CURLE_OK) { puts("FAIL untrusted CA accepted"); fail++; }
    if (argc > 4 && get(argv[4], argv[2]) == CURLE_OK) { puts("FAIL wrong host name accepted"); fail++; }
    puts(fail ? "tls check: FAILED" : "tls check: all passed");
    curl_global_cleanup();
    return fail;
}
