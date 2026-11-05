/*
 * SPDX-FileCopyrightText: 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.InputDevice;

/*
 * The touch controller only scans for the pen once it is told which pen
 * is connected over Bluetooth. Report the pen version of every Xiaomi
 * stylus input device, ORed with 0x10 while it is connected.
 */
final class StylusDeviceListener implements InputManager.InputDeviceListener {

    private static final String TAG = "XiaomiStylus";

    private static final int PEN_CONNECTED = 0x10;

    private final InputManager mInputManager;
    private final BluetoothAdapter mAdapter;
    private final PenMipp mMipp;
    private final PenPosition mPosition;
    private final PenReports mReports = new PenReports();
    private final PenGestureAction mGestureAction;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // input device id -> pen version
    private final SparseIntArray mPens = new SparseIntArray();

    StylusDeviceListener(Context context, PenPosition position) {
        mPosition = position;
        mInputManager = context.getSystemService(InputManager.class);
        mAdapter = context.getSystemService(BluetoothManager.class).getAdapter();
        mMipp = new PenMipp(context);
        mGestureAction = new PenGestureAction(context);

        // Gesture feedback and actions, the job HyperOS did in system_server.
        // A double press buzzes the pen; a pinch runs the action selected by
        // persist.pen.pinch_action instead of relying on the app.
        mReports.setOnDoublePress(mMipp::sendGestureHaptic);
        mReports.setOnPinch(mGestureAction::onPinch);
    }

    void start() {
        mMipp.start();
        mInputManager.registerInputDeviceListener(this, mHandler);

        for (int id : mInputManager.getInputDeviceIds()) {
            onInputDeviceAdded(id);
        }
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice device = mInputManager.getInputDevice(deviceId);
        if (device == null || mPens.indexOfKey(deviceId) >= 0) {
            return;
        }

        int version = PenGestures.getPenVersion(device.getVendorId(), device.getProductId());
        if (version == 0) {
            return;
        }

        keepEnabled(device);
        mPens.put(deviceId, version);
        mPosition.start();
        Log.i(TAG, "Pen " + device.getName() + " connected, version " + version);
        TouchFeature.setModeValue(TouchFeature.MODE_PEN_CONNECTION, version | PEN_CONNECTED);

        String address = device.getBluetoothAddress();
        if (address != null && mAdapter != null) {
            // stock reads the active state first only on this pen
            boolean activeStateFirst = device.getVendorId() == 0x0022
                    && device.getProductId() == 0x5081;
            mMipp.connect(mAdapter.getRemoteDevice(address), activeStateFirst);
        }
        if (version == 8) {
            mReports.start();
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        int index = mPens.indexOfKey(deviceId);
        if (index < 0) {
            return;
        }

        int version = mPens.valueAt(index);
        mPens.removeAt(index);
        Log.i(TAG, "Pen disconnected, version " + version);
        TouchFeature.setModeValue(TouchFeature.MODE_PEN_CONNECTION, version);

        if (mPens.size() == 0) {
            mPosition.stop();
            mMipp.disconnect();
            mReports.stop();
        }
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        if (mPens.indexOfKey(deviceId) < 0) {
            onInputDeviceAdded(deviceId);
            return;
        }
        InputDevice device = mInputManager.getInputDevice(deviceId);
        if (device != null) {
            keepEnabled(device);
        }
    }

    /*
     * The pen registers its mouse node before its keyboard and stylus nodes,
     * and the Bluetooth pointer suppression can disable it while it is only
     * a mouse. Its gestures come from the keyboard node, so turn it back on.
     */
    private void keepEnabled(InputDevice device) {
        if (device.isEnabled() || !(device.supportsSource(InputDevice.SOURCE_KEYBOARD)
                || device.supportsSource(InputDevice.SOURCE_STYLUS))) {
            return;
        }
        Log.i(TAG, "Re-enabling pen input device " + device.getName());
        mInputManager.enableInputDevice(device.getId());
    }
}
