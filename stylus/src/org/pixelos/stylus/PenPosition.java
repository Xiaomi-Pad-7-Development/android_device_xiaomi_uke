/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.graphics.PointF;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;

/*
 * Remembers where the pen last touched or hovered over the screen, so the
 * pen panel can open next to it. Only watches input while a pen is connected.
 */
final class PenPosition {

    // The pen has to have been seen this recently for its position to count
    private static final long MAX_AGE_MILLIS = 5000;

    private final InputManager mInputManager;
    private final Looper mLooper;

    private InputMonitor mMonitor;
    private InputEventReceiver mReceiver;

    private volatile float mX;
    private volatile float mY;
    private volatile long mTime;

    PenPosition(InputManager inputManager, Looper looper) {
        mInputManager = inputManager;
        mLooper = looper;
    }

    void start() {
        if (mMonitor != null) {
            return;
        }
        mMonitor = mInputManager.monitorGestureInput("XiaomiPenPosition", Display.DEFAULT_DISPLAY);
        mReceiver = new Receiver(mMonitor.getInputChannel(), mLooper);
    }

    void stop() {
        if (mMonitor == null) {
            return;
        }
        mReceiver.dispose();
        mMonitor.dispose();
        mReceiver = null;
        mMonitor = null;
        mTime = 0;
    }

    /** Returns the recent pen position in screen coordinates, or null if it is not known. */
    PointF get() {
        if (mTime == 0 || SystemClock.uptimeMillis() - mTime > MAX_AGE_MILLIS) {
            return null;
        }
        return new PointF(mX, mY);
    }

    private final class Receiver extends InputEventReceiver {

        Receiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            try {
                if (event instanceof MotionEvent) {
                    final MotionEvent motion = (MotionEvent) event;
                    if (motion.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                        mX = motion.getRawX();
                        mY = motion.getRawY();
                        mTime = SystemClock.uptimeMillis();
                    }
                }
            } finally {
                // A monitor only watches, it never consumes the event
                finishInputEvent(event, false);
            }
        }
    }
}
