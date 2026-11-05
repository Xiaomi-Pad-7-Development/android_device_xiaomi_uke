/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.hardware.input.InputManager;
import android.os.Bundle;
import android.view.InputDevice;

import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class GestureSettingsActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new GestureSettingsFragment())
                    .commit();
        }
    }

    public static class GestureSettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setPreferenceDataStore(
                    new PenDataStore(requireContext().getContentResolver()));
            setPreferencesFromResource(R.xml.pen_gestures, rootKey);

            if (!hasDoubleTap()) {
                getPreferenceScreen().removePreference(
                        findPreference(PenGestures.DOUBLE_TAP));
            }
        }

        // Shown unless the pens that are connected all lack a double tap
        private boolean hasDoubleTap() {
            final InputManager inputManager = requireContext().getSystemService(InputManager.class);
            boolean penConnected = false;
            for (int id : inputManager.getInputDeviceIds()) {
                final InputDevice device = inputManager.getInputDevice(id);
                if (!PenGestures.isPen(device)) {
                    continue;
                }
                penConnected = true;
                if (PenGestures.getPenVersion(device.getVendorId(), device.getProductId())
                        == PenGestures.PEN_VERSION_PRO) {
                    return true;
                }
            }
            return !penConnected;
        }
    }
}
