package com.waleta.research1;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.widget.AspectRatioTextureView;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class CameraPreviewActivity extends AppCompatActivity {

    private static final String TAG = "CameraQueue";

    private AspectRatioTextureView[] textureViews = new AspectRatioTextureView[3];

    private USBMonitor usbMonitor;

    private UVCCamera activeCamera = null;
    private UsbDevice activeDevice = null;

    private Queue<UsbDevice> queue = new LinkedList<>();

    private boolean isOpening = false;

    private Button captureBtn;

    private int previewWidth = 320;
    private int previewHeight = 240;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        textureViews[0] = findViewById(R.id.textureCam1);
        textureViews[1] = findViewById(R.id.textureCam2);
        textureViews[2] = findViewById(R.id.textureCam3);

        captureBtn = findViewById(R.id.captureBtn);
        captureBtn.setOnClickListener(v -> captureAndNext());

        initUSBMonitor();

        for (AspectRatioTextureView tv : textureViews) initPreviewView(tv);
    }

    private void initPreviewView(AspectRatioTextureView tv) {
        tv.setAspectRatio(previewWidth, previewHeight);
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int w, int h) {}
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return false; }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        });
    }

    // ================================================================
    // USB MONITOR FIX — queue berjalan via permission → onDeviceOpen()
    // ================================================================
    private void initUSBMonitor() {

        usbMonitor = new USBMonitor(this, new USBMonitor.OnDeviceConnectListener() {

            @Override
            public void onAttach(UsbDevice device) {
                Log.d(TAG, "Detected → enqueue " + device.getDeviceName());
                queue.add(device);
                tryProcessQueue();
            }

            @Override
            public void onDeviceOpen(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean newCreate) {
                Log.d(TAG, "Permission granted → opening " + device.getDeviceName());
                openCameraReal(device, ctrlBlock);
            }

            @Override public void onDeviceClose(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {}
            @Override public void onDetach(UsbDevice device) {}
            @Override public void onCancel(UsbDevice device) {}
            @Override public void onError(UsbDevice device, USBMonitor.USBException e) {}
        });
    }

    // ================================================================
    // Queue handler — satu kamera sekaligus
    // ================================================================
    private void tryProcessQueue() {
        if (isOpening) return;
        if (activeCamera != null) return;
        if (queue.isEmpty()) return;

        UsbDevice dev = queue.poll();
        activeDevice = dev;
        isOpening = true;

        usbMonitor.requestPermission(dev);
    }

    // ================================================================
    // Open camera hanya melalui ctrlBlock (legal dari library UVC)
    // ================================================================
    private void openCameraReal(UsbDevice device, USBMonitor.UsbControlBlock ctrl) {
        try {
            int slot = UsbCameraMap.getIndexForDevice(this, device);
            if (slot == -1) {
                slot = getFreeSlot(device);
            }

            UVCCamera cam = new UVCCamera(new UVCParam());
            cam.open(ctrl);

            List<Size> sizes = cam.getSupportedSizeList();
            Size chosen = pickPreferred(sizes);

            cam.setPreviewSize(chosen.width, chosen.height, chosen.type, chosen.fps);

            AspectRatioTextureView tv = textureViews[slot];
            tv.setAspectRatio(chosen.width, chosen.height);
            cam.setPreviewTexture(tv.getSurfaceTexture());

            cam.startPreview();

            activeCamera = cam;

            Log.d(TAG, "Camera opened in slot " + slot);

        } catch (Exception e) {
            Log.e(TAG, "Open error: " + e.getMessage(), e);
        }

        isOpening = false;
    }

    private int getFreeSlot(UsbDevice dev) {
        for (int i = 0; i < 3; i++) {
            boolean used = false;
            for (UsbDevice d : queue) {
                if (UsbCameraMap.getIndexForDevice(this, d) == i) {
                    used = true; break;
                };
            }
            if (!used) {
                UsbCameraMap.saveIndexForDevice(this, dev, i);
                return i;
            }
        }
        return 0;
    }

    private Size pickPreferred(List<Size> sizes) {
        for (Size s : sizes)
            if (s.width == 320 && s.height == 240) return s;
        return sizes.get(0);
    }

    // ================================================================
    // Capture satu kamera → lanjut buka kamera berikutnya
    // ================================================================
    private void captureAndNext() {
        if (activeCamera == null) {
            Log.d(TAG, "No camera active.");
            return;
        }

        int slot = UsbCameraMap.getIndexForDevice(this, activeDevice);
        Bitmap bmp = textureViews[slot].getBitmap();

        if (bmp != null) {
            Log.d(TAG, "Captured slot " + slot + ": " + bmp.getWidth() + "x" + bmp.getHeight());
        }

        closeActiveCamera();
        tryProcessQueue();
    }

    private void closeActiveCamera() {
        if (activeCamera != null) {
            try {
                activeCamera.stopPreview();
                activeCamera.destroy();
            } catch (Exception ignored) {}
        }
        activeCamera = null;
        activeDevice = null;
    }

    // ================================================================
    @Override protected void onStart() {
        super.onStart();
        usbMonitor.register();
    }

    @Override protected void onStop() {
        usbMonitor.unregister();
        closeActiveCamera();
        super.onStop();
    }

    @Override protected void onDestroy() {
        usbMonitor.destroy();
        super.onDestroy();
    }
}
