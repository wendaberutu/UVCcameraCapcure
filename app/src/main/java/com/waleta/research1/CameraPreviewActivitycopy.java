//package com.waleta.research1;
//
//import android.graphics.Bitmap;
//import android.graphics.SurfaceTexture;
//import android.hardware.usb.UsbDevice;
//import android.os.Bundle;
//import android.util.Log;
//import android.view.TextureView;
//import android.view.View;
//import android.widget.Button;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.serenegiant.usb.Size;
//import com.serenegiant.usb.USBMonitor;
//import com.serenegiant.usb.UVCCamera;
//import com.serenegiant.usb.UVCParam;
//import com.serenegiant.widget.AspectRatioTextureView;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class CameraPreviewActivity extends AppCompatActivity {
//    private static final String TAG = "CameraPreview";
//
//    private AspectRatioTextureView[] textureViews = new AspectRatioTextureView[3];
//    private List<UVCCamera> cameras = new ArrayList<>();
//    private List<UsbDevice> devices = new ArrayList<>();
//    private USBMonitor usbMonitor;
//    private Button captureBtn;
//
//    private int previewWidth = 640;
//    private int previewHeight = 480;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_camera_preview);
//
//        textureViews[0] = findViewById(R.id.textureCam1);
//        textureViews[1] = findViewById(R.id.textureCam2);
//        textureViews[2] = findViewById(R.id.textureCam3);
//        captureBtn = findViewById(R.id.captureBtn);
//
//        initUSBMonitor(); // tetap pakai versi aslimu
//        for (AspectRatioTextureView tv : textureViews) initPreviewView(tv);
//        captureBtn.setOnClickListener(v -> captureAll());
//    }
//
//    private void initUSBMonitor() {
//        usbMonitor = new USBMonitor(this, new USBMonitor.OnDeviceConnectListener() {
//            @Override
//            public void onAttach(UsbDevice device) {
//                Log.d(TAG, "USB device terdeteksi: " + device.getDeviceName());
//                if (!devices.contains(device)) {
//                    devices.add(device);
//                    usbMonitor.requestPermission(device);
//                }
//            }
//
//            @Override
//            public void onDeviceOpen(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
//                Log.d(TAG, "Kamera USB dibuka: " + device.getDeviceName());
//                openCamera(device, ctrlBlock);
//            }
//
//            @Override
//            public void onDeviceClose(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
//                Log.d(TAG, "Kamera ditutup: " + device.getDeviceName());
//                closeCamera(device);
//            }
//
//            @Override
//            public void onDetach(UsbDevice device) {
//                Log.d(TAG, "USB dilepas: " + device.getDeviceName());
//                closeCamera(device);
//            }
//
//            @Override public void onCancel(UsbDevice device) {}
//            @Override public void onError(UsbDevice device, USBMonitor.USBException e) {
//                Log.e(TAG, "Error kamera: " + e.getMessage());
//            }
//        });
//    }
//
//    private void initPreviewView(AspectRatioTextureView tv) {
//        tv.setAspectRatio(previewWidth, previewHeight);
//        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
//            @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {}
//            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
//            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return false; }
//            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
//        });
//    }
//
//    private void openCamera(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
//        try {
//            // pastikan setiap kamera punya slot sendiri (max 3)
//            int index = devices.indexOf(device);
//            if (index >= 3) {
//                Log.w(TAG, "Melebihi 3 kamera, abaikan: " + device.getDeviceName());
//                return;
//            }
//
//            UVCCamera cam = new UVCCamera(new UVCParam());
//            cam.open(ctrlBlock);
//
//            List<Size> sizes = cam.getSupportedSizeList();
//            if (sizes == null || sizes.isEmpty()) {
//                Log.e(TAG, "❌ Tidak ada resolusi didukung oleh " + device.getDeviceName());
//                return;
//            }
//
//            Size chosen = pickPreferredSize(sizes);
//            cam.setPreviewSize(chosen.width, chosen.height, chosen.type, chosen.fps);
//
//            previewWidth = chosen.width;
//            previewHeight = chosen.height;
//
//            AspectRatioTextureView tv = textureViews[index];
//            tv.setAspectRatio(previewWidth, previewHeight);
//
//            if (tv.isAvailable() && tv.getSurfaceTexture() != null) {
//                cam.setPreviewTexture(tv.getSurfaceTexture());
//                cam.startPreview();
//                Log.d(TAG, "✅ Kamera index " + index + " aktif (" + chosen.width + "x" + chosen.height + ")");
//            } else {
//                Log.d(TAG, "SurfaceTexture belum siap untuk kamera index " + index);
//            }
//
//            cameras.add(cam);
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Gagal membuka kamera: " + e.getMessage(), e);
//        }
//    }
//
//    private Size pickPreferredSize(List<Size> sizes) {
//        for (Size s : sizes)
//            if (s.width == 1280 && s.height == 720) return s;
//        return sizes.get(0);
//    }
//
//    private void captureAll() {
//        for (int i = 0; i < textureViews.length; i++) {
//            AspectRatioTextureView tv = textureViews[i];
//            if (tv != null && tv.isAvailable()) {
//                Bitmap bmp = tv.getBitmap();
//                if (bmp != null) {
//                    Log.d(TAG, "📸 Capture Cam" + (i + 1) + ": " + bmp.getWidth() + "x" + bmp.getHeight());
//                } else {
//                    Log.e(TAG, "❌ Capture Cam" + (i + 1) + " gagal (bitmap null)");
//                }
//            }
//        }
//    }
//
//    private void closeCamera(UsbDevice device) {
//        int idx = devices.indexOf(device);
//        if (idx >= 0 && idx < cameras.size()) {
//            try {
//                cameras.get(idx).stopPreview();
//                cameras.get(idx).destroy();
//            } catch (Exception ignored) {}
//            Log.d(TAG, "Kamera index " + idx + " dimatikan");
//            cameras.remove(idx);
//            devices.remove(idx);
//        }
//    }
//
//    @Override
//    protected void onStart() {
//        super.onStart();
//        if (usbMonitor != null) usbMonitor.register();
//    }
//
//    @Override
//    protected void onStop() {
//        if (usbMonitor != null) usbMonitor.unregister();
//        for (UVCCamera c : cameras) {
//            try { c.stopPreview(); c.destroy(); } catch (Exception ignored) {}
//        }
//        cameras.clear();
//        super.onStop();
//    }
//
//    @Override
//    protected void onDestroy() {
//        if (usbMonitor != null) {
//            usbMonitor.destroy();
//            usbMonitor = null;
//        }
//        super.onDestroy();
//    }
//}