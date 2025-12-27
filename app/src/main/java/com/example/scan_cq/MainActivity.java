package com.example.scan_cq;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Size; // 务必保留这行，防止报错
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
//import com.google.mlkit.vision.barcode.common.Barcode; // 注意：新版ML Kit Barcode常量位置可能变化，如报错请改回 com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Date: 25.12.27 Saturday, 14:30-16:31 识别不完整且支付重叠，log有报错
 * 18：27-18:58,about 2.5h with AI
 */


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "QRScanner";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private PreviewView previewView;
    private QRCodeOverlayView overlayView;
    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.preview_view);
        overlayView = findViewById(R.id.overlay_view);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // 设置点击监听
        overlayView.setOnBarcodeClickListener(barcode -> {
            String rawValue = barcode.getRawValue();
            if (rawValue != null) {
                handleBarcodeClick(rawValue, barcode.getValueType());
            }
        });

        // 核心修改 1：支持所有格式 (条形码 + 二维码)
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 处理点击事件：如果是URL则跳转，否则复制或弹窗提示
     */
    private void handleBarcodeClick(String rawValue, int valueType) {
        // 方法1：简单判断是否以 http 开头
        if (valueType == Barcode.TYPE_URL || rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(rawValue));
                startActivity(intent);
                Toast.makeText(this, "正在打开链接...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "无法打开该链接", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error opening URL", e);
            }
        } else {
            // 如果不是链接，可以选择复制到剪贴板
            Toast.makeText(this, "内容: " + rawValue, Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                cameraProvider = ProcessCameraProvider.getInstance(this).get();
                bindCameraUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Error getting camera provider", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 请求 4K 分辨率
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(3840, 2160))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            processImageForQRCode(imageProxy);
        });

        try {
            cameraProvider.unbindAll();
            Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            CameraControl cameraControl = camera.getCameraControl();
        } catch (Exception e) {
            Log.e(TAG, "Error binding camera use cases", e);
        }
    }

    private void processImageForQRCode(ImageProxy imageProxy) {
        @androidx.camera.core.ExperimentalGetImage
        android.media.Image mediaImage = imageProxy.getImage();

        if (mediaImage != null) {
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            boolean isRotated = imageProxy.getImageInfo().getRotationDegrees() == 90 ||
                    imageProxy.getImageInfo().getRotationDegrees() == 270;
            int analysisWidth = isRotated ? imageProxy.getHeight() : imageProxy.getWidth();
            int analysisHeight = isRotated ? imageProxy.getWidth() : imageProxy.getHeight();

            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        // 修正：切换到主线程更新 UI，防止并发问题或 UI 不刷新
                        runOnUiThread(() -> {
                            if (!barcodes.isEmpty()) {
                                overlayView.setDetectedBarcodes(barcodes, analysisWidth, analysisHeight);
                            } else {
                                overlayView.clearBarcodes();
                            }
                        });
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Barcode detection failed", e))
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });
        } else {
            imageProxy.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Log.e(TAG, "Camera permission denied");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        barcodeScanner.close();
    }
}