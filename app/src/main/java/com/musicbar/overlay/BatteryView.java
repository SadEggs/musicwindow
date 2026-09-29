package com.musicbar.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A small rectangular battery: outline, cap, and a fill that follows the charge. */
public class BatteryView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private int level = -1;
    private boolean charging;
    private int color = 0xFFFFFFFF;

    public BatteryView(Context ctx) {
        super(ctx);
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    /** 0..100, or -1 when the system does not report a level. */
    public void setLevel(int level, boolean charging) {
        if (this.level == level && this.charging == charging) {
            return;
        }
        this.level = level;
        this.charging = charging;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize((int) dp(21), widthSpec),
                resolveSize((int) dp(11), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cap = Math.max(1.5f, dp(2));
        float stroke = Math.max(1f, dp(1));
        float gap = stroke / 2f;

        body.set(gap, gap, w - cap - gap, h - gap);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(color);
        canvas.drawRoundRect(body, dp(2), dp(2), paint);

        // The little cap on the right, which is what makes it read as a battery.
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(w - cap, h * 0.32f, w, h * 0.68f, paint);

        if (level >= 0) {
            float inset = stroke + dp(1);
            float full = body.width() - inset * 2f;
            float filled = Math.max(0f, Math.min(full, full * level / 100f));
            if (filled > 0f) {
                paint.setColor(charging ? 0xFF8BC34A : color);
                canvas.drawRect(body.left + inset, body.top + inset,
                        body.left + inset + filled, body.bottom - inset, paint);
                paint.setColor(color);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
