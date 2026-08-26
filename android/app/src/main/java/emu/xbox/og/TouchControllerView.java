package emu.xbox.og;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On-screen Xbox controller: two analog sticks, ABXY, LB/RB, LT/RT (digital),
 * L3/R3, Back/Start and a generic pause button. Rendered from the Kenney
 * "Mobile Controls" pack (CC0), Style C (white outline, transparent
 * background): https://kenney.nl/assets/mobile-controls
 *
 * Each stick tracks its own pointer id across the gesture so a second finger
 * on a button never disturbs it, and vice versa; buttons are re-evaluated
 * from the live pointer list every event, excluding whichever pointers the
 * sticks currently own.
 *
 * Every control's default position/size/opacity can be overridden by the
 * user (see {@link TouchLayoutStore}); this same class also implements the
 * layout editor's "edit mode" (see {@link #setEditMode}), since it already
 * owns every control's geometry.
 */
public class TouchControllerView extends View {
    interface Listener {
        void onTouchButton(int button, boolean pressed);
        void onTouchAxis(int axis, float value);
        void onTouchPause();
    }

    /** Notified when the edit-mode selection changes, to sync an editor panel's UI. */
    interface EditListener {
        void onSelectionChanged();
    }

    static final String KEY_LEFT_STICK = "left_stick";
    static final String KEY_RIGHT_STICK = "right_stick";
    static final String KEY_DPAD = "dpad";
    static final String KEY_ABXY = "abxy";
    static final String KEY_LB = "lb";
    static final String KEY_RB = "rb";
    static final String KEY_LT = "lt";
    static final String KEY_RT = "rt";
    static final String KEY_L3 = "l3";
    static final String KEY_R3 = "r3";
    static final String KEY_BACK = "back";
    static final String KEY_START = "start";
    static final String KEY_PAUSE = "pause";

    private static final int CODE_PAUSE = -1;
    private static final int KIND_BUTTON = 0;
    private static final int KIND_AXIS_DIGITAL = 1;
    private static final int KIND_PAUSE = 2;

    private static final float STICK_BASE_DP = 120f;
    private static final float STICK_CAP_DP = 60f;
    private static final float ROUND_DP = 62f;
    private static final float SMALL_DP = 42f;
    private static final float PILL_W_DP = 84f;
    private static final float PILL_H_DP = 42f;
    private static final float MARGIN_DP = 14f;
    private static final float GAP_DP = 8f;
    private static final float CLUSTER_SPREAD_DP = 50f;
    private static final float DPAD_DP = 96f;
    private static final float STICK_CAPTURE_SCALE = 1.5f;
    private static final float TOUCH_SLOP_DP = 8f;
    private static final long LONG_PRESS_MS = 450L;
    private static final int PRESSED_ALPHA = 255;

    private final class Stick {
        final int axisXId;
        final int axisYId;
        float cx, cy;
        float baseRadius;
        float capRadius;
        float restAlpha;
        int activePointerId = MotionEvent.INVALID_POINTER_ID;
        float offsetX;
        float offsetY;

        Stick(int axisXId, int axisYId) {
            this.axisXId = axisXId;
            this.axisYId = axisYId;
        }
    }

    private final class Dpad {
        float cx, cy, radius;
        float restAlpha;
        boolean left, up, right, down;
    }

    private final class Btn {
        final Bitmap art;
        final Bitmap glyph;
        final String label;
        final int kind;
        final int id;
        float cx, cy;
        float halfW, halfH;
        float restAlpha;
        boolean pressed;

        Btn(Bitmap art, Bitmap glyph, String label, int kind, int id) {
            this.art = art;
            this.glyph = glyph;
            this.label = label;
            this.kind = kind;
            this.id = id;
        }

        boolean contains(float x, float y) {
            return Math.abs(x - cx) <= halfW * 1.15f && Math.abs(y - cy) <= halfH * 1.15f;
        }
    }

