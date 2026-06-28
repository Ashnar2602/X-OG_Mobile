package emu.xbox.og;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TouchControllerView extends View {
    interface Listener {
        void onTouchButton(int button, boolean pressed);
        void onTouchAxis(int axis, float value);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private boolean a;
    private boolean b;
    private boolean x;
    private boolean y;

    public TouchControllerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(28f);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        drawButton(canvas, w - 180, h - 165, "A", a);
        drawButton(canvas, w - 110, h - 235, "B", b);
        drawButton(canvas, w - 250, h - 235, "X", x);
        drawButton(canvas, w - 180, h - 305, "Y", y);
        drawButton(canvas, 100, h - 210, "<", false);
        drawButton(canvas, 180, h - 290, "^", false);
        drawButton(canvas, 260, h - 210, ">", false);
        drawButton(canvas, 180, h - 130, "v", false);
    }

    private void drawButton(Canvas canvas, float cx, float cy, String label, boolean pressed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? 0xBB45D483 : 0x66333333);
        canvas.drawCircle(cx, cy, 38f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText(label, cx, cy + 10f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float xPos = event.getX();
        float yPos = event.getY();
        boolean down = event.getActionMasked() != MotionEvent.ACTION_UP &&
                event.getActionMasked() != MotionEvent.ACTION_CANCEL;
        int w = getWidth();
        int h = getHeight();
        boolean inButtons = xPos > w - 310 && yPos > h - 360;
        boolean inStick = xPos < 340 && yPos > h - 360;

        if (!inButtons && !inStick && down) {
            return false;
        }

        boolean nextA = down && near(xPos, yPos, w - 180, h - 165);
        boolean nextB = down && near(xPos, yPos, w - 110, h - 235);
        boolean nextX = down && near(xPos, yPos, w - 250, h - 235);
        boolean nextY = down && near(xPos, yPos, w - 180, h - 305);
        update(AndroidInputMapper.BTN_A, a, nextA);
        update(AndroidInputMapper.BTN_B, b, nextB);
        update(AndroidInputMapper.BTN_X, x, nextX);
        update(AndroidInputMapper.BTN_Y, y, nextY);
        a = nextA;
        b = nextB;
        x = nextX;
        y = nextY;

        if (down && inStick) {
            float dx = clamp((xPos - 180f) / 120f);
            float dy = clamp((yPos - (h - 210f)) / 120f);
            if (listener != null) {
                listener.onTouchAxis(2, dx);
                listener.onTouchAxis(3, dy);
            }
        } else if (listener != null) {
            listener.onTouchAxis(2, 0f);
            listener.onTouchAxis(3, 0f);
        }
        invalidate();
        return true;
    }

    private void update(int button, boolean oldValue, boolean newValue) {
        if (oldValue != newValue && listener != null) {
            listener.onTouchButton(button, newValue);
        }
    }

    private static boolean near(float x, float y, float cx, float cy) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy < 52f * 52f;
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }
}
