/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "vendor.lineage.health-service.uke"

#include "ChargingControl.h"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

using aidl::vendor::lineage::health::ChargingControl;

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(0);

    std::shared_ptr<ChargingControl> cc = ndk::SharedRefBase::make<ChargingControl>();
    const std::string instance = std::string(ChargingControl::descriptor) + "/default";
    if (AServiceManager_addService(cc->asBinder().get(), instance.c_str()) != STATUS_OK) {
        LOG(FATAL) << "Failed to register " << instance;
    }

    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach
}
