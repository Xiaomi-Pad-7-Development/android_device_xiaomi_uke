/*
 * SPDX-FileCopyrightText: 2026 Yet Another AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.IActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.android.internal.util.ScreenshotHelper;
import com.xiaomi.touchservice.pencilengine.PencilEngineManagerService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
 * The Focus Pen Pro sends its squeeze, double tap and swipe gestures in
 * HID report 2, one keyboard usage per gesture. The kernel keeps that
 * report off the input devices, so the gestures come from hidraw and each
 * one runs its action here as an injected key or scroll. A pen app
 * that asked the pencil engine for the gestures gets the double tap and
 * swipes itself while it is in front, as the button keys stock sends it.
 */
final class PenGestures {

    private static final String TAG = "XiaomiStylus";

    // HID keyboard usages of the gestures in report 2
    private static final int USAGE_SQUEEZE = 0x6e;
    private static final int USAGE_DOUBLE_TAP = 0x58;
    private static final int USAGE_SWIPE_UP = 0x61;
    private static final int USAGE_SWIPE_DOWN = 0x5b;
    // the POCO Focus Pen swipes with page up and page down instead
    private static final int USAGE_PAGE_UP = 0x4b;
    private static final int USAGE_PAGE_DOWN = 0x4e;

    static final String SQUEEZE = "squeeze";
    static final String DOUBLE_TAP = "double_tap";
    static final String SWIPE_UP = "swipe_up";
    static final String SWIPE_DOWN = "swipe_down";

    static final String ACTION_NONE = "none";
    static final String ACTION_PIE = "pie";
    static final String ACTION_NOTES = "notes";
    static final String ACTION_SCREENSHOT = "screenshot";
    static final String ACTION_ASSISTANT = "assistant";
    static final String ACTION_RECENTS = "recents";
    static final String ACTION_PLAY_PAUSE = "play_pause";
    static final String ACTION_SCROLL_UP = "scroll_up";
    static final String ACTION_SCROLL_DOWN = "scroll_down";
    static final String ACTION_PARTIAL_SCREENSHOT = "partial_screenshot";
    static final String ACTION_VOICE_SEARCH = "voice_search";
    static final String ACTION_CAMERA = "camera";
    static final String ACTION_FLASHLIGHT = "flashlight";
    static final String ACTION_LAST_APP = "last_app";
    static final String ACTION_VOLUME_UP = "volume_up";
    static final String ACTION_VOLUME_DOWN = "volume_down";

    private static final String ACTION_SCREEN_CAMERA_GESTURE =
            "lineageos.intent.action.SCREEN_CAMERA_GESTURE";

    // pen haptic types and strength stock uses for a double tap and a swipe
    private static final int HAPTIC_DOUBLE_TAP = 3;
    private static final int HAPTIC_SWIPE = 4;
    private static final int HAPTIC_STRENGTH = 128;

    // mouse wheel steps one swipe scrolls by, most apps ignore page keys
    private static final float SCROLL_STEPS = 8;

    private static final String PIE_ACTIONS = "pie_actions";
    static final String NOTES_APP = "notes_app";

    // every action the pie can show, in the order it shows them
    private static final List<String> PIE_CHOICES = Arrays.asList(ACTION_NOTES,
            ACTION_SCREENSHOT, ACTION_PARTIAL_SCREENSHOT, ACTION_ASSISTANT,
            ACTION_VOICE_SEARCH, ACTION_CAMERA, ACTION_FLASHLIGHT, ACTION_RECENTS,
            ACTION_LAST_APP, ACTION_PLAY_PAUSE, ACTION_VOLUME_UP, ACTION_VOLUME_DOWN);

