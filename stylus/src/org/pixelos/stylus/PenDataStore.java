/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.content.ContentResolver;

import java.util.HashSet;
import java.util.Set;

import androidx.preference.PreferenceDataStore;

/** Keeps the stylus preferences in Settings.Secure, where the key handler reads them. */
class PenDataStore extends PreferenceDataStore {

    private final ContentResolver mResolver;

    PenDataStore(ContentResolver resolver) {
        mResolver = resolver;
    }

    @Override
    public String getString(String key, String defValue) {
        return PenGestures.getAction(mResolver, key);
    }

    @Override
    public void putString(String key, String value) {
        PenGestures.setAction(mResolver, key, value);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return new HashSet<>(PenGestures.getPanelActions(mResolver));
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        PenGestures.setPanelActions(mResolver, values);
    }
}
