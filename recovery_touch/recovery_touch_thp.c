/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * The NT36532 is a host-processed touch IC: the driver only streams raw
 * frames and Xiaomi's THP daemon turns them into input events. Outside of
 * touchfeature-service, which recovery doesn't have, nothing runs it, so
 * run it here.
 */

#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <sys/system_properties.h>
#include <time.h>

#define LOG_TAG "recovery_touch_thp"
#define LOG(prio, ...) __android_log_print(ANDROID_LOG_##prio, LOG_TAG, __VA_ARGS__)

/*
 * Recovery boots with the clock at 1970 and the daemon waits ~30s for a sane
 * one before it starts reporting touches, so start from the build date.
 */
static void ensure_sane_clock(void) {
    char value[PROP_VALUE_MAX];
    if (__system_property_get("ro.build.date.utc", value) <= 0) return;

    struct timespec build = {.tv_sec = atol(value)};
    struct timespec now;
    if (clock_gettime(CLOCK_REALTIME, &now) == 0 && now.tv_sec >= build.tv_sec) return;

    if (clock_settime(CLOCK_REALTIME, &build) != 0) {
        LOG(ERROR, "failed to set the clock to the build date");
    }
}

int main(void) {
    ensure_sane_clock();

    void* handle = dlopen("libtouchreport.so", RTLD_NOW);
    if (!handle) {
        LOG(ERROR, "dlopen failed: %s", dlerror());
        return 1;
    }

    int (*thp_daemon_main)(void) = (int (*)(void))dlsym(handle, "thp_daemon_main");
    if (!thp_daemon_main) {
        LOG(ERROR, "thp_daemon_main not found: %s", dlerror());
        return 1;
    }

    int ret = thp_daemon_main();
    LOG(ERROR, "thp_daemon_main returned %d", ret);
    return ret ? ret : 1;
}