    /** Snapshot of a control's on-screen bounding box, used only by the editor. */
    private final class Editable {
        float cx, cy, halfW, halfH;
    }

    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect destRect = new Rect();
    private final float density;
    private Listener listener;

    private final Bitmap stickBaseArt;
    private final Bitmap stickCapArt;
    private final Bitmap roundArt;
    private final Bitmap pillArt;
    private final Bitmap dpadArt;
    private final Bitmap glyphA;
    private final Bitmap glyphB;
    private final Bitmap glyphX;
    private final Bitmap glyphY;
    private final Bitmap glyphPause;

    private final Stick leftStick = new Stick(AndroidInputMapper.AXIS_LSTICK_X, AndroidInputMapper.AXIS_LSTICK_Y);
    private final Stick rightStick = new Stick(AndroidInputMapper.AXIS_RSTICK_X, AndroidInputMapper.AXIS_RSTICK_Y);
    private final Dpad dpad = new Dpad();
    private final Btn[] buttons;

    private final Map<String, TouchControlOverride> overrides;
    private final Map<String, Editable> editables = new LinkedHashMap<>();

    private boolean editMode;
    private EditListener editListener;
    private final Set<String> selected = new LinkedHashSet<>();
    private String pendingKey;
    private boolean longPressFired;
    private boolean movedBeyondSlop;
    private boolean dragging;
    private int dragPointerId = MotionEvent.INVALID_POINTER_ID;
    private float dragStartX, dragStartY;
    private final Map<String, float[]> dragStartOffsets = new HashMap<>();
    private final Map<String, float[]> spacingBase = new HashMap<>();
    private float spacingCentroidX, spacingCentroidY;
    private final Runnable longPressRunnable = this::onLongPress;

