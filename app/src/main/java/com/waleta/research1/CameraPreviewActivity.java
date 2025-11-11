package com.waleta.research1;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.widget.AspectRatioTextureView;

import java.util.List;

public class CameraPreviewActivity extends AppCompatActivity {
    private static final String TAG = "CameraPreview";

    private AspectRatioTextureView previewView;
    private ImageView capturedImage;
    private Button captureBtn;
    private TextView infoText;

    private USBMonitor usbMonitor;
    private UVCCamera camera;
    private UsbDevice currentDevice;

    private int previewWidth = 640;
    private int previewHeight = 480;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        previewView = findViewById(R.id.textureView);
        capturedImage = findViewById(R.id.capturedImage);
        captureBtn = findViewById(R.id.capture);
        infoText = findViewById(R.id.infoText);

        initUSBMonitor();
        initPreviewView();

        captureBtn.setOnClickListener(v -> captureImage());
    }

    private void captureImage() {
        if (camera == null || previewView == null) return;
        try {
            Bitmap bitmap = previewView.getBitmap();
            if (bitmap != null) {
                releaseCamera();
                previewView.setVisibility(View.GONE);
                capturedImage.setImageBitmap(bitmap);
                capturedImage.setVisibility(View.VISIBLE);
                infoText.setVisibility(View.GONE);
                captureBtn.setVisibility(View.GONE);
                Log.d(TAG, "✅ Gambar berhasil di-capture dan kamera dimatikan");
            } else {
                Log.e(TAG, "❌ Gagal mengambil frame (bitmap null)");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error saat capture: " + e.getMessage());
        }
    }

    private void initUSBMonitor() {
        usbMonitor = new USBMonitor(this, new USBMonitor.OnDeviceConnectListener() {
            @Override
            public void onAttach(UsbDevice device) {
                Log.d(TAG, "USB device terdeteksi: " + device.getDeviceName());
                if (usbMonitor != null) usbMonitor.requestPermission(device);
            }

            @Override
            public void onDeviceOpen(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
                Log.d(TAG, "Kamera USB dibuka");
                currentDevice = device;
                openCamera(ctrlBlock);
            }

            @Override
            public void onDeviceClose(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                Log.d(TAG, "Kamera ditutup: " + device.getDeviceName());
                releaseCamera();
            }

            @Override
            public void onDetach(UsbDevice device) {
                Log.d(TAG, "USB dilepas: " + device.getDeviceName());
                releaseCamera();
            }

            @Override public void onCancel(UsbDevice device) {}
            @Override public void onError(UsbDevice device, USBMonitor.USBException e) {
                Log.e(TAG, "Error kamera: " + e.getMessage());
            }
        });
    }

    private void initPreviewView() {
        previewView.setAspectRatio(previewWidth, previewHeight);
        previewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "SurfaceTexture siap: " + width + "x" + height);
                if (camera != null) {
                    camera.setPreviewTexture(surface);
                    camera.startPreview();
                }
            }

            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) {
                if (camera != null) {
                    try { camera.stopPreview(); } catch (Exception ignored) {}
                }
                return false;
            }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        });
    }

    private void openCamera(USBMonitor.UsbControlBlock ctrlBlock) {
        releaseCamera();
        runOnUiThread(() -> {
            try {
                camera = new UVCCamera(new UVCParam());
                camera.open(ctrlBlock);

                List<Size> sizes = camera.getSupportedSizeList();
                if (sizes == null || sizes.isEmpty()) {
                    Log.e(TAG, "❌ Tidak ada resolusi didukung");
                    return;
                }

                Size chosen = pickPreferredSize(sizes);
                if (chosen == null) chosen = sizes.get(0);

                camera.setPreviewSize(chosen.width, chosen.height, chosen.type, chosen.fps);

                previewWidth = chosen.width;
                previewHeight = chosen.height;

                previewView.setAspectRatio(previewWidth, previewHeight);
                if (previewView.isAvailable() && previewView.getSurfaceTexture() != null) {
                    camera.setPreviewTexture(previewView.getSurfaceTexture());
                    camera.startPreview();
                    previewView.setVisibility(View.VISIBLE);
                    Log.d(TAG, "✅ Preview dimulai di " + previewWidth + "x" + previewHeight);
                } else {
                    Log.d(TAG, "Menunggu SurfaceTexture siap...");
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Gagal membuka kamera: " + e.getMessage(), e);
            }
        });
    }

    private Size pickPreferredSize(List<Size> sizes) {
        for (Size s : sizes)
            if (s.type == 5 && s.width == 1280 && s.height == 720) return s;
        for (Size s : sizes)
            if (s.type == 7 && s.width == 1280 && s.height == 720) return s;
        return sizes.get(0);
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.destroy();
            } catch (Exception ignored) {}
            camera = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (usbMonitor != null) usbMonitor.register();
    }

    @Override
    protected void onStop() {
        if (usbMonitor != null) usbMonitor.unregister();
        releaseCamera();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        if (usbMonitor != null) {
            usbMonitor.destroy();
            usbMonitor = null;
        }
        super.onDestroy();
    }

    private static class Mode {
        final int width;
        final int height;
        final int format;
        final float fps;

        Mode(int w, int h, int fmt, float f) {
            width = w;
            height = h;
            format = fmt;
            fps = f;
        }
    }
}
