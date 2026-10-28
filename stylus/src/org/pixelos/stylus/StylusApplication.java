/*
 * SPDX-FileCopyrightText: 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.app.Application;
import android.hardware.input.InputManager;
import android.os.Looper;

public class StylusApplication extends Application {

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
