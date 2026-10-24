#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

DEVICE_PATH := device/xiaomi/uke
KERNEL_PATH := $(DEVICE_PATH)-kernel

# Add common definitions for Qualcomm
$(call inherit-product, hardware/qcom-caf/common/common.mk)

# Product characteristic
PRODUCT_CHARACTERISTICS := nosdcard,tablet

# Shipping API level
PRODUCT_SHIPPING_API_LEVEL := 34
