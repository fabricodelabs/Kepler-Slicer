#include <jni.h>
#include "semver.h"

// This is a JNI toolchain check using the official upstream semver component.
// It does not provide, or claim to provide, slicing.
extern "C" JNIEXPORT jint JNICALL
Java_com_keplerslicer_engine_NativeVersion_compare(JNIEnv *env, jclass, jstring lhs, jstring rhs)
{
    if (lhs == nullptr || rhs == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Version strings must not be null");
        return 0;
    }
    const char *left = env->GetStringUTFChars(lhs, nullptr);
    if (!left) return 0;
    const char *right = env->GetStringUTFChars(rhs, nullptr);
    if (!right) {
        env->ReleaseStringUTFChars(lhs, left);
        return 0;
    }
    semver_t a{}, b{};
    const int left_status = semver_parse(left, &a);
    const int right_status = semver_parse(right, &b);
    env->ReleaseStringUTFChars(lhs, left);
    env->ReleaseStringUTFChars(rhs, right);
    const int result = left_status == 0 && right_status == 0 ? semver_compare(a, b) : 0;
    semver_free(&a);
    semver_free(&b);
    if (left_status != 0 || right_status != 0)
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid OrcaSlicer version");
    return result;
}
