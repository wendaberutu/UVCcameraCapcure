package com.waleta.research1;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.widget.AspectRatioTextureView;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class CameraPreviewActivity extends AppCompatActivity {

    private static final String TAG = "UVC_QUEUE";

    private AspectRatioTextureView[] textureViews = new AspectRatioTextureView[3];
    private ImageView[] resultViews = new ImageView[3];
    private Surface[] surfaces = new Surface[3];

    private USBMonitor usbMonitor;
    private UVCCamera activeCamera = null;
    private UsbDevice activeDevice = null;

    private Queue<UsbDevice> queue = new LinkedList<>();
    private boolean isOpening = false;

    private Button captureBtn;

    private int preferredW = 1024;
    private int preferredH = 768;

    private Bitmap[] imageResult = new Bitmap[3];

    private Map<String, Integer> portToSlot = new HashMap<>();

    private byte[][] lastFrameData = new byte[3][];
    private int[] frameW = new int[3];
    private int[] frameH = new int[3];
    private final Object[] frameLocks = new Object[]{new Object(), new Object(), new Object()};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        textureViews[0] = findViewById(R.id.textureCam1);
        textureViews[1] = findViewById(R.id.textureCam2);
        textureViews[2] = findViewById(R.id.textureCam3);

        resultViews[0] = findViewById(R.id.imgResult1);
        resultViews[1] = findViewById(R.id.imgResult2);
        resultViews[2] = findViewById(R.id.imgResult3);

        captureBtn = findViewById(R.id.captureBtn);
        captureBtn.setOnClickListener(v -> captureAndNext());

        for (int i = 0; i < 3; i++) {
            initPreviewListener(textureViews[i], i);
            resultViews[i].setVisibility(View.GONE);
        }

        initUSBMonitor();
    }

    private void initPreviewListener(AspectRatioTextureView tv, int slot) {
        tv.setAspectRatio(preferredW, preferredH);
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture s, int w, int h) {}
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return false; }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        });
    }

    private void initUSBMonitor() {
        usbMonitor = new USBMonitor(this, new USBMonitor.OnDeviceConnectListener() {
            @Override
            public void onAttach(UsbDevice device) {
                queue.add(device);
                tryProcessQueue();
            }

            @Override
            public void onDeviceOpen(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean newCreate) {
                openCameraReal(device, ctrlBlock);
            }

            @Override public void onDeviceClose(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {}
            @Override public void onDetach(UsbDevice device) {}
            @Override public void onCancel(UsbDevice device) {}
            @Override public void onError(UsbDevice device, USBMonitor.USBException e) {}
        });
    }

    private void tryProcessQueue() {
        if (isOpening) return;
        if (activeCamera != null) return;
        if (queue.isEmpty()) return;

        UsbDevice dev = queue.poll();
        activeDevice = dev;
        isOpening = true;

        usbMonitor.requestPermission(dev);
    }

    private String getDeviceKey(UsbDevice dev) {
        return dev.getDeviceName();
    }

    private int getSlotForDevice(UsbDevice dev) {
        String key = getDeviceKey(dev);
        if (!portToSlot.containsKey(key)) {
            int assigned = portToSlot.size();
            if (assigned > 2) assigned = 2;
            portToSlot.put(key, assigned);
        }
        return portToSlot.get(key);
    }

    private void openCameraReal(UsbDevice device, USBMonitor.UsbControlBlock ctrl) {
        try {
            int slot = getSlotForDevice(device);
            Log.w(TAG, "Camera matched to SLOT " + slot + " via USB PORT");

            if (imageResult[slot] != null) {
                isOpening = false;
                tryProcessQueue();
                return;
            }

            UVCCamera cam = new UVCCamera(new UVCParam());
            cam.open(ctrl);

            List<Size> sizes = cam.getSupportedSizeList();
            Size chosen = sizes.get(0);
            for (Size s : sizes) {
                if (s.width == preferredW && s.height == preferredH) chosen = s;
            }

            cam.setPreviewSize(chosen.width, chosen.height, chosen.type, chosen.fps);

            frameW[slot] = chosen.width;
            frameH[slot] = chosen.height;

            int finalSlot = slot;
            cam.setFrameCallback(frame -> {
                if (frame == null) return;
                byte[] buf = new byte[frame.remaining()];
                frame.get(buf);
                synchronized (frameLocks[finalSlot]) {
                    lastFrameData[finalSlot] = buf;
                }
            }, UVCCamera.PIXEL_FORMAT_NV21);

            startPreviewSafe(cam, textureViews[slot], slot);

            activeCamera = cam;

        } catch (Exception e) {
            Log.e(TAG, "openCamera ERROR: " + e.getMessage());
        } finally {
            isOpening = false;
        }
    }

    private void startPreviewSafe(UVCCamera cam, AspectRatioTextureView tv, int slot) {
        tv.postDelayed(() -> {
            try {
                SurfaceTexture tex = tv.getSurfaceTexture();
                if (tex == null) return;

                if (surfaces[slot] != null) surfaces[slot].release();

                surfaces[slot] = new Surface(tex);

                resultViews[slot].setVisibility(View.GONE);
                tv.setVisibility(View.VISIBLE);

                cam.setPreviewDisplay(surfaces[slot]);
                cam.startPreview();

            } catch (Exception e) {
                Log.e(TAG, "PREVIEW ERROR: " + e.getMessage());
            }
        }, 500);
    }

    private void captureAndNext() {
        if (activeCamera == null || activeDevice == null) return;

        int slot = getSlotForDevice(activeDevice);
        Bitmap bmp = createBitmapFromLastFrame(slot);

        if (bmp != null) {
            imageResult[slot] = bmp;
            resultViews[slot].setImageBitmap(bmp);
            resultViews[slot].setVisibility(View.VISIBLE);
            textureViews[slot].setVisibility(View.GONE);
        }

        try {
            activeCamera.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_NV21);
            activeCamera.stopPreview();
            activeCamera.setPreviewDisplay((Surface) null);
        } catch (Exception ignored) {}

        if (surfaces[slot] != null) {
            surfaces[slot].release();
            surfaces[slot] = null;
        }

        closeActiveCamera();
        tryProcessQueue();
    }

    private Bitmap createBitmapFromLastFrame(int slot) {
        byte[] data;
        int w;
        int h;

        synchronized (frameLocks[slot]) {
            data = lastFrameData[slot];
            w = frameW[slot];
            h = frameH[slot];
        }

        if (data == null || w <= 0 || h <= 0) return null;

        try {
            YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, w, h, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, w, h), 90, baos);
            byte[] jpeg = baos.toByteArray();
            return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        } catch (Exception e) {
            Log.e(TAG, "createBitmapFromLastFrame ERROR: " + e.getMessage());
            return null;
        }
    }

    private void closeActiveCamera() {
        if (activeCamera != null) {
            try {
                activeCamera.stopPreview();
            } catch (Exception ignored) {}
            try {
                activeCamera.destroy();
            } catch (Exception ignored) {}
        }
        activeCamera = null;
        activeDevice = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        usbMonitor.register();
    }

    @Override
    protected void onStop() {
        usbMonitor.unregister();
        closeActiveCamera();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        usbMonitor.destroy();
        for (int i = 0; i < 3; i++) {
            if (surfaces[i] != null) {
                surfaces[i].release();
                surfaces[i] = null;
            }
        }
        super.onDestroy();
    }
}
