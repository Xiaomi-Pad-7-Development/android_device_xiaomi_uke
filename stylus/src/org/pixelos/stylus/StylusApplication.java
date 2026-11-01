/*
 * SPDX-FileCopyrightText: 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.app.Application;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.provider.Settings;

public class StylusApplication extends Application {

    // Xiaomi's pen apps only register for pen gestures with the touch service when this is set
    private static final String PENCIL_ENGINE_VERSION_KEY = "stylus_pencil_engine_version";
    private static final String PENCIL_ENGINE_VERSION = "0.3.2";

    private PenPairing mPenPairing;
    private StylusDeviceListener mStylusDeviceListener;
    private PenPosition mPenPosition;
    private PenPanel mPenPanel;

    @Override
    public void onCreate() {
        super.onCreate();

        // The gesture settings run in their own process
        if (!getPackageName().equals(getProcessName())) {
            return;
        }

        if (!PENCIL_ENGINE_VERSION.equals(
                Settings.System.getString(getContentResolver(), PENCIL_ENGINE_VERSION_KEY))) {
            Settings.System.putString(getContentResolver(), PENCIL_ENGINE_VERSION_KEY,
                    PENCIL_ENGINE_VERSION);
        }

        mPenPosition = new PenPosition(getSystemService(InputManager.class),
                Looper.getMainLooper());
        mPenPanel = new PenPanel(this, mPenPosition);
        mPenPanel.start();

        mStylusDeviceListener = new StylusDeviceListener(this, mPenPosition);
        mStylusDeviceListener.start();

        mPenPairing = new PenPairing(this);
        mPenPairing.start();
    }
}
