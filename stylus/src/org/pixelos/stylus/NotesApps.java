/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.app.ActivityTaskManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;

import java.util.List;
import java.util.Set;

/** Tells whether the app in front is a notes app that handles the pen gestures itself. */
final class NotesApps {

    private static final String TAG = "NotesApps";

    private static final Set<String> KNOWN = new ArraySet<>(new String[] {
            "com.onyx.galaxy.global.note",
            "com.samsung.android.app.notes",
            "com.microsoft.office.onenote",
            "com.google.android.keep",
            "com.xiaomi.notes",
            "com.miui.notes",
            "com.moleskine.notes",
            "com.metamoji.noteanytime",
            "com.wacom.notes",
            "com.flexcil.flexcilnote",
            "com.steadfastinnovation.android.projectpapyrus",
            "com.myscript.nebo.beta",
            "com.myscript.nebo",
            "com.wetransfer.paper",
            "com.notability.notability",
            "com.evernote",
            "com.zoho.notebook",
            "com.nebo",
    });

    private final Context mContext;
    private final RoleManager mRoles;

    NotesApps(Context context) {
        mContext = context;
        mRoles = context.getSystemService(RoleManager.class);
    }

    boolean isInFront() {
        final String pkg = frontPackage();
        if (pkg == null) {
            return false;
        }
        if (KNOWN.contains(pkg)) {
            return true;
        }
        if (mRoles == null) {
            return false;
        }
        final List<String> holders = mRoles.getRoleHoldersAsUser(RoleManager.ROLE_NOTES,
                UserHandle.of(android.app.ActivityManager.getCurrentUser()));
        return holders.contains(pkg);
    }

    private static String frontPackage() {
        try {
            final ActivityTaskManager.RootTaskInfo info =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            final ComponentName top = info != null ? info.topActivity : null;
            return top != null ? top.getPackageName() : null;
        } catch (Exception e) {
            Log.w(TAG, "Could not read the focused task", e);
            return null;
        }
    }
}