    private final Context mContext;
    private final PenMipp mMipp;
    private final InputManager mInputManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final PenPie mPie;
    private final SharedPreferences mPrefs;
    private final CameraManager.TorchCallback mTorchCallback =
            new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId.equals(mTorchCameraId)) {
                mTorchEnabled = enabled;
            }
        }
    };

    private CameraManager mCameraManager;
    private String mTorchCameraId;
    private boolean mTorchEnabled;

    PenGestures(Context context, PenMipp mipp) {
        mContext = context;
        mMipp = mipp;
        mInputManager = context.getSystemService(InputManager.class);
        mPie = new PenPie(context, this::run);
        mPrefs = context.createDeviceProtectedStorageContext().getSharedPreferences(
                PreferenceManager.getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE);
    }

    void onReport(int usage) {
        String gesture = gestureFor(usage);
        if (gesture != null) {
            mHandler.post(() -> onGesture(gesture));
        }
    }

    private void onGesture(String gesture) {
        if (!mContext.getSystemService(PowerManager.class).isInteractive()) {
            return;
        }

        if (!SQUEEZE.equals(gesture)
                && PencilEngineManagerService.wantsGestures(focusedPackage())) {
            Log.i(TAG, "Pen " + gesture + ", passing it to the app");
            vibrate(gesture);
            injectKey(appKeyCodeFor(gesture));
            return;
        }

        String action = actionFor(gesture);
        Log.i(TAG, "Pen " + gesture + ", running " + action);
        if (ACTION_NONE.equals(action)) {
            return;
        }

        vibrate(gesture);
        run(action);
    }

    void run(String action) {
        if (ACTION_PIE.equals(action)) {
            mPie.toggle(pieActions());
            return;
        }
        if (ACTION_SCROLL_UP.equals(action)) {
            injectScroll(SCROLL_STEPS);
            return;
        }
        if (ACTION_SCROLL_DOWN.equals(action)) {
            injectScroll(-SCROLL_STEPS);
            return;
        }
        if (ACTION_NOTES.equals(action) && openNotesApp()) {
            return;
        }
        if (ACTION_PARTIAL_SCREENSHOT.equals(action)) {
            new ScreenshotHelper(mContext).takeScreenshot(
                    WindowManager.TAKE_SCREENSHOT_SELECTED_REGION,
                    WindowManager.ScreenshotSource.SCREENSHOT_KEY_OTHER, mHandler, null);
            return;
        }
        if (ACTION_CAMERA.equals(action)) {
            mContext.sendBroadcastAsUser(new Intent(ACTION_SCREEN_CAMERA_GESTURE),
                    UserHandle.CURRENT, Manifest.permission.STATUS_BAR_SERVICE);
            return;
        }
        if (ACTION_FLASHLIGHT.equals(action)) {
            toggleTorch();
            return;
        }
        if (ACTION_LAST_APP.equals(action)) {
            switchToLastApp();
            return;
        }

        int keyCode = keyCodeFor(action);
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            injectKey(keyCode);
        }
    }

    private void vibrate(String gesture) {
        if (DOUBLE_TAP.equals(gesture)) {
            mMipp.vibrate(HAPTIC_DOUBLE_TAP, HAPTIC_STRENGTH);
        } else if (SWIPE_UP.equals(gesture) || SWIPE_DOWN.equals(gesture)) {
            mMipp.vibrate(HAPTIC_SWIPE, HAPTIC_STRENGTH);
        }
    }

    private void injectKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD);
        mInputManager.injectInputEvent(down, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        mInputManager.injectInputEvent(KeyEvent.changeAction(down, KeyEvent.ACTION_UP),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    // the app in front is the first task, the one before it that is not the launcher is next
    private void switchToLastApp() {
        try {
            final int userId = ActivityManager.getCurrentUser();
            final IActivityManager am = ActivityManager.getService();
            final ResolveInfo home = mContext.getPackageManager().resolveActivityAsUser(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY, userId);
            final String homePackage = home != null ? home.activityInfo.packageName : null;
            final List<ActivityManager.RecentTaskInfo> tasks = am.getRecentTasks(5,
                    ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId).getList();
            for (int i = 1; i < tasks.size(); i++) {
                final ActivityManager.RecentTaskInfo task = tasks.get(i);
                final ComponentName component = task.origActivity != null
                        ? task.origActivity : task.baseIntent.getComponent();
                if (component == null || component.getPackageName().equals(homePackage)
                        || component.getPackageName().equals("com.android.systemui")) {
                    continue;
                }
                am.moveTaskToFront(null, mContext.getPackageName(), task.id,
                        ActivityManager.MOVE_TASK_NO_USER_ACTION, null);
                return;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Could not switch to the last app", e);
        }
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

    private void injectScroll(float steps) {
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = metrics.widthPixels / 2f;
        coords.y = metrics.heightPixels / 2f;
        coords.setAxisValue(MotionEvent.AXIS_VSCROLL, steps);

        long now = SystemClock.uptimeMillis();
        MotionEvent scroll = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1,
                new MotionEvent.PointerProperties[] { properties },
                new MotionEvent.PointerCoords[] { coords }, 0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_MOUSE, 0);
        mInputManager.injectInputEvent(scroll, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        scroll.recycle();
    }

    private static String focusedPackage() {
        try {
            ActivityTaskManager.RootTaskInfo info =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            if (info == null || info.topActivity == null) {
                return null;
            }
            return info.topActivity.getPackageName();
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to get the focused app", e);
            return null;
        }
    }

    private static String gestureFor(int usage) {
        switch (usage) {
            case USAGE_SQUEEZE:
                return SQUEEZE;
            case USAGE_DOUBLE_TAP:
                return DOUBLE_TAP;
            case USAGE_SWIPE_UP:
            case USAGE_PAGE_UP:
                return SWIPE_UP;
            case USAGE_SWIPE_DOWN:
            case USAGE_PAGE_DOWN:
                return SWIPE_DOWN;
            default:
                return null;
        }
    }

    private static int appKeyCodeFor(String gesture) {
        switch (gesture) {
            case DOUBLE_TAP:
                return KeyEvent.KEYCODE_BUTTON_8;
            case SWIPE_UP:
                return KeyEvent.KEYCODE_BUTTON_9;
            default:
                return KeyEvent.KEYCODE_BUTTON_10;
        }
    }

    private String actionFor(String gesture) {
        return mPrefs.getString(gesture, ACTION_NONE);
    }

    // the system notes role opens through the stylus tail key when no app is picked
    private boolean openNotesApp() {
        String packageName = mPrefs.getString(NOTES_APP, null);
        if (packageName == null) {
            return false;
        }

        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent == null) {
            Log.w(TAG, "Notes app " + packageName + " can't be launched");
            return false;
        }
        mContext.startActivityAsUser(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                UserHandle.CURRENT);
        return true;
    }

    private List<String> pieActions() {
        Set<String> selected = mPrefs.getStringSet(PIE_ACTIONS, Collections.emptySet());
        List<String> actions = new ArrayList<>();
        for (String action : PIE_CHOICES) {
            if (selected.contains(action)) {
                actions.add(action);
            }
        }
        return actions;
    }

    private static int keyCodeFor(String action) {
        switch (action) {
            case ACTION_NOTES:
                return KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL;
            case ACTION_SCREENSHOT:
                return KeyEvent.KEYCODE_SYSRQ;
            case ACTION_ASSISTANT:
                return KeyEvent.KEYCODE_ASSIST;
            case ACTION_RECENTS:
                return KeyEvent.KEYCODE_APP_SWITCH;
            case ACTION_PLAY_PAUSE:
                return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            case ACTION_VOICE_SEARCH:
                return KeyEvent.KEYCODE_VOICE_ASSIST;
            case ACTION_VOLUME_UP:
                return KeyEvent.KEYCODE_VOLUME_UP;
            case ACTION_VOLUME_DOWN:
                return KeyEvent.KEYCODE_VOLUME_DOWN;
            default:
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
