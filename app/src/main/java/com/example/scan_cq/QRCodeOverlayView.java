package com.example.scan_cq;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.barcode.Barcode;
import java.util.ArrayList;
import java.util.List;

public class QRCodeOverlayView extends View {
    private List<Barcode> barcodes = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private Paint textBackgroundPaint; // 新增：文字背景画笔

    // 分析图像的宽高（已考虑旋转）
    private int sourceWidth;
    private int sourceHeight;

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
        boxPaint.setStrokeWidth(5f); // 稍微加粗

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE); // 白字
        textPaint.setTextSize(35f); //稍微调小字体以适应密集显示
        textPaint.setAntiAlias(true);
        textPaint.setStyle(Paint.Style.FILL);

        textBackgroundPaint = new Paint();
        textBackgroundPaint.setColor(Color.parseColor("#80000000")); // 半透明黑色背景
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (barcodes.isEmpty() || sourceWidth == 0 || sourceHeight == 0) return;

        // 修改点 3：正确的坐标映射计算 (模拟 CenterCrop 模式)
        // PreviewView 默认是 FILL_CENTER (CenterCrop)，需要计算缩放比例和偏移
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
                // 转换坐标
                float left = boundingBox.left * scale + dx;
                float top = boundingBox.top * scale + dy;
                float right = boundingBox.right * scale + dx;
                float bottom = boundingBox.bottom * scale + dy;

                // 1. 画框
                canvas.drawRect(left, top, right, bottom, boxPaint);

                // 2. 画文字 (带背景，防止重叠看不清)
                String barcodeValue = barcode.getRawValue();
                if (barcodeValue != null) {
                    // 截断过长的文字
                    String displayText = barcodeValue.length() > 15 ?
                            barcodeValue.substring(0, 15) + "..." : barcodeValue;

                    // 计算文字宽高
                    Rect textBounds = new Rect();
                    textPaint.getTextBounds(displayText, 0, displayText.length(), textBounds);

                    float padding = 10f;
                    float textWidth = textBounds.width();
                    float textHeight = textBounds.height();

                    // 确定文字位置：尽量放在框的上方，如果上方没空间则放下方
                    float textX = left;
                    float textY = top - padding;

                    if (textY - textHeight < 0) {
                        textY = bottom + textHeight + padding; // 移到下方
                    }

                    // 画文字背景矩形
                    RectF bgRect = new RectF(textX - padding,
                            textY - textHeight - padding,
                            textX + textWidth + padding,
                            textY + padding);
                    canvas.drawRoundRect(bgRect, 8, 8, textBackgroundPaint);

                    // 画文字
                    canvas.drawText(displayText, textX, textY, textPaint);
                }
            }
        }
    }
}