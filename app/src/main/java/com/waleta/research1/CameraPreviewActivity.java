package com.waleta.research1;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
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

    private static final String TAG = "UVC_QUEUE";

    private AspectRatioTextureView[] textureViews = new AspectRatioTextureView[3];
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

    private int nextSlot = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_preview);

        textureViews[0] = findViewById(R.id.textureCam1);
        textureViews[1] = findViewById(R.id.textureCam2);
        textureViews[2] = findViewById(R.id.textureCam3);

        captureBtn = findViewById(R.id.captureBtn);
        captureBtn.setOnClickListener(v -> captureAndNext());

        for (int i = 0; i < 3; i++) initPreviewListener(textureViews[i], i);

        initUSBMonitor();
    }

    private void initPreviewListener(AspectRatioTextureView tv, int slot) {
        tv.setAspectRatio(preferredW, preferredH);

        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int w, int h) {
                Log.w(TAG, "SurfaceTexture AVAILABLE slot " + slot);
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return false; }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        });
    }

    // USB MONITOR
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
            @Override public void onError(UsbDevice device, USBMonitor.USBException e) {
                Log.e(TAG, "USB ERROR: " + e.getMessage());
            }
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

    private void openCameraReal(UsbDevice device, USBMonitor.UsbControlBlock ctrl) {
        try {
            int slot = nextSlot;

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
                if (s.width == preferredW && s.height == preferredH) {
                    chosen = s;
                    break;
                }
            }

            cam.setPreviewSize(chosen.width, chosen.height, chosen.type, chosen.fps);
            startPreviewSafe(cam, textureViews[slot], slot);

            activeCamera = cam;

        } catch (Exception e) {
            Log.e(TAG, "openCamera ERROR: " + e.getMessage());
        }

        isOpening = false;
    }

    private void startPreviewSafe(UVCCamera cam, AspectRatioTextureView tv, int slot) {
        tv.postDelayed(() -> {
            try {
                SurfaceTexture tex = tv.getSurfaceTexture();
                if (tex == null) {
                    startPreviewSafe(cam, tv, slot);
                    return;
                }

                if (surfaces[slot] != null)
                    surfaces[slot].release();

                surfaces[slot] = new Surface(tex);

                cam.setPreviewDisplay((Surface) surfaces[slot]);
                cam.startPreview();

            } catch (Exception e) {
                Log.e(TAG, "PREVIEW ERROR: " + e.getMessage());
            }
        }, 300);
    }

    private void captureAndNext() {
        if (activeCamera == null) return;

        int slot = nextSlot;

        // Ambil bitmap dari preview camera
        Bitmap bmp = textureViews[slot].getBitmap();

        if (bmp != null) {
            imageResult[slot] = bmp;

            // 1. Stop kamera & lepas Surface kamera
            try {
                activeCamera.stopPreview();
                activeCamera.setPreviewDisplay((Surface) null);
            } catch (Exception ignored) {}

            // 2. Lepas Surface lama agar tidak menimpa screenshot
            if (surfaces[slot] != null) {
                surfaces[slot].release();
                surfaces[slot] = null;
            }

            // 3. Ganti SurfaceTexture untuk TextureView → WAJIB
            SurfaceTexture newTex = new SurfaceTexture(0);
            textureViews[slot].setSurfaceTexture(newTex);

            // 4. Gambar screenshot ke TextureView (tidak akan hitam)
            showScreenshot(slot, bmp);
        }

        // Lanjut ke slot berikutnya
        nextSlot++;
        if (nextSlot >= 3) nextSlot = 0;

        // Tutup kamera dan lanjutkan antrian kamera selanjutnya
        closeActiveCamera();
        tryProcessQueue();
    }


    private void showScreenshot(int slot, Bitmap bmp) {
        AspectRatioTextureView tv = textureViews[slot];

        // 1. Replace SurfaceTexture
        SurfaceTexture newTex = new SurfaceTexture(0);
        tv.setSurfaceTexture(newTex);

        // 2. Draw bitmap aman (tdk ditimpa preview)
        tv.postDelayed(() -> {
            try {
                Canvas canvas = tv.lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bmp, 0, 0, null);
                    tv.unlockCanvasAndPost(canvas);
                }
            } catch (Exception e) {
                Log.e(TAG, "DRAW ERROR: " + e.getMessage());
            }
        }, 50);
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

    @Override protected void onStart() { super.onStart(); usbMonitor.register(); }
    @Override protected void onStop() { usbMonitor.unregister(); closeActiveCamera(); super.onStop(); }
    @Override protected void onDestroy() {
        usbMonitor.destroy();
        for (int i = 0; i < 3; i++)
            if (surfaces[i] != null) surfaces[i].release();
        super.onDestroy();
    }
}
