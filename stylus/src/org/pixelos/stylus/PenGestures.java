/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.content.ContentResolver;
import android.os.UserHandle;
import android.view.InputDevice;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/*
 * The Xiaomi pens report their gestures as keys on their keyboard node.
 * Each gesture runs the action picked for it in the pen gesture settings.
 */
final class PenGestures {

    static final String PACKAGE = "org.pixelos.stylus";

    // The Focus Pen Pro is the only pen with a double tap
    static final int PEN_VERSION_PRO = 8;

    static final String DOUBLE_TAP = "pen_gesture_double_tap";
    static final String SWIPE_UP = "pen_gesture_swipe_up";
    static final String SWIPE_DOWN = "pen_gesture_swipe_down";
    static final String SQUEEZE = "pen_gesture_squeeze";

    // Entries shown by the pen panel
    static final String PANEL_ACTIONS = "pen_panel_actions";

    static final String ACTION_NONE = "none";
    static final String ACTION_PANEL = "panel";
    static final String ACTION_ASSISTANT = "assistant";
    static final String ACTION_VOICE_SEARCH = "voice_search";
    static final String ACTION_CAMERA = "camera";
    static final String ACTION_TAKE_PICTURE = "take_picture";
    static final String ACTION_FLASHLIGHT = "flashlight";
    static final String ACTION_LAST_APP = "last_app";
    static final String ACTION_RECENTS = "recents";
    static final String ACTION_SCREENSHOT = "screenshot";
    static final String ACTION_PARTIAL_SCREENSHOT = "partial_screenshot";
    static final String ACTION_PLAY_PAUSE = "play_pause";
    static final String ACTION_VOLUME_UP = "volume_up";
    static final String ACTION_VOLUME_DOWN = "volume_down";
    static final String ACTION_SCREEN_OFF = "screen_off";
    static final String ACTION_PAGE_UP = "page_up";
    static final String ACTION_PAGE_DOWN = "page_down";

    // Every action the panel can show, in the order of pen_panel_action_values
    static final List<String> PANEL_CHOICES = Arrays.asList(
            ACTION_ASSISTANT, ACTION_VOICE_SEARCH, ACTION_CAMERA, ACTION_TAKE_PICTURE,
            ACTION_FLASHLIGHT, ACTION_LAST_APP, ACTION_RECENTS, ACTION_SCREENSHOT,
            ACTION_PARTIAL_SCREENSHOT, ACTION_PLAY_PAUSE, ACTION_VOLUME_UP, ACTION_VOLUME_DOWN,
            ACTION_SCREEN_OFF, ACTION_PAGE_UP, ACTION_PAGE_DOWN);

    // Stock offers the assistant, notes and a screenshot. There is no notes app here
    private static final String DEFAULT_PANEL_ACTIONS = ACTION_ASSISTANT + "," + ACTION_SCREENSHOT;

    private PenGestures() {}

    /** Returns the pen generation for a vendor and product id, or 0 if it is not a pen. */
    static int getPenVersion(int vendorId, int productId) {
        if (vendorId == 0x1915) {
            switch (productId) {
                case 0xeaea:
                    return 1;
                case 0x4d81:
                    return 2;
            }
        } else if (vendorId == 0x0022) {
            switch (productId) {
                case 0x3280:
                case 0x3281:
                case 0x4d80:
                    return 3;
                case 0x4e83:
                    return 4;
                case 0x3183:
                    return 5;
                case 0x3283:
                    return 6;
                case 0x4f84:
                    return 7;
                case 0x5081:
                    return PEN_VERSION_PRO;
            }
        }
        return 0;
    }

    static boolean isPen(InputDevice device) {
        return device != null && getPenVersion(device.getVendorId(), device.getProductId()) != 0;
    }

    /** Returns the gesture setting for a pen scan code, or null if it is not a gesture. */
    static String gestureFor(int scanCode) {
        switch (scanCode) {
            case 96: // KEY_KPENTER
                return DOUBLE_TAP;
            case 73: // KEY_KP9
            case 104: // KEY_PAGEUP, sent by the POCO Focus Pen
                return SWIPE_UP;
            case 81: // KEY_KP3
            case 109: // KEY_PAGEDOWN, sent by the POCO Focus Pen
                return SWIPE_DOWN;
            case 189: // KEY_F19
                return SQUEEZE;
            default:
                return null;
        }
    }

    static String defaultAction(String gesture) {
        switch (gesture) {
            case SWIPE_UP:
                return ACTION_PAGE_UP;
            case SWIPE_DOWN:
                return ACTION_PAGE_DOWN;
            case SQUEEZE:
                return ACTION_PANEL;
            default:
                return ACTION_NONE;
        }
    }

    static String getAction(ContentResolver resolver, String gesture) {
        final String action = Settings.Secure.getStringForUser(resolver, gesture,
                UserHandle.USER_CURRENT);
        return action != null ? action : defaultAction(gesture);
    }

    static void setAction(ContentResolver resolver, String gesture, String action) {
        Settings.Secure.putStringForUser(resolver, gesture, action, UserHandle.USER_CURRENT);
    }

    /** Returns the actions the panel shows, in display order. */
    static List<String> getPanelActions(ContentResolver resolver) {
        String value = Settings.Secure.getStringForUser(resolver, PANEL_ACTIONS,
                UserHandle.USER_CURRENT);
        if (value == null) {
            value = DEFAULT_PANEL_ACTIONS;
        }
        final List<String> actions = new ArrayList<>();
        for (String action : value.split(",")) {
            if (PANEL_CHOICES.contains(action) && !actions.contains(action)) {
                actions.add(action);
            }
        }
        return actions;
    }

    static void setPanelActions(ContentResolver resolver, Collection<String> selected) {
        final List<String> ordered = new ArrayList<>();
        for (String action : PANEL_CHOICES) {
            if (selected.contains(action)) {
                ordered.add(action);
            }
        }
        Settings.Secure.putStringForUser(resolver, PANEL_ACTIONS, String.join(",", ordered),
                UserHandle.USER_CURRENT);
    }
}
