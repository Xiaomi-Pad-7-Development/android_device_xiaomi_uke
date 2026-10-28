/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.touch-service.uke"

#include "HighTouchPollingRate.h"

#include <aidl/vendor/xiaomi/hw/touchfeature/ITouchFeature.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>

using aidl::vendor::xiaomi::hw::touchfeature::ITouchFeature;

namespace aidl {
namespace vendor {
namespace lineage {
namespace touch {

static constexpr int kTouchId = 0;
static constexpr int kTouchGameMode = 0;

static std::shared_ptr<ITouchFeature> getTouchFeature() {
    const std::string instance = std::string(ITouchFeature::descriptor) + "/default";
    return ITouchFeature::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService(instance.c_str())));
}

ndk::ScopedAStatus HighTouchPollingRate::getEnabled(bool* _aidl_return) {
    auto touchFeature = getTouchFeature();
    int value = 0;
    if (!touchFeature ||
        !touchFeature->getModeCurValue(kTouchId, kTouchGameMode, &value).isOk()) {
        LOG(ERROR) << "Failed to read touch game mode";
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    *_aidl_return = value > 0;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus HighTouchPollingRate::setEnabled(bool enabled) {
    auto touchFeature = getTouchFeature();
    int ret = 0;
    if (!touchFeature ||
        !touchFeature->setModeValue(kTouchId, kTouchGameMode, enabled ? 1 : 0, &ret).isOk()) {
        LOG(ERROR) << "Failed to set touch game mode";
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    return ndk::ScopedAStatus::ok();
}

}  // namespace touch
}  // namespace lineage
}  // namespace vendor
}  // namespace aidl
