#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Show the bypass charging toggle in GameSpace.
# This has to be set before the products below are inherited.
BYPASS_CHARGE_SUPPORTED := true

# Apps this tablet does not ship (see also UkeRemovedApps in Android.bp, which drops Twelve and Jelly).
# These flags have to be set before the products below are inherited.
# No call recorder (BCR)
TARGET_PREBUILT_BCR := false
# No Google Phone, Google Messages or Google Contacts (the GMS telecom package)
TARGET_INCLUDE_GOOGLE_TELECOMM := false

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base.mk)

# Inherit some common LineageOS stuff.
$(call inherit-product, vendor/lineage/config/common_full_tablet_wifionly.mk)

# Inherit from device makefile.
$(call inherit-product, device/xiaomi/uke/device.mk)

PRODUCT_DEVICE := uke
PRODUCT_BRAND := Xiaomi
PRODUCT_NAME := lineage_uke
PRODUCT_MANUFACTURER := Xiaomi
PRODUCT_MODEL := 2410CRP4CG

PRODUCT_BUILD_PROP_OVERRIDES += \
    BuildDesc="missi-user 16 BP2A.250605.031.A3 OS3.0.304.0.WOZMIXM release-keys"

# Axion Device Configuration
AXION_MAINTAINER := delano-git
AXION_PROCESSOR := Snapdragon®_7+_Gen_3

# Camera Info
AXION_CAMERA_REAR_INFO := 13
AXION_CAMERA_FRONT_INFO := 8

# Graphics & Display
TARGET_ENABLE_BLUR := true
TARGET_SUPPORTED_REFRESH_RATES := 60,90,120,144
HBM_SUPPORTED := true
HBM_NODE := /data/vendor/display/hbm_mode
