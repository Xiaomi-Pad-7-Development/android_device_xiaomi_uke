/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.hardware.BatteryState;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.view.InputDevice;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class StylusSettingsActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new StylusSettingsFragment())
                    .commit();
        }
    }

    public static class StylusSettingsFragment extends PreferenceFragmentCompat
            implements InputManager.InputDeviceListener,
                    InputManager.InputDeviceBatteryListener {

        private InputManager mInputManager;
        private Preference mBattery;
        private int mDeviceId = -1;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setPreferenceDataStore(
                    new PenDataStore(requireContext().getContentResolver()));
            setPreferencesFromResource(R.xml.stylus_settings, rootKey);
            mBattery = findPreference("stylus_battery");
            mInputManager = requireContext().getSystemService(InputManager.class);
        }

        @Override
        public void onStart() {
            super.onStart();
            mInputManager.registerInputDeviceListener(this, null);
            refresh();
        }

        @Override
        public void onStop() {
            super.onStop();
            mInputManager.unregisterInputDeviceListener(this);
            detach();
        }

        private int findStylus() {
            for (int id : mInputManager.getInputDeviceIds()) {
                InputDevice device = mInputManager.getInputDevice(id);
                if (device != null && device.getVendorId() == PenGestures.VENDOR_ID
                        && device.getProductId() == PenGestures.PRODUCT_ID) {
                    return id;
                }
            }
            return -1;
        }

        private void detach() {
            if (mDeviceId != -1) {
                mInputManager.removeInputDeviceBatteryListener(mDeviceId, this);
                mDeviceId = -1;
            }
        }

        private void refresh() {
            final int id = findStylus();
            if (id == mDeviceId) {
                return;
            }
            detach();
            if (id == -1) {
                mBattery.setSummary(R.string.stylus_not_connected);
                return;
            }
            mDeviceId = id;
            // Reports the current state as soon as it is registered
            mInputManager.addInputDeviceBatteryListener(id,
                    requireContext().getMainExecutor(), this);
        }

        @Override
        public void onBatteryStateChanged(int deviceId, long eventTimeMillis,
                BatteryState state) {
            if (deviceId != mDeviceId) {
                return;
            }
            if (!state.isPresent() || Float.isNaN(state.getCapacity())) {
                mBattery.setSummary(R.string.stylus_battery_unavailable);
                return;
            }

            final int percent = Math.round(state.getCapacity() * 100);
            switch (state.getStatus()) {
                case BatteryState.STATUS_CHARGING:
                    mBattery.setSummary(getString(R.string.stylus_battery_charging, percent));
                    break;
                case BatteryState.STATUS_FULL:
                    mBattery.setSummary(getString(R.string.stylus_battery_full, percent));
                    break;
                default:
                    mBattery.setSummary(getString(R.string.stylus_battery_level, percent));
            }
        }

        @Override
        public void onInputDeviceAdded(int deviceId) {
            refresh();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            refresh();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            refresh();
        }
    }
}
