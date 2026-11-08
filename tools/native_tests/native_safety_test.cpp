// Native regression tests for the audit fixes (run on an Android emulator, see tools/native_tests/run.sh):
//  SEC-07  jni_safe_utf8 always yields valid Modified UTF-8 (BMP only, no NUL, no malformed bytes); random fuzzing.
//  SEC-03  the bundled expat 2.10 rejects malformed XML and stops entity expansion bombs ("billion laughs").
#include "../../engine/src/jni_text.hpp"
#include <expat.h>
#include <cstdio>
#include <cstring>
#include <random>
#include <string>

static int failures = 0;
#define CHECK(c, what) do { if (!(c)) { std::printf("FAIL %s\n", what); ++failures; } } while (0)

static bool valid_modified_utf8(const std::string &s) {
    for (size_t i = 0; i < s.size();) {
        unsigned char c = s[i];
        if (c == 0) return false;
        if (c < 0x80) { ++i; continue; }
        size_t len = (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : 0;
        if (!len || i + len > s.size()) return false;
        for (size_t k = 1; k < len; ++k) if ((static_cast<unsigned char>(s[i + k]) & 0xC0) != 0x80) return false;
        i += len;
    }
    return true;
}

static int parse(const std::string &xml, XML_Error *error) {
    XML_Parser p = XML_ParserCreate(nullptr);
    int ok = XML_Parse(p, xml.data(), static_cast<int>(xml.size()), 1);
    *error = XML_GetErrorCode(p);
    XML_ParserFree(p);
    return ok;
}

int main() {
    using kepler::jni_safe_utf8;
    CHECK(jni_safe_utf8("plain ASCII") == "plain ASCII", "ASCII unchanged");
    CHECK(jni_safe_utf8("\xD0\x9F\xD1\x80\xD0\xB8\xD0\xBC\xD0\xB5\xD1\x80") == "\xD0\x9F\xD1\x80\xD0\xB8\xD0\xBC\xD0\xB5\xD1\x80", "Cyrillic kept");
    CHECK(jni_safe_utf8("a\xF0\x9F\x98\x80" "b") == "a?b", "emoji becomes one '?'");
    CHECK(jni_safe_utf8("x\xFFy") == "x?y", "invalid byte replaced");
    CHECK(jni_safe_utf8("\xC0\xAF") == "??", "overlong encoding rejected");
    CHECK(jni_safe_utf8("\xED\xA0\x80") == "???", "surrogate rejected");
    CHECK(jni_safe_utf8("\xE2\x82") == "??", "truncated sequence rejected");
    CHECK(jni_safe_utf8(nullptr).empty(), "null pointer");
    std::mt19937 rng(20261108);
    for (int round = 0; round < 200000; ++round) {
        std::string in(rng() % 64, '\0');
        for (auto &ch : in) { ch = static_cast<char>(rng() & 0xFF); if (!ch) ch = 1; }
        if (!valid_modified_utf8(jni_safe_utf8(in.c_str()))) { CHECK(false, "fuzz output not Modified UTF-8"); break; }
    }

    std::printf("expat %s\n", XML_ExpatVersion());
    XML_Error e;
    CHECK(parse("<model><vertex x=\"1\"/></model>", &e) == XML_STATUS_OK, "well-formed 3MF XML parses");
    CHECK(parse("<model><vertex x=\"1\"></model>", &e) == XML_STATUS_ERROR, "mismatched tag rejected");
    std::string lol = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY l0 \"lollollollollollollollollollol\">";
    for (int i = 1; i <= 9; ++i) lol += "<!ENTITY l" + std::to_string(i) + " \"" + std::string("&l") + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";&l" + std::to_string(i - 1) + ";\">";
    lol += "]><model>&l9;</model>";
    CHECK(parse(lol, &e) == XML_STATUS_ERROR && e == XML_ERROR_AMPLIFICATION_LIMIT_BREACH, "billion laughs stopped by the amplification limit");
    std::printf(failures ? "native safety tests: %d failed\n" : "native safety tests: all passed\n", failures);
    return failures ? 1 : 0;
}
