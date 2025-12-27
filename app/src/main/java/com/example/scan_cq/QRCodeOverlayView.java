package com.example.scan_cq;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.barcode.Barcode;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.barcode.Barcode;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.barcode.Barcode;
import java.util.ArrayList;
import java.util.List;

public class QRCodeOverlayView extends View {
    private List<Barcode> barcodes = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private int imageWidth;
    private int imageHeight;

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
        boxPaint.setStrokeWidth(4f);

        textPaint = new Paint();
        textPaint.setColor(Color.GREEN);
        textPaint.setTextSize(40f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    public void setDetectedBarcodes(List<Barcode> detectedBarcodes, int width, int height) {
        this.barcodes = detectedBarcodes;
        this.imageWidth = width;
        this.imageHeight = height;
        invalidate();
    }

    public void clearBarcodes() {
        barcodes.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;

        for (Barcode barcode : barcodes) {
            Rect boundingBox = barcode.getBoundingBox();
            if (boundingBox != null) {
                float left = boundingBox.left * scaleX;
                float top = boundingBox.top * scaleY;
                float right = boundingBox.right * scaleX;
                float bottom = boundingBox.bottom * scaleY;

                // Draw bounding box
                canvas.drawRect(left, top, right, bottom, boxPaint);

                // Draw barcode value
                String barcodeValue = barcode.getRawValue();
                if (barcodeValue != null && !barcodeValue.isEmpty()) {
                    float textX = left + 10;
                    float textY = top - 20;

                    if (textY < 0) {
                        textY = bottom + 50;
                    }

                    canvas.drawText(barcodeValue.length() > 30 ?
                                    barcodeValue.substring(0, 30) + "..." : barcodeValue,
                            textX, textY, textPaint);
                }
            }
        }
    }
}