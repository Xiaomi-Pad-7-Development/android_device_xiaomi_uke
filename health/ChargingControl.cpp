/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.health-service.uke"

#include "ChargingControl.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

using android::base::ParseInt;
using android::base::ReadFileToString;
using android::base::Trim;
using android::base::WriteStringToFile;

namespace aidl {
namespace vendor {
namespace lineage {
namespace health {

// Xiaomi MCA smart charge control, see union SMART_CHG_HEADER in the kernel:
// bit 0 - enable, bit 1 - navigation (hold the battery at a SoC level while
// the charger keeps powering the device), bits 16-23 - SoC limit.
// Writing just the navigation bit goes back to normal charging; writing 0 does not release a hold.
static constexpr const char* kSmartChgPath = "/sys/class/qcom-battery/smart_chg";
static constexpr const char* kCapacityPath = "/sys/class/power_supply/battery/capacity";
static constexpr uint32_t kSmartChgEnable = 1 << 0;
static constexpr uint32_t kSmartChgNavigation = 1 << 1;
static constexpr int kSocLimitShift = 16;

ndk::ScopedAStatus ChargingControl::getChargingEnabled(bool* _aidl_return) {
    std::lock_guard<std::mutex> lock(mLock);
    *_aidl_return = mChargingEnabled;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::setChargingEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(mLock);
    uint32_t value = kSmartChgNavigation;

    if (!enabled) {
        // Hold the battery at its current level
        std::string buf;
        int soc = 0;
        if (!ReadFileToString(kCapacityPath, &buf) || !ParseInt(Trim(buf), &soc, 1, 100)) {
            LOG(ERROR) << "Failed to read battery capacity";
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
        }
        value |= kSmartChgEnable | (static_cast<uint32_t>(soc) << kSocLimitShift);
    }

    if (!WriteStringToFile(std::to_string(value), kSmartChgPath)) {
        PLOG(ERROR) << "Failed to write " << kSmartChgPath;
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    mChargingEnabled = enabled;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ChargingControl::getChargingDeadline(int64_t* /* _aidl_return */) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus ChargingControl::setChargingDeadline(int64_t /* deadline */) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus ChargingControl::getChargingLimit(ChargingLimitInfo* /* _aidl_return */) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus ChargingControl::setChargingLimit(const ChargingLimitInfo& /* limit */) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus ChargingControl::getSupportedMode(int* _aidl_return) {
    *_aidl_return = static_cast<int>(ChargingControlSupportedMode::TOGGLE) |
                    static_cast<int>(ChargingControlSupportedMode::BYPASS);
    return ndk::ScopedAStatus::ok();
}

}  // namespace health
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
