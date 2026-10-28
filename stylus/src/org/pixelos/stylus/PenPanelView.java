/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.pixelos.stylus;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/*
 * Draws the pen actions as a half ring cut into pie slices, with an icon in
 * each slice and the name of the slice under the pen in the hub. The flat
 * side sits on the pen, and the half ring opens upwards unless there is no
 * room above. An action runs when the pen or a finger is lifted from its
 * slice, and lifting from anywhere else closes the panel.
 */
final class PenPanelView extends View {

    interface Listener {
        void onSelected(String action);

        void onDismiss();

        void onTouched();
    }

    private static final int SCRIM = 0x40000000;

    private static final float THICKNESS_DP = 56;
    private static final float GAP_DP = 4;
    // Radius of the rounded corners of the slices and the hub
    private static final float CORNER_DP = 8;
    private static final float ICON_DP = 24;
    private static final float MARGIN_DP = 8;
    private static final float MIN_MID_RADIUS_DP = 84;
    // How much arc each slice needs at its middle to fit its icon
    private static final float MIN_SLICE_DP = 40;
    private static final float TEXT_SIZE_SP = 11;

    private final List<String> mActions;
    private final List<String> mLabels;
    private final PointF mCenter;
    private final Listener mListener;

    private final int mSliceColor;
    private final int mPressedColor;
    private final int mHubColor;
    private final int mIconColor;
    private final int mPressedIconColor;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable[] mIcons;
    private final Path[] mSlices;
    private final RectF mHub = new RectF();
    private final float mGap;
    private final float mCorner;
    private final float mIconSize;
    private final float mMargin;
    private final float mInner;
    private final float mOuter;
    private final float mSweep;

    private boolean mUp = true;
    private StaticLayout mHubText;
    private int mPressed = -1;

    PenPanelView(Context context, List<String> actions, List<String> labels, PointF center,
            Listener listener) {
        super(context);
        mActions = actions;
        mLabels = labels;
        mCenter = new PointF(center.x, center.y);
        mListener = listener;

        // Colors come from the system palette, so the panel follows the wallpaper
        // accent and the light or dark theme like the rest of the UI
        final boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        mSliceColor = context.getColor(night
                ? android.R.color.system_neutral1_700 : android.R.color.system_neutral1_100);
        mHubColor = context.getColor(night
                ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_50);
        mPressedColor = context.getColor(night
                ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
        mIconColor = context.getColor(night
                ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900);
        mPressedIconColor = context.getColor(night
                ? android.R.color.system_accent1_900 : android.R.color.system_accent1_0);

        final float density = context.getResources().getDisplayMetrics().density;
        mGap = GAP_DP * density;
        mCorner = CORNER_DP * density;
        mIconSize = ICON_DP * density;
        mMargin = MARGIN_DP * density;

        final int count = actions.size();
        mSweep = 180f / count;
        final float middle = Math.max(MIN_MID_RADIUS_DP,
                MIN_SLICE_DP * count / (float) Math.PI) * density;
        mInner = middle - THICKNESS_DP / 2 * density;
        mOuter = middle + THICKNESS_DP / 2 * density;

        // Rounded corners come from stroking the outline of a shrunken shape
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeWidth(2 * mCorner);

        mTextPaint.setColor(mIconColor);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                TEXT_SIZE_SP, context.getResources().getDisplayMetrics()));

