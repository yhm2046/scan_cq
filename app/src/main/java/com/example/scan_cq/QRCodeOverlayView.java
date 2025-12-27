package com.example.scan_cq;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.google.mlkit.vision.barcode.Barcode;
import java.util.ArrayList;
import java.util.List;

public class QRCodeOverlayView extends View {
    private List<Barcode> barcodes = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private Paint textBackgroundPaint;
    private int sourceWidth;
    private int sourceHeight;

    // 新增：点击回调接口
    public interface OnBarcodeClickListener {
        void onBarcodeClick(Barcode barcode);
    }

    private OnBarcodeClickListener barcodeClickListener;

    public void setOnBarcodeClickListener(OnBarcodeClickListener listener) {
        this.barcodeClickListener = listener;
    }

    public QRCodeOverlayView(Context context) {
        super(context);
        init();
    }

    public QRCodeOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public QRCodeOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(35f);
        textPaint.setAntiAlias(true);
        textPaint.setStyle(Paint.Style.FILL);

        textBackgroundPaint = new Paint();
        textBackgroundPaint.setColor(Color.parseColor("#80000000"));
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setAntiAlias(true);
    }

    public void setDetectedBarcodes(List<Barcode> detectedBarcodes, int width, int height) {
        this.barcodes = detectedBarcodes;
        this.sourceWidth = width;
        this.sourceHeight = height;
        invalidate();
    }

    public void clearBarcodes() {
        barcodes.clear();
        invalidate();
    }

    /**
     * 新增：处理触摸事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) { // 响应手指抬起操作
            checkTouch(event.getX(), event.getY());
            return true; // 消费事件
        }
        return true; // 必须返回true才能接收后续事件
    }

    /**
     * 新增：检测点击位置是否在任何一个二维码框内
     */
    private void checkTouch(float x, float y) {
        if (barcodes.isEmpty() || sourceWidth == 0 || sourceHeight == 0 || barcodeClickListener == null) return;

        // 重新计算缩放比例和偏移（与 onDraw 逻辑保持完全一致）
        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float scale;
        float dx = 0, dy = 0;

        if (viewWidth / viewHeight > (float) sourceWidth / sourceHeight) {
            scale = viewWidth / sourceWidth;
            dy = (viewHeight - sourceHeight * scale) * 0.5f;
        } else {
            scale = viewHeight / sourceHeight;
            dx = (viewWidth - sourceWidth * scale) * 0.5f;
        }

        // 遍历当前显示的二维码，检查点击是否在范围内
        for (Barcode barcode : barcodes) {
            Rect boundingBox = barcode.getBoundingBox();
            if (boundingBox != null) {
                // 转换坐标到屏幕坐标系
                float left = boundingBox.left * scale + dx;
                float top = boundingBox.top * scale + dy;
                float right = boundingBox.right * scale + dx;
                float bottom = boundingBox.bottom * scale + dy;

                RectF touchArea = new RectF(left, top, right, bottom);

                // 增加一点点击容错范围 (例如 20像素)
                float tolerance = 20f;
                touchArea.inset(-tolerance, -tolerance);

                if (touchArea.contains(x, y)) {
                    // 触发点击回调
                    barcodeClickListener.onBarcodeClick(barcode);
                    return; // 一次只处理一个点击
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (barcodes.isEmpty() || sourceWidth == 0 || sourceHeight == 0) return;

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float scale;
        float dx = 0, dy = 0;

        // 计算填满屏幕所需的缩放比例
        if (viewWidth / viewHeight > (float) sourceWidth / sourceHeight) {
            scale = viewWidth / sourceWidth;
            dy = (viewHeight - sourceHeight * scale) * 0.5f;
        } else {
            scale = viewHeight / sourceHeight;
            dx = (viewWidth - sourceWidth * scale) * 0.5f;
        }

        for (Barcode barcode : barcodes) {
            Rect boundingBox = barcode.getBoundingBox();
            if (boundingBox != null) {
                float left = boundingBox.left * scale + dx;
                float top = boundingBox.top * scale + dy;
                float right = boundingBox.right * scale + dx;
                float bottom = boundingBox.bottom * scale + dy;

                // 1. 画框
                canvas.drawRect(left, top, right, bottom, boxPaint);

                // 2. 画文字
                String barcodeValue = barcode.getRawValue();
                if (barcodeValue != null) {
                    String displayText = barcodeValue.length() > 15 ?
                            barcodeValue.substring(0, 15) + "..." : barcodeValue;

                    Rect textBounds = new Rect();
                    textPaint.getTextBounds(displayText, 0, displayText.length(), textBounds);

                    float padding = 10f;
                    float textWidth = textBounds.width();
                    float textHeight = textBounds.height();

                    float textX = left;
                    float textY = top - padding;

                    if (textY - textHeight < 0) {
                        textY = bottom + textHeight + padding;
                    }

                    RectF bgRect = new RectF(textX - padding,
                            textY - textHeight - padding,
                            textX + textWidth + padding,
                            textY + padding);
                    canvas.drawRoundRect(bgRect, 8, 8, textBackgroundPaint);
                    canvas.drawText(displayText, textX, textY, textPaint);
                }
            }
        }
    }
}