    public TouchControllerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        density = getResources().getDisplayMetrics().density;
        overrides = TouchLayoutStore.load(context);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 12f);
        textPaint.setFakeBoldText(true);

        stickBaseArt = decode(R.drawable.touch_stick_base);
        stickCapArt = decode(R.drawable.touch_stick_cap);
        roundArt = decode(R.drawable.touch_btn_round);
        pillArt = decode(R.drawable.touch_btn_pill);
        dpadArt = decode(R.drawable.touch_dpad);
        glyphA = decode(R.drawable.touch_glyph_a);
        glyphB = decode(R.drawable.touch_glyph_b);
        glyphX = decode(R.drawable.touch_glyph_x);
        glyphY = decode(R.drawable.touch_glyph_y);
        glyphPause = decode(R.drawable.touch_glyph_pause);

        buttons = new Btn[]{
                new Btn(roundArt, glyphA, null, KIND_BUTTON, AndroidInputMapper.BTN_A),
                new Btn(roundArt, glyphB, null, KIND_BUTTON, AndroidInputMapper.BTN_B),
                new Btn(roundArt, glyphX, null, KIND_BUTTON, AndroidInputMapper.BTN_X),
                new Btn(roundArt, glyphY, null, KIND_BUTTON, AndroidInputMapper.BTN_Y),
                new Btn(pillArt, null, "LB", KIND_BUTTON, AndroidInputMapper.BTN_LB),
                new Btn(pillArt, null, "RB", KIND_BUTTON, AndroidInputMapper.BTN_RB),
                new Btn(pillArt, null, "LT", KIND_AXIS_DIGITAL, AndroidInputMapper.AXIS_LTRIGGER),
                new Btn(pillArt, null, "RT", KIND_AXIS_DIGITAL, AndroidInputMapper.AXIS_RTRIGGER),
                new Btn(roundArt, null, "L3", KIND_BUTTON, AndroidInputMapper.BTN_LS),
                new Btn(roundArt, null, "R3", KIND_BUTTON, AndroidInputMapper.BTN_RS),
                new Btn(roundArt, null, "BACK", KIND_BUTTON, AndroidInputMapper.BTN_BACK),
                new Btn(roundArt, null, "START", KIND_BUTTON, AndroidInputMapper.BTN_START),
                new Btn(roundArt, glyphPause, null, KIND_PAUSE, CODE_PAUSE),
        };
    }

    private Bitmap decode(int resId) {
        return BitmapFactory.decodeResource(getResources(), resId);
    }

    private float dp(float value) {
        return value * density;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    // ---- Edit mode API (used by TouchLayoutEditorActivity) ----

    void setEditMode(boolean enabled) {
        editMode = enabled;
        if (!enabled) {
            selected.clear();
        }
        invalidate();
    }

    void setEditListener(EditListener l) {
        editListener = l;
    }

    List<String> getSelectedKeys() {
        return new ArrayList<>(selected);
    }

    boolean isAbxySelectedAlone() {
        return selected.size() == 1 && selected.contains(KEY_ABXY);
    }

    float getSelectionScale() {
        if (selected.isEmpty()) {
            return 1f;
        }
        return overrideOrDefault(selected.iterator().next()).scale;
    }

    float getAbxyButtonScale() {
        return overrideOrDefault(KEY_ABXY).scale2;
    }

    float getSelectionOpacity() {
        if (selected.isEmpty()) {
            return DEFAULT_OVERRIDE.alpha;
        }
        return overrideOrDefault(selected.iterator().next()).alpha;
    }

    void setSelectionScale(float scale) {
        for (String key : selected) {
            getOrCreateOverride(key).scale = scale;
        }
        relayoutAndSave();
    }

    void setAbxyButtonScale(float scale) {
        if (!selected.contains(KEY_ABXY)) {
            return;
        }
        getOrCreateOverride(KEY_ABXY).scale2 = scale;
        relayoutAndSave();
    }

    void setSelectionOpacity(float alpha) {
        for (String key : selected) {
            getOrCreateOverride(key).alpha = alpha;
        }
        relayoutAndSave();
    }

    /** Call once when the multi-selection is (re)formed, before dragging the spacing slider. */
    void beginSpacingAdjust() {
        spacingBase.clear();
        if (selected.size() < 2) {
            return;
        }
        float sumX = 0f;
        float sumY = 0f;
        for (String key : selected) {
            Editable e = editables.get(key);
            if (e == null) {
                continue;
            }
            TouchControlOverride ov = overrideOrDefault(key);
            spacingBase.put(key, new float[]{e.cx, e.cy, ov.dx, ov.dy});
            sumX += e.cx;
            sumY += e.cy;
        }
        if (spacingBase.isEmpty()) {
            return;
        }
        spacingCentroidX = sumX / spacingBase.size();
        spacingCentroidY = sumY / spacingBase.size();
    }

    /** Scales the selection's spread around its centroid; 1.0 = the spacing captured at {@link #beginSpacingAdjust}. */
    void setSelectionSpacing(float factor) {
        for (Map.Entry<String, float[]> entry : spacingBase.entrySet()) {
            String key = entry.getKey();
            float[] base = entry.getValue();
            float targetX = spacingCentroidX + (base[0] - spacingCentroidX) * factor;
            float targetY = spacingCentroidY + (base[1] - spacingCentroidY) * factor;
            TouchControlOverride ov = getOrCreateOverride(key);
            ov.dx = base[2] + (targetX - base[0]) / density;
            ov.dy = base[3] + (targetY - base[1]) / density;
        }
        relayoutAndSave();
    }

    void resetLayout() {
        overrides.clear();
        selected.clear();
        spacingBase.clear();
        TouchLayoutStore.clear(getContext());
        layoutControls(getWidth(), getHeight());
        notifySelectionChanged();
        invalidate();
    }

    private void relayoutAndSave() {
        layoutControls(getWidth(), getHeight());
        TouchLayoutStore.save(getContext(), overrides);
        invalidate();
    }

    private static final TouchControlOverride DEFAULT_OVERRIDE = new TouchControlOverride();

    private TouchControlOverride overrideOrDefault(String key) {
        TouchControlOverride ov = overrides.get(key);
        return ov != null ? ov : DEFAULT_OVERRIDE;
    }

    private TouchControlOverride getOrCreateOverride(String key) {
        TouchControlOverride ov = overrides.get(key);
        if (ov == null) {
            ov = new TouchControlOverride();
            overrides.put(key, ov);
        }
        return ov;
    }

    // ---- Layout ----

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutControls(w, h);
    }

    private void layoutControls(int w, int h) {
        float margin = dp(MARGIN_DP);
        float gap = dp(GAP_DP);
        float stickR = dp(STICK_BASE_DP) / 2f;
        float capR = dp(STICK_CAP_DP) / 2f;
        float roundR = dp(ROUND_DP) / 2f;
        float smallR = dp(SMALL_DP) / 2f;
        float pillW = dp(PILL_W_DP);
        float pillH = dp(PILL_H_DP);
        float spread = dp(CLUSTER_SPREAD_DP);
        float dpadR = dp(DPAD_DP) / 2f;

        // Fixed reference anchors: every control's default comes only from
        // these, never from another control's (possibly overridden) live
        // position, so editing one control never silently moves another.
        float leftStickBaseCx = margin + stickR;
        float leftStickBaseCy = h - margin - stickR;
        float dpadBaseCx = leftStickBaseCx + stickR + gap + dpadR;
        float dpadBaseCy = leftStickBaseCy;
        float clusterBaseCx = w - margin - roundR - spread;
        float clusterBaseCy = h - margin - roundR - spread;
        float rightStickBaseCx = clusterBaseCx - spread - roundR - gap - stickR;
        float rightStickBaseCy = h - margin - stickR;
        float l3BaseCx = leftStickBaseCx;
        float l3BaseCy = leftStickBaseCy - stickR - gap - smallR;
        float r3BaseCx = rightStickBaseCx;
        float r3BaseCy = rightStickBaseCy - stickR - gap - smallR;
        float ltBaseCx = margin + pillW / 2f;
        float ltBaseCy = margin + pillH / 2f;
        float lbBaseCx = margin + pillW / 2f;
        float lbBaseCy = margin + pillH + gap + pillH / 2f;
        float rtBaseCx = w - margin - pillW / 2f;
        float rtBaseCy = margin + pillH / 2f;
        float rbBaseCx = w - margin - pillW / 2f;
        float rbBaseCy = margin + pillH + gap + pillH / 2f;
        float centerY = h - margin - roundR;
        float centerGap = roundR * 2f + gap;
        float backBaseCx = w / 2f - centerGap;
        float startBaseCx = w / 2f + centerGap;
        float pauseBaseCx = w / 2f;

        applyStick(leftStick, KEY_LEFT_STICK, leftStickBaseCx, leftStickBaseCy, stickR, capR);
        applyStick(rightStick, KEY_RIGHT_STICK, rightStickBaseCx, rightStickBaseCy, stickR, capR);

        TouchControlOverride dpadOv = overrideOrDefault(KEY_DPAD);
        dpad.cx = dpadBaseCx + dp(dpadOv.dx);
        dpad.cy = dpadBaseCy + dp(dpadOv.dy);
        dpad.radius = dpadR * dpadOv.scale;
        dpad.restAlpha = dpadOv.alpha;
        putEditable(KEY_DPAD, dpad.cx, dpad.cy, dpad.radius, dpad.radius);

        // ABXY: a fixed cross. Its own override moves/resizes the whole
        // cluster (scale) and independently resizes the 4 buttons (scale2);
        // the buttons are never individually selectable or movable.
        TouchControlOverride abxyOv = overrideOrDefault(KEY_ABXY);
        float clusterCx = clusterBaseCx + dp(abxyOv.dx);
        float clusterCy = clusterBaseCy + dp(abxyOv.dy);
        float clusterSpread = spread * abxyOv.scale;
        float clusterButtonR = roundR * abxyOv.scale2;
        setAbxyBox(findByGlyphCode(AndroidInputMapper.BTN_Y), clusterCx, clusterCy - clusterSpread, clusterButtonR, abxyOv.alpha);
        setAbxyBox(findByGlyphCode(AndroidInputMapper.BTN_A), clusterCx, clusterCy + clusterSpread, clusterButtonR, abxyOv.alpha);
        setAbxyBox(findByGlyphCode(AndroidInputMapper.BTN_X), clusterCx - clusterSpread, clusterCy, clusterButtonR, abxyOv.alpha);
        setAbxyBox(findByGlyphCode(AndroidInputMapper.BTN_B), clusterCx + clusterSpread, clusterCy, clusterButtonR, abxyOv.alpha);
        putEditable(KEY_ABXY, clusterCx, clusterCy, clusterSpread + clusterButtonR, clusterSpread + clusterButtonR);

        setBox(findByLabel("L3"), KEY_L3, l3BaseCx, l3BaseCy, smallR, smallR);
        setBox(findByLabel("R3"), KEY_R3, r3BaseCx, r3BaseCy, smallR, smallR);
        setBox(findByLabel("LT"), KEY_LT, ltBaseCx, ltBaseCy, pillW / 2f, pillH / 2f);
        setBox(findByLabel("LB"), KEY_LB, lbBaseCx, lbBaseCy, pillW / 2f, pillH / 2f);
        setBox(findByLabel("RT"), KEY_RT, rtBaseCx, rtBaseCy, pillW / 2f, pillH / 2f);
        setBox(findByLabel("RB"), KEY_RB, rbBaseCx, rbBaseCy, pillW / 2f, pillH / 2f);
        setBox(findByLabel("BACK"), KEY_BACK, backBaseCx, centerY, roundR, roundR);
        setBox(findByCodeKind(KIND_PAUSE), KEY_PAUSE, pauseBaseCx, centerY, roundR, roundR);
        setBox(findByLabel("START"), KEY_START, startBaseCx, centerY, roundR, roundR);
    }

    private void applyStick(Stick s, String key, float baseCx, float baseCy, float baseRadius, float baseCapRadius) {
        TouchControlOverride ov = overrideOrDefault(key);
        s.cx = baseCx + dp(ov.dx);
        s.cy = baseCy + dp(ov.dy);
        s.baseRadius = baseRadius * ov.scale;
        s.capRadius = baseCapRadius * ov.scale;
        s.restAlpha = ov.alpha;
        putEditable(key, s.cx, s.cy, s.baseRadius, s.baseRadius);
    }

    private void setBox(Btn b, String key, float baseCx, float baseCy, float baseHalfW, float baseHalfH) {
        if (b == null) {
            return;
        }
        TouchControlOverride ov = overrideOrDefault(key);
        b.cx = baseCx + dp(ov.dx);
        b.cy = baseCy + dp(ov.dy);
        b.halfW = baseHalfW * ov.scale;
        b.halfH = baseHalfH * ov.scale;
        b.restAlpha = ov.alpha;
        putEditable(key, b.cx, b.cy, b.halfW, b.halfH);
    }

    private void setAbxyBox(Btn b, float cx, float cy, float r, float alpha) {
        if (b == null) {
            return;
        }
        b.cx = cx;
        b.cy = cy;
        b.halfW = r;
        b.halfH = r;
        b.restAlpha = alpha;
    }

    private void putEditable(String key, float cx, float cy, float halfW, float halfH) {
        Editable e = editables.get(key);
        if (e == null) {
            e = new Editable();
            editables.put(key, e);
        }
        e.cx = cx;
        e.cy = cy;
        e.halfW = halfW;
        e.halfH = halfH;
    }

    private Btn findByGlyphCode(int code) {
        for (Btn b : buttons) {
            if (b.kind == KIND_BUTTON && b.id == code && b.glyph != null) {
                return b;
            }
        }
        return null;
    }

    private Btn findByLabel(String label) {
        for (Btn b : buttons) {
            if (label.equals(b.label)) {
                return b;
            }
        }
        return null;
    }

    private Btn findByCodeKind(int kind) {
        for (Btn b : buttons) {
            if (b.kind == kind) {
                return b;
            }
        }
        return null;
    }

    // ---- Drawing ----

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawStick(canvas, leftStick);
        drawStick(canvas, rightStick);
        drawDpad(canvas);
        for (Btn b : buttons) {
            drawButton(canvas, b);
        }
        if (editMode) {
            drawSelectionOutlines(canvas);
        }
    }

    private void drawSelectionOutlines(Canvas canvas) {
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(3f));
        for (Map.Entry<String, Editable> entry : editables.entrySet()) {
            Editable e = entry.getValue();
            boolean isSelected = selected.contains(entry.getKey());
            selectionPaint.setColor(isSelected ? 0xFF33B5FF : 0x66FFFFFF);
            canvas.drawRect(e.cx - e.halfW, e.cy - e.halfH, e.cx + e.halfW, e.cy + e.halfH, selectionPaint);
        }
    }

    private void drawDpad(Canvas canvas) {
        boolean anyPressed = dpad.left || dpad.up || dpad.right || dpad.down;
        artPaint.setAlpha(anyPressed ? PRESSED_ALPHA : (int) (255 * dpad.restAlpha));
        destRect.set((int) (dpad.cx - dpad.radius), (int) (dpad.cy - dpad.radius),
                (int) (dpad.cx + dpad.radius), (int) (dpad.cy + dpad.radius));
        canvas.drawBitmap(dpadArt, null, destRect, artPaint);
    }

    private void drawStick(Canvas canvas, Stick s) {
        artPaint.setAlpha((int) (255 * s.restAlpha));
        destRect.set((int) (s.cx - s.baseRadius), (int) (s.cy - s.baseRadius),
                (int) (s.cx + s.baseRadius), (int) (s.cy + s.baseRadius));
        canvas.drawBitmap(stickBaseArt, null, destRect, artPaint);

        artPaint.setAlpha(s.activePointerId != MotionEvent.INVALID_POINTER_ID ? PRESSED_ALPHA : (int) (255 * s.restAlpha));
        float capCx = s.cx + s.offsetX;
        float capCy = s.cy + s.offsetY;
        destRect.set((int) (capCx - s.capRadius), (int) (capCy - s.capRadius),
                (int) (capCx + s.capRadius), (int) (capCy + s.capRadius));
        canvas.drawBitmap(stickCapArt, null, destRect, artPaint);
    }

    private void drawButton(Canvas canvas, Btn b) {
        artPaint.setAlpha(b.pressed ? PRESSED_ALPHA : (int) (255 * b.restAlpha));
        destRect.set((int) (b.cx - b.halfW), (int) (b.cy - b.halfH),
                (int) (b.cx + b.halfW), (int) (b.cy + b.halfH));
        canvas.drawBitmap(b.art, null, destRect, artPaint);

        if (b.glyph != null) {
            float gw = b.halfW * 0.9f;
            float gh = b.halfH * 0.9f;
            destRect.set((int) (b.cx - gw), (int) (b.cy - gh), (int) (b.cx + gw), (int) (b.cy + gh));
            artPaint.setAlpha(255);
            canvas.drawBitmap(b.glyph, null, destRect, artPaint);
        } else if (b.label != null) {
            canvas.drawText(b.label, b.cx, b.cy + textPaint.getTextSize() * 0.35f, textPaint);
        }
    }

    // ---- Touch: play mode ----

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editMode) {
            return handleEditTouch(event);
        }

        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_CANCEL) {
            releaseStick(leftStick);
            releaseStick(rightStick);
            for (Btn b : buttons) {
                setButtonPressed(b, false);
            }
            setDpadDirections(false, false, false, false);
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int idx = event.getActionIndex();
            int pid = event.getPointerId(idx);
            float x = event.getX(idx);
            float y = event.getY(idx);
            tryEngageStick(leftStick, pid, x, y);
            tryEngageStick(rightStick, pid, x, y);
        }

        updateStick(leftStick, event);
        updateStick(rightStick, event);

        int upIndex = -1;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            upIndex = event.getActionIndex();
            int upPid = event.getPointerId(upIndex);
            if (leftStick.activePointerId == upPid) {
                releaseStick(leftStick);
            }
            if (rightStick.activePointerId == upPid) {
                releaseStick(rightStick);
            }
        }

        updateDpad(event, upIndex);

        for (Btn b : buttons) {
            boolean down = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == upIndex) {
                    continue;
                }
                int pid = event.getPointerId(i);
                if (pid == leftStick.activePointerId || pid == rightStick.activePointerId) {
                    continue;
                }
                if (b.contains(event.getX(i), event.getY(i))) {
                    down = true;
                    break;
                }
            }
            setButtonPressed(b, down);
        }

        invalidate();
        return true;
    }

    private void tryEngageStick(Stick s, int pid, float x, float y) {
        if (s.activePointerId != MotionEvent.INVALID_POINTER_ID) {
            return;
        }
        float captureR = s.baseRadius * STICK_CAPTURE_SCALE;
        float dx = x - s.cx;
        float dy = y - s.cy;
        if (dx * dx + dy * dy <= captureR * captureR) {
            s.activePointerId = pid;
        }
    }

    private void updateStick(Stick s, MotionEvent event) {
        if (s.activePointerId == MotionEvent.INVALID_POINTER_ID) {
            return;
        }
        int idx = event.findPointerIndex(s.activePointerId);
        if (idx == -1) {
            return;
        }
        float dx = event.getX(idx) - s.cx;
        float dy = event.getY(idx) - s.cy;
        // The cap's center is allowed to travel all the way to the base's
        // outer rim (not rim-minus-cap-radius), so at full deflection the
        // cap overlaps the base edge rather than staying fully inside it.
        float dist = (float) Math.hypot(dx, dy);
        float maxDist = s.baseRadius;
        if (dist > maxDist) {
            float scale = maxDist / dist;
            dx *= scale;
            dy *= scale;
        }
        s.offsetX = dx;
        s.offsetY = dy;
        if (listener != null) {
            listener.onTouchAxis(s.axisXId, dx / maxDist);
            listener.onTouchAxis(s.axisYId, dy / maxDist);
        }
    }

    private void updateDpad(MotionEvent event, int upIndex) {
        boolean left = false;
        boolean up = false;
        boolean right = false;
        boolean down = false;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == upIndex) {
                continue;
            }
            int pid = event.getPointerId(i);
            if (pid == leftStick.activePointerId || pid == rightStick.activePointerId) {
                continue;
            }
            float dx = event.getX(i) - dpad.cx;
            float dy = event.getY(i) - dpad.cy;
            if (dx * dx + dy * dy > dpad.radius * dpad.radius) {
                continue;
            }
            // Dominant-axis test: the plus-shaped pad has no diagonal
            // artwork, so each touch resolves to exactly one direction.
            if (Math.abs(dx) > Math.abs(dy)) {
                if (dx > 0) {
                    right = true;
                } else {
                    left = true;
                }
            } else {
                if (dy > 0) {
                    down = true;
                } else {
                    up = true;
                }
            }
        }
        setDpadDirections(left, up, right, down);
    }

    private void setDpadDirections(boolean left, boolean up, boolean right, boolean down) {
        if (dpad.left != left) {
            dpad.left = left;
            if (listener != null) {
                listener.onTouchButton(AndroidInputMapper.BTN_DPAD_LEFT, left);
            }
        }
        if (dpad.up != up) {
            dpad.up = up;
            if (listener != null) {
                listener.onTouchButton(AndroidInputMapper.BTN_DPAD_UP, up);
            }
        }
        if (dpad.right != right) {
            dpad.right = right;
            if (listener != null) {
                listener.onTouchButton(AndroidInputMapper.BTN_DPAD_RIGHT, right);
            }
        }
        if (dpad.down != down) {
            dpad.down = down;
            if (listener != null) {
                listener.onTouchButton(AndroidInputMapper.BTN_DPAD_DOWN, down);
            }
        }
    }

    private void releaseStick(Stick s) {
        if (s.activePointerId == MotionEvent.INVALID_POINTER_ID) {
            return;
        }
        s.activePointerId = MotionEvent.INVALID_POINTER_ID;
        s.offsetX = 0;
        s.offsetY = 0;
        if (listener != null) {
            listener.onTouchAxis(s.axisXId, 0f);
            listener.onTouchAxis(s.axisYId, 0f);
        }
    }

    private void setButtonPressed(Btn b, boolean down) {
        if (b.pressed == down) {
            return;
        }
        b.pressed = down;
        if (listener == null) {
            return;
        }
        switch (b.kind) {
            case KIND_AXIS_DIGITAL:
                listener.onTouchAxis(b.id, down ? 1f : 0f);
                break;
            case KIND_PAUSE:
                if (down) {
                    listener.onTouchPause();
                }
                break;
            case KIND_BUTTON:
            default:
                listener.onTouchButton(b.id, down);
                break;
        }
    }

    // ---- Touch: edit mode ----

    private boolean handleEditTouch(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float x = event.getX();
                float y = event.getY();
                pendingKey = findEditableAt(x, y);
                longPressFired = false;
                movedBeyondSlop = false;
                dragging = false;
                dragPointerId = event.getPointerId(0);
                dragStartX = x;
                dragStartY = y;
                if (pendingKey != null) {
                    postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pendingKey == null) {
                    break;
                }
                int idx = event.findPointerIndex(dragPointerId);
                if (idx < 0) {
                    break;
                }
                float x = event.getX(idx);
                float y = event.getY(idx);
                float dx = x - dragStartX;
                float dy = y - dragStartY;
                if (!movedBeyondSlop && Math.hypot(dx, dy) > dp(TOUCH_SLOP_DP)) {
                    movedBeyondSlop = true;
                    removeCallbacks(longPressRunnable);
                    if (!selected.contains(pendingKey)) {
                        selected.clear();
                        selected.add(pendingKey);
                        notifySelectionChanged();
                    }
                    dragging = true;
                    captureDragStartOffsets();
                }
                if (dragging) {
                    applyDragDelta(dx, dy);
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longPressRunnable);
                if (dragging) {
                    TouchLayoutStore.save(getContext(), overrides);
                    dragging = false;
                } else if (!longPressFired) {
                    selected.clear();
                    if (pendingKey != null) {
                        selected.add(pendingKey);
                    }
                    notifySelectionChanged();
                }
                pendingKey = null;
                invalidate();
                break;
            }
            default:
                break;
        }
        return true;
    }

    private void onLongPress() {
        if (pendingKey == null || movedBeyondSlop) {
            return;
        }
        longPressFired = true;
        if (selected.contains(pendingKey)) {
            selected.remove(pendingKey);
        } else {
            selected.add(pendingKey);
        }
        notifySelectionChanged();
        invalidate();
    }

    private String findEditableAt(float x, float y) {
        String found = null;
        for (Map.Entry<String, Editable> entry : editables.entrySet()) {
            Editable e = entry.getValue();
            if (Math.abs(x - e.cx) <= e.halfW && Math.abs(y - e.cy) <= e.halfH) {
                found = entry.getKey();
            }
        }
        return found;
    }

    private void captureDragStartOffsets() {
        dragStartOffsets.clear();
        for (String key : selected) {
            TouchControlOverride ov = getOrCreateOverride(key);
            dragStartOffsets.put(key, new float[]{ov.dx, ov.dy});
        }
    }

    private void applyDragDelta(float dxPx, float dyPx) {
        float dxDp = dxPx / density;
        float dyDp = dyPx / density;
        for (String key : selected) {
            float[] base = dragStartOffsets.get(key);
            if (base == null) {
                continue;
            }
            TouchControlOverride ov = getOrCreateOverride(key);
            ov.dx = base[0] + dxDp;
            ov.dy = base[1] + dyDp;
        }
        layoutControls(getWidth(), getHeight());
    }

    private void notifySelectionChanged() {
        if (editListener != null) {
            editListener.onSelectionChanged();
        }
    }
}
