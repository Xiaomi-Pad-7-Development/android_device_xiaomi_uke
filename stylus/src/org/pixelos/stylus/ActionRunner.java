/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.android.internal.util.ScreenshotHelper;

import org.lineageos.internal.util.ActionUtils;

/*
 * Runs the action picked for a pen gesture or a pen panel entry. It runs in
 * system_server, where the key handler lives, because the stylus app's
 * SELinux domain cannot reach services such as the camera.
 */
final class ActionRunner {

    private static final String TAG = "XiaomiStylus";

    private static final String ACTION_SCREEN_CAMERA_GESTURE =
            "lineageos.intent.action.SCREEN_CAMERA_GESTURE";

    private final Context mContext;
    private final Handler mHandler;

    private CameraManager mCameraManager;
    private String mTorchCameraId;
    private boolean mTorchEnabled;

    private final CameraManager.TorchCallback mTorchCallback = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId.equals(mTorchCameraId)) {
                mTorchEnabled = enabled;
            }
        }
    };

    ActionRunner(Context context) {
        mContext = context;

        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new Handler(thread.getLooper());
    }

    void run(String action) {
        run(action, 0);
    }

    void run(String action, long delayMillis) {
        mHandler.postDelayed(() -> runNow(action), delayMillis);
    }

    private void runNow(String action) {
        final PowerManager pm = mContext.getSystemService(PowerManager.class);
        if (!pm.isInteractive()) {
            return;
        }

        switch (action) {
            case PenGestures.ACTION_ASSISTANT:
                injectKey(KeyEvent.KEYCODE_ASSIST);
                break;
            case PenGestures.ACTION_VOICE_SEARCH:
                injectKey(KeyEvent.KEYCODE_VOICE_ASSIST);
                break;
            case PenGestures.ACTION_CAMERA:
                mContext.sendBroadcastAsUser(new Intent(ACTION_SCREEN_CAMERA_GESTURE),
                        UserHandle.CURRENT, Manifest.permission.STATUS_BAR_SERVICE);
                break;
            case PenGestures.ACTION_TAKE_PICTURE:
                injectKey(KeyEvent.KEYCODE_CAMERA);
                break;
            case PenGestures.ACTION_FLASHLIGHT:
                toggleTorch();
                break;
            case PenGestures.ACTION_LAST_APP:
                ActionUtils.switchToLastApp(mContext, ActivityManager.getCurrentUser());
                break;
            case PenGestures.ACTION_RECENTS:
                injectKey(KeyEvent.KEYCODE_APP_SWITCH);
                break;
            case PenGestures.ACTION_SCREENSHOT:
                takeScreenshot(WindowManager.TAKE_SCREENSHOT_FULLSCREEN);
                break;
            case PenGestures.ACTION_PARTIAL_SCREENSHOT:
                takeScreenshot(WindowManager.TAKE_SCREENSHOT_SELECTED_REGION);
                break;
            case PenGestures.ACTION_PLAY_PAUSE:
                injectKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case PenGestures.ACTION_VOLUME_UP:
                adjustVolume(AudioManager.ADJUST_RAISE);
                break;
            case PenGestures.ACTION_VOLUME_DOWN:
                adjustVolume(AudioManager.ADJUST_LOWER);
                break;
            case PenGestures.ACTION_SCREEN_OFF:
                pm.goToSleep(SystemClock.uptimeMillis());
                break;
            case PenGestures.ACTION_PAGE_UP:
                injectKey(KeyEvent.KEYCODE_PAGE_UP);
                break;
            case PenGestures.ACTION_PAGE_DOWN:
                injectKey(KeyEvent.KEYCODE_PAGE_DOWN);
                break;
            default:
                break;
        }
    }

    private void injectKey(int keyCode) {
        final InputManager im = mContext.getSystemService(InputManager.class);
        final long now = SystemClock.uptimeMillis();
        final KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD);
        im.injectInputEvent(down, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        im.injectInputEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private void takeScreenshot(int type) {
        new ScreenshotHelper(mContext).takeScreenshot(type,
                WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER, mHandler, null);
    }

    private void adjustVolume(int direction) {
        mContext.getSystemService(AudioManager.class).adjustSuggestedStreamVolume(direction,
                AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
    }

    private void toggleTorch() {
        if (mCameraManager == null) {
            mCameraManager = mContext.getSystemService(CameraManager.class);
            mTorchCameraId = findTorchCamera();
            mCameraManager.registerTorchCallback(mTorchCallback, mHandler);
        }
        if (mTorchCameraId == null) {
            return;
        }
        try {
            mCameraManager.setTorchMode(mTorchCameraId, !mTorchEnabled);
        } catch (CameraAccessException e) {
            Log.w(TAG, "Could not toggle the flashlight", e);
        }
    }

    private String findTorchCamera() {
        try {
            for (String id : mCameraManager.getCameraIdList()) {
                CameraCharacteristics c = mCameraManager.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && facing != null
                        && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            Log.w(TAG, "Could not find the flashlight", e);
        }
        return null;
    }
}
