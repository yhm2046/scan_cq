package com.example.scan_cq;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.util.Log;
import android.util.Size; // 务必保留这行，防止报错
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

        // 核心修改 1：支持所有格式 (条形码 + 二维码)
        // FORMAT_ALL_FORMATS 会同时检测 EAN-13, Code 128, QR Code 等
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

        // 核心修改：请求 4K 分辨率 (3840x2160)
        // 如果手机硬件不支持 4K，CameraX 会自动降级到它支持的最高分辨率 (通常是 1080p 或 2K)
        // 这样可以确保获得最清晰的图像细节，专门应对密集小码
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(3840, 2160))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            processImageForQRCode(imageProxy);
        });

        try {
            cameraProvider.unbindAll();
            // 绑定生命周期，并获取 cameraControl 对象（用于控制对焦/变焦）
            Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            // 额外优化：确保开启自动对焦 (虽然默认通常开启，但这行代码强制激活)
            // 某些手机在微距下可能不对焦，这有助于让它“看清楚”
            CameraControl cameraControl = camera.getCameraControl();

            // 这里我们不需要手动设置对焦模式，因为 CameraX 默认就是 Continuous Auto Focus
            // 但如果需要，可以添加点击屏幕对焦的逻辑 (需要 PreviewView 的 TouchListener)

        } catch (Exception e) {
            Log.e(TAG, "Error binding camera use cases", e);
        }
    }

    private void processImageForQRCode(ImageProxy imageProxy) {
        @androidx.camera.core.ExperimentalGetImage
        android.media.Image mediaImage = imageProxy.getImage();

        if (mediaImage != null) {
            InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            // 获取图像信息用于 Overlay 映射
            boolean isRotated = imageProxy.getImageInfo().getRotationDegrees() == 90 ||
                    imageProxy.getImageInfo().getRotationDegrees() == 270;
            int analysisWidth = isRotated ? imageProxy.getHeight() : imageProxy.getWidth();
            int analysisHeight = isRotated ? imageProxy.getWidth() : imageProxy.getHeight();

            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            // 识别成功，传递所有类型的码给 View
                            overlayView.setDetectedBarcodes(barcodes, analysisWidth, analysisHeight);
                        } else {
                            overlayView.clearBarcodes();
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Barcode detection failed", e))
                    .addOnCompleteListener(task -> {
                        // 必须关闭 ImageProxy，否则画面会卡住
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