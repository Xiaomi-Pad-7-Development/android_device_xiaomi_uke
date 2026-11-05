/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;

/*
 * Loaded into system_server through config_deviceKeyHandlerLibs. Swallows
 * the Xiaomi pen gesture keys and runs the action set for each one, except
 * in a notes app, which gets the keys itself.
 */
public final class KeyHandler implements DeviceKeyHandler {

    private final Context mContext;
    private final ContentResolver mResolver;
    private final ActionRunner mRunner;
    private final NotesApps mNotesApps;

    public KeyHandler(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
        mRunner = new ActionRunner(context);
        mNotesApps = new NotesApps(context);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getStringExtra(PenPanel.EXTRA_ACTION);
                if (action != null) {
                    mRunner.run(action);
                }
            }
        }, new IntentFilter(PenPanel.ACTION_RUN), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        final String gesture = PenGestures.gestureFor(event.getScanCode());
        if (gesture == null || !PenGestures.isPen(event.getDevice())
                || mNotesApps.isInFront()) {
            return event;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            final String action = PenGestures.getAction(mResolver, gesture);
            if (PenGestures.ACTION_PANEL.equals(action)) {
                // The panel lives in the app, which tracks where the pen is
                mContext.sendBroadcastAsUser(
                        new Intent(PenPanel.ACTION_TOGGLE).setPackage(PenGestures.PACKAGE),
                        UserHandle.SYSTEM);
            } else {
                mRunner.run(action);
            }
        }
        return null;
    }
}
