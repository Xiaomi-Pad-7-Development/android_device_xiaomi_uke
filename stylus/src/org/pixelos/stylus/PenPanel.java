/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import java.util.ArrayList;
import java.util.List;

/*
 * A ring of pen actions that opens next to the pen. The key handler asks
 * for it with ACTION_TOGGLE when a gesture is set to show the panel.
 */
final class PenPanel implements PenPanelView.Listener {

    static final String ACTION_TOGGLE = "org.pixelos.stylus.action.TOGGLE_PANEL";

    // Sent back to the key handler in system_server, which runs the actions
    static final String ACTION_RUN = "org.pixelos.stylus.action.RUN_ACTION";
    static final String EXTRA_ACTION = "action";

    private static final String TAG = "XiaomiStylus";

    // The panel dismisses itself when it is left alone for this long
    private static final long TIMEOUT_MILLIS = 10000;

    // Gives the panel time to leave the screen before the action runs
    private static final long ACTION_DELAY_MILLIS = 250;

    private final Context mContext;
    private final PenPosition mPosition;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDismiss = this::dismiss;

    private WindowManager mWindowManager;
    private PenPanelView mView;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                dismiss();
            } else {
                toggle();
            }
        }
    };

    PenPanel(Context context, PenPosition position) {
        mContext = context;
        mPosition = position;
    }

    void start() {
        final IntentFilter filter = new IntentFilter(ACTION_TOGGLE);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void toggle() {
        if (mView != null) {
            dismiss();
            return;
        }
        if (!mContext.getSystemService(PowerManager.class).isInteractive() || isKeyguardLocked()) {
            return;
        }

        final List<String> actions = PenGestures.getPanelActions(mContext.getContentResolver());
        if (actions.isEmpty()) {
            return;
        }
        if (actions.size() == 1) {
            runAction(actions.get(0));
            return;
        }
        show(actions);
    }

    // KeyguardManager needs the trust service, which this app may not look up
    private static boolean isKeyguardLocked() {
        try {
            return WindowManagerGlobal.getWindowManagerService().isKeyguardLocked();
        } catch (RemoteException e) {
            return true;
        }
    }

    private void show(List<String> actions) {
        final Display display = mContext.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        final Context windowContext = mContext.createWindowContext(display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        mWindowManager = windowContext.getSystemService(WindowManager.class);

        final Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
        PointF center = mPosition.get();
        if (center == null) {
            center = new PointF(bounds.width() / 2f, bounds.height() / 2f);
        }

        final List<String> labels = new ArrayList<>();
        for (String action : actions) {
            labels.add(windowContext.getString(PenPanelView.labelFor(action)));
        }
        mView = new PenPanelView(windowContext, actions, labels, center, this);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setTitle("XiaomiPenPanel");

        try {
            mWindowManager.addView(mView, params);
            mHandler.postDelayed(mDismiss, TIMEOUT_MILLIS);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not show the pen panel", e);
            mView = null;
        }
    }

    private void dismiss() {
        mHandler.removeCallbacks(mDismiss);
        if (mView == null) {
            return;
        }
        try {
            mWindowManager.removeViewImmediate(mView);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not remove the pen panel", e);
        }
        mView = null;
    }

    @Override
    public void onSelected(String action) {
        dismiss();
        mHandler.postDelayed(() -> runAction(action), ACTION_DELAY_MILLIS);
    }

    private void runAction(String action) {
        mContext.sendBroadcastAsUser(new Intent(ACTION_RUN).putExtra(EXTRA_ACTION, action),
                UserHandle.SYSTEM);
    }

    @Override
    public void onDismiss() {
        dismiss();
    }

    @Override
    public void onTouched() {
        mHandler.removeCallbacks(mDismiss);
        mHandler.postDelayed(mDismiss, TIMEOUT_MILLIS);
    }
}
