package com.aizora.aicamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.net.Uri;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends ComponentActivity {
    private static final String DEFAULT_URL = "rtsp://192.168.0.102:554/live/ch00_1";
    private static final int CAMERA_PERMISSION_REQUEST = 41;
    private VLCVideoLayout videoLayout;
    private TextureView phonePreview;
    private EditText urlInput;
    private TextView statusView;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private CameraDevice phoneCamera;
    private CameraCaptureSession phoneSession;
    private String phoneCameraId;
    private boolean fullScreen = false;
    private boolean phoneCameraEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildInterface();
        // Wait until the view hierarchy is attached before initializing native VLC.
        videoLayout.postDelayed(this::initializePlayer, 300);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackgroundColor(Color.rgb(15, 23, 42));

        TextView title = new TextView(this);
        title.setText("AIZORA AI CAMERA  V2.0");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(4), 0, dp(12));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setText(DEFAULT_URL);
        urlInput.setTextColor(Color.WHITE);
        urlInput.setHintTextColor(Color.LTGRAY);
        urlInput.setHint("rtsp://camera-address:554/path");
        urlInput.setTextSize(13);
        urlInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        urlInput.setBackgroundColor(Color.rgb(35, 45, 65));
        root.addView(urlInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        buttons.setPadding(0, dp(8), 0, dp(8));

        Button connect = new Button(this);
        connect.setText("CONNECT");
        Button stop = new Button(this);
        stop.setText("STOP");
        Button full = new Button(this);
        full.setText("FULLSCREEN");
        Button phone = new Button(this);
        phone.setText("PHONE CAM");

        buttons.addView(connect, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(full, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(phone, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("Ready — connect to CCTV; PHONE CAM adds a small selfie preview");
        statusView.setTextColor(Color.rgb(147, 197, 253));
        statusView.setTextSize(12);
        statusView.setPadding(0, 0, 0, dp(8));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout videoFrame = new FrameLayout(this);
        videoFrame.setBackgroundColor(Color.BLACK);
        videoLayout = new VLCVideoLayout(this);
        videoFrame.addView(videoLayout, new FrameLayout.LayoutParams(-1, -1));

        phonePreview = new TextureView(this);
        phonePreview.setVisibility(View.GONE);
        phonePreview.setBackgroundColor(Color.DKGRAY);
        FrameLayout.LayoutParams previewParams = new FrameLayout.LayoutParams(dp(132), dp(184), Gravity.TOP | Gravity.END);
        previewParams.setMargins(0, dp(10), dp(10), 0);
        videoFrame.addView(phonePreview, previewParams);
        root.addView(videoFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView note = new TextView(this);
        note.setText("CCTV is the main view • Phone camera appears in a small overlay");
        note.setTextColor(Color.LTGRAY);
        note.setTextSize(11);
        note.setPadding(0, dp(8), 0, 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        connect.setOnClickListener(v -> connectCamera());
        stop.setOnClickListener(v -> stopCamera());
        full.setOnClickListener(v -> toggleFullScreen());
        phone.setOnClickListener(v -> togglePhoneCamera());
    }

    private void initializePlayer() {
        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--rtsp-tcp");
            options.add("--network-caching=1500");
            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);
            mediaPlayer.setEventListener(event -> runOnUiThread(() -> {
                switch (event.type) {
                    case MediaPlayer.Event.Opening:
                        setStatus("Opening CCTV stream...");
                        break;
                    case MediaPlayer.Event.Buffering:
                        setStatus("CCTV buffering: " + (int) event.getBuffering() + "%");
                        break;
                    case MediaPlayer.Event.Playing:
                        setStatus(phoneCameraEnabled ? "CCTV playing • phone camera overlay enabled" : "CCTV video is playing");
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        setStatus("CCTV stream error — check RTSP URL, Wi-Fi and camera access");
                        break;
                    case MediaPlayer.Event.Stopped:
                        setStatus("CCTV stopped");
                        break;
                    default:
                        break;
                }
            }));
            mediaPlayer.attachViews(videoLayout, null, false, false);
            setStatus("Player ready. Press CONNECT to open the CCTV stream.");
        } catch (Throwable e) {
            // Prevent Java-level startup/linkage errors from closing the whole app.
            setStatus("VLC initialization failed: " + e.getClass().getSimpleName() + ": " + safeThrowableMessage(e));
        }
    }

    private void connectCamera() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty() || !(url.startsWith("rtsp://") || url.startsWith("rtsps://"))) {
            Toast.makeText(this, "Enter a valid RTSP URL", Toast.LENGTH_LONG).show();
            return;
        }
        if (mediaPlayer == null || libVLC == null) {
            setStatus("Video player could not initialize");
            return;
        }
        try {
            mediaPlayer.stop();
            Media media = new Media(libVLC, Uri.parse(url));
            media.setHWDecoderEnabled(false, false);
            media.addOption(":network-caching=1500");
            media.addOption(":rtsp-tcp");
            mediaPlayer.setMedia(media);
            media.release();
            setStatus("Connecting to CCTV...");
            mediaPlayer.play();
        } catch (Exception e) {
            setStatus("Could not start CCTV stream: " + safeMessage(e));
        }
    }

    private void togglePhoneCamera() {
        if (phoneCameraEnabled) {
            closePhoneCamera();
            phonePreview.setVisibility(View.GONE);
            phoneCameraEnabled = false;
            setStatus("Phone camera overlay off; CCTV can continue playing");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        phoneCameraEnabled = true;
        phonePreview.setVisibility(View.VISIBLE);
        if (phonePreview.isAvailable()) {
            openPhoneCamera();
        } else {
            phonePreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { openPhoneCamera(); }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { closePhoneCamera(); return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
            });
        }
        setStatus("Opening phone camera overlay...");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                togglePhoneCamera();
            } else {
                setStatus("Camera permission denied; CCTV view remains available");
                Toast.makeText(this, "Allow Camera permission to show the phone-camera overlay", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openPhoneCamera() {
        if (!phoneCameraEnabled || phonePreview == null || !phonePreview.isAvailable()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            phoneCameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    phoneCameraId = id;
                    break;
                }
                if (phoneCameraId == null) phoneCameraId = id;
            }
            if (phoneCameraId == null) {
                setStatus("No phone camera found");
                return;
            }
            manager.openCamera(phoneCameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    phoneCamera = camera;
                    createPhonePreviewSession();
                }
                @Override public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    if (phoneCamera == camera) phoneCamera = null;
                    setStatus("Phone camera disconnected");
                }
                @Override public void onError(CameraDevice camera, int error) {
                    camera.close();
                    if (phoneCamera == camera) phoneCamera = null;
                    setStatus("Phone camera error: " + error);
                }
            }, null);
        } catch (CameraAccessException | SecurityException e) {
            setStatus("Cannot open phone camera: " + safeMessage(e));
        }
    }

    private void createPhonePreviewSession() {
        try {
            SurfaceTexture texture = phonePreview.getSurfaceTexture();
            if (texture == null || phoneCamera == null) return;
            texture.setDefaultBufferSize(640, 480);
            Surface surface = new Surface(texture);
            CaptureRequest.Builder builder = phoneCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            phoneCamera.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (phoneCamera == null || !phoneCameraEnabled) {
                        session.close();
                        surface.release();
                        return;
                    }
                    phoneSession = session;
                    try {
                        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        session.setRepeatingRequest(builder.build(), null, null);
                        runOnUiThread(() -> setStatus("CCTV + phone-camera overlay running"));
                    } catch (CameraAccessException e) {
                        runOnUiThread(() -> setStatus("Phone preview failed: " + safeMessage(e)));
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    session.close();
                    surface.release();
                    runOnUiThread(() -> setStatus("Could not configure phone-camera preview"));
                }
            }, null);
        } catch (CameraAccessException | IllegalStateException e) {
            setStatus("Phone preview failed: " + safeMessage(e));
        }
    }

    private void closePhoneCamera() {
        if (phoneSession != null) {
            try { phoneSession.stopRepeating(); } catch (Exception ignored) { }
            try { phoneSession.close(); } catch (Exception ignored) { }
            phoneSession = null;
        }
        if (phoneCamera != null) {
            try { phoneCamera.close(); } catch (Exception ignored) { }
            phoneCamera = null;
        }
    }

    private void stopCamera() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
        }
        setStatus("CCTV stopped");
    }

    private void toggleFullScreen() {
        fullScreen = !fullScreen;
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (fullScreen) {
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void setStatus(String message) {
        if (statusView != null) statusView.setText(message);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private String safeThrowableMessage(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onStop() {
        closePhoneCamera();
        phoneCameraEnabled = false;
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        closePhoneCamera();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.detachViews();
                mediaPlayer.release();
            } catch (Exception ignored) { }
            mediaPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
        super.onDestroy();
    }
}