        mIcons = new Drawable[count];
        for (int i = 0; i < count; i++) {
            mIcons[i] = context.getDrawable(iconFor(actions.get(i))).mutate();
        }
        mSlices = new Path[count];
    }

    /** Resource of the label for an action. */
    static int labelFor(String action) {
        switch (action) {
            case PenGestures.ACTION_ASSISTANT:
                return R.string.pen_gesture_action_assistant;
            case PenGestures.ACTION_VOICE_SEARCH:
                return R.string.pen_gesture_action_voice_search;
            case PenGestures.ACTION_CAMERA:
                return R.string.pen_gesture_action_camera;
            case PenGestures.ACTION_TAKE_PICTURE:
                return R.string.pen_gesture_action_take_picture;
            case PenGestures.ACTION_FLASHLIGHT:
                return R.string.pen_gesture_action_flashlight;
            case PenGestures.ACTION_LAST_APP:
                return R.string.pen_gesture_action_last_app;
            case PenGestures.ACTION_RECENTS:
                return R.string.pen_gesture_action_recents;
            case PenGestures.ACTION_SCREENSHOT:
                return R.string.pen_gesture_action_screenshot;
            case PenGestures.ACTION_PARTIAL_SCREENSHOT:
                return R.string.pen_gesture_action_partial_screenshot;
            case PenGestures.ACTION_PLAY_PAUSE:
                return R.string.pen_gesture_action_play_pause;
            case PenGestures.ACTION_VOLUME_UP:
                return R.string.pen_gesture_action_volume_up;
            case PenGestures.ACTION_VOLUME_DOWN:
                return R.string.pen_gesture_action_volume_down;
            case PenGestures.ACTION_SCREEN_OFF:
                return R.string.pen_gesture_action_screen_off;
            case PenGestures.ACTION_PAGE_UP:
                return R.string.pen_gesture_action_page_up;
            case PenGestures.ACTION_PAGE_DOWN:
                return R.string.pen_gesture_action_page_down;
            default:
                return R.string.pen_gesture_action_none;
        }
    }

    /** Resource of the icon for an action. */
    private static int iconFor(String action) {
        switch (action) {
            case PenGestures.ACTION_ASSISTANT:
                return R.drawable.ic_pen_action_assistant;
            case PenGestures.ACTION_VOICE_SEARCH:
                return R.drawable.ic_pen_action_voice_search;
            case PenGestures.ACTION_CAMERA:
                return R.drawable.ic_pen_action_camera;
            case PenGestures.ACTION_TAKE_PICTURE:
                return R.drawable.ic_pen_action_take_picture;
            case PenGestures.ACTION_FLASHLIGHT:
                return R.drawable.ic_pen_action_flashlight;
            case PenGestures.ACTION_LAST_APP:
                return R.drawable.ic_pen_action_last_app;
            case PenGestures.ACTION_RECENTS:
                return R.drawable.ic_pen_action_recents;
            case PenGestures.ACTION_SCREENSHOT:
                return R.drawable.ic_pen_action_screenshot;
            case PenGestures.ACTION_PARTIAL_SCREENSHOT:
                return R.drawable.ic_pen_action_partial_screenshot;
            case PenGestures.ACTION_PLAY_PAUSE:
                return R.drawable.ic_pen_action_play_pause;
            case PenGestures.ACTION_VOLUME_UP:
                return R.drawable.ic_pen_action_volume_up;
            case PenGestures.ACTION_VOLUME_DOWN:
                return R.drawable.ic_pen_action_volume_down;
            case PenGestures.ACTION_SCREEN_OFF:
                return R.drawable.ic_pen_action_screen_off;
            case PenGestures.ACTION_PAGE_UP:
                return R.drawable.ic_pen_action_page_up;
            default:
                return R.drawable.ic_pen_action_page_down;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Keep the whole half ring on the screen, above the pen if there is room
        final float reach = mOuter + mMargin;
        mCenter.x = clamp(mCenter.x, reach, w - reach);
        if (mCenter.y - reach >= 0) {
            mUp = true;
        } else if (mCenter.y + reach <= h) {
            mUp = false;
        } else {
            mUp = true;
            mCenter.y = clamp(mCenter.y, reach, h - reach);
        }

        final float hub = mInner - mGap - mCorner;
        mHub.set(mCenter.x - hub, mCenter.y - hub, mCenter.x + hub, mCenter.y + hub);
        // Each slice is shrunk by the corner radius and then stroked with a round
        // join, which rounds every corner. The gap stays the same width at the
        // inner and outer edge.
        final float outerRadius = mOuter - mCorner;
        final float innerRadius = mInner + mCorner;
        final RectF outer = new RectF(mCenter.x - outerRadius, mCenter.y - outerRadius,
                mCenter.x + outerRadius, mCenter.y + outerRadius);
        final RectF inner = new RectF(mCenter.x - innerRadius, mCenter.y - innerRadius,
                mCenter.x + innerRadius, mCenter.y + innerRadius);
        // A slice never shrinks past a sliver, however many there are
        final float maxInset = (mSweep - 1) / 2;
        final float outerInset = Math.min(maxInset,
                (float) Math.toDegrees((mGap / 2 + mCorner) / mOuter));
        final float innerInset = Math.min(maxInset,
                (float) Math.toDegrees((mGap / 2 + mCorner) / mInner));
        for (int i = 0; i < mSlices.length; i++) {
            final float start = sliceStart(i);
            final Path path = new Path();
            path.arcTo(outer, start + outerInset, mSweep - 2 * outerInset);
            path.arcTo(inner, start + mSweep - innerInset, -(mSweep - 2 * innerInset));
            path.close();
            mSlices[i] = path;
        }
    }

    /** Canvas angle where slice i starts, numbering the slices from left to right. */
    private float sliceStart(int i) {
        return mUp ? 180 + i * mSweep : 180 - (i + 1) * mSweep;
    }

    private static float clamp(float value, float min, float max) {
        return max < min ? (min + max) / 2 : Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(SCRIM);

        final float middle = (mInner + mOuter) / 2;
        for (int i = 0; i < mSlices.length; i++) {
            final boolean pressed = i == mPressed;
            mPaint.setColor(pressed ? mPressedColor : mSliceColor);
            canvas.drawPath(mSlices[i], mPaint);

            final double angle = Math.toRadians(sliceStart(i) + mSweep / 2);
            final float x = mCenter.x + (float) (middle * Math.cos(angle));
            final float y = mCenter.y + (float) (middle * Math.sin(angle));
            final int half = (int) (mIconSize / 2);
            mIcons[i].setBounds((int) x - half, (int) y - half, (int) x + half, (int) y + half);
            mIcons[i].setTint(pressed ? mPressedIconColor : mIconColor);
            mIcons[i].draw(canvas);
        }

        // The hub is the half disc the slices sit around, and names the pressed slice
        mPaint.setColor(mHubColor);
        canvas.drawArc(mHub, mUp ? 180 : 0, 180, true, mPaint);
        if (mHubText != null) {
            canvas.save();
            canvas.translate(mCenter.x - mHubText.getWidth() / 2f,
                    mCenter.y + (mUp ? -1 : 1) * mInner / 2 - mHubText.getHeight() / 2f);
            mHubText.draw(canvas);
            canvas.restore();
        }
    }

    private int sliceAt(float x, float y) {
        final float dx = x - mCenter.x;
        final float dy = y - mCenter.y;
        final double distance = Math.hypot(dx, dy);
        if (distance < mInner || distance > mOuter) {
            return -1;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) {
            angle += 360;
        }
        final int index;
        if (mUp) {
            if (angle < 180) {
                return -1;
            }
            index = (int) ((angle - 180) / mSweep);
        } else {
            if (angle > 180) {
                return -1;
            }
            index = (int) ((180 - angle) / mSweep);
        }
        return Math.min(index, mActions.size() - 1);
    }

    private void press(int index) {
        if (index == mPressed) {
            return;
        }
        mPressed = index;
        if (index < 0) {
            mHubText = null;
        } else {
            final String label = mLabels.get(index);
            mHubText = StaticLayout.Builder.obtain(label, 0, label.length(), mTextPaint,
                            (int) (mInner * 1.5f))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build();
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                mListener.onTouched();
                press(sliceAt(event.getX(), event.getY()));
                break;
            case MotionEvent.ACTION_UP:
                final int index = sliceAt(event.getX(), event.getY());
                if (index >= 0) {
                    mListener.onSelected(mActions.get(index));
                } else {
                    mListener.onDismiss();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mListener.onDismiss();
                break;
        }
        return true;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                press(sliceAt(event.getX(), event.getY()));
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                press(-1);
                break;
        }
        return true;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The half ring would be in the wrong place after a rotation
        mListener.onDismiss();
    }
}
