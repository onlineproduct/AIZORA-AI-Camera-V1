package com.aizora.aicamera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends ComponentActivity {
    private static final String DEFAULT_RTSP = "rtsp://192.168.0.102:554/live/ch00_1";

    private PlayerView cctvPlayerView;
    private ExoPlayer player;
    private PreviewView phonePreview;
    private ImageCapture imageCapture;
    private EditText rtspInput;
    private TextView status;
    private LinearLayout header;
    private LinearLayout controls;
    private LinearLayout urlRow;
    private FrameLayout videoStage;
    private FrameLayout phoneWindow;
    private boolean fullscreen = false;
    private Button fullscreenButton;
    private int lens = CameraSelector.LENS_FACING_BACK;
    private float downRawX, downRawY;
    private int downX, downY;

    private final ActivityResultLauncher<String> cameraPermission =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startPhoneCamera();
            else toast("Phone camera permission is needed for the small preview.");
        });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startPhoneCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1020);

        header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(12), dp(12));
        TextView title = new TextView(this);
        title.setText("AIZORA AI Camera");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        fullscreenButton = button("Fullscreen");
        Button full = fullscreenButton;
        header.addView(full);
        root.addView(header);

        videoStage = new FrameLayout(this);
        videoStage.setBackgroundColor(0xFF05070C);
        cctvPlayerView = new PlayerView(this);
        cctvPlayerView.setUseController(false);
        cctvPlayerView.setKeepContentOnPlayerReset(true);
        videoStage.addView(cctvPlayerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        phoneWindow = new FrameLayout(this);
        phoneWindow.setBackgroundColor(0xFF182235);
        phoneWindow.setElevation(dp(12));
        phoneWindow.setClipToOutline(true);
        phonePreview = new PreviewView(this);
        phonePreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        phoneWindow.addView(phonePreview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView phoneTag = new TextView(this);
        phoneTag.setText("PHONE");
        phoneTag.setTextColor(0xFFFFFFFF);
        phoneTag.setTextSize(10);
        phoneTag.setTypeface(null, android.graphics.Typeface.BOLD);
        phoneTag.setPadding(dp(6), dp(3), dp(6), dp(3));
        phoneTag.setBackgroundColor(0xCC087F5B);
        FrameLayout.LayoutParams tagLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        phoneWindow.addView(phoneTag, tagLp);

        int pipWidth = dp(132);
        int pipHeight = dp(188);
        FrameLayout.LayoutParams pipLp = new FrameLayout.LayoutParams(pipWidth, pipHeight, Gravity.TOP | Gravity.END);
        pipLp.setMargins(0, dp(14), dp(14), 0);
        videoStage.addView(phoneWindow, pipLp);
        phoneWindow.setOnTouchListener((v, event) -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) phoneWindow.getLayoutParams();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = phoneWindow.getLeft();
                downY = phoneWindow.getTop();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int dx = (int) (event.getRawX() - downRawX);
                int dy = (int) (event.getRawY() - downRawY);
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                lp.leftMargin = Math.max(0, Math.min(videoStage.getWidth() - phoneWindow.getWidth(), downX + dx));
                lp.topMargin = Math.max(0, Math.min(videoStage.getHeight() - phoneWindow.getHeight(), downY + dy));
                phoneWindow.setLayoutParams(lp);
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_UP;
        });

        root.addView(videoStage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        status = new TextView(this);
        status.setText("CCTV disconnected • Drag PHONE window to move");
        status.setTextColor(0xFFB7C5D8);
        status.setTextSize(12);
        status.setPadding(dp(12), dp(7), dp(12), dp(7));
        root.addView(status);

        urlRow = new LinearLayout(this);
        urlRow.setPadding(dp(8), dp(4), dp(8), dp(4));
        rtspInput = new EditText(this);
        rtspInput.setSingleLine(true);
        rtspInput.setText(DEFAULT_RTSP);
        rtspInput.setTextColor(0xFFFFFFFF);
        rtspInput.setHintTextColor(0xFF9AA7B8);
        rtspInput.setHint("RTSP camera address");
        rtspInput.setTextSize(12);
        urlRow.addView(rtspInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button connect = button("Connect CCTV");
        urlRow.addView(connect);
        root.addView(urlRow);

        controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(6), dp(4), dp(6), dp(10));
        Button stop = button("Stop");
        Button snapshot = button("Phone Snapshot");
        Button flip = button("Flip Phone");
        controls.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(snapshot, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(flip, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(controls);

        connect.setOnClickListener(v -> connectCctv());
        stop.setOnClickListener(v -> stopCctv());
        snapshot.setOnClickListener(v -> takePhoneSnapshot());
        flip.setOnClickListener(v -> {
            lens = lens == CameraSelector.LENS_FACING_BACK
                    ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            startPhoneCamera();
        });
        full.setOnClickListener(v -> toggleFullscreen(full));
        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        return b;
    }

    private void connectCctv() {
        String url = rtspInput.getText().toString().trim();
        if (!url.toLowerCase(Locale.ROOT).startsWith("rtsp://")) {
            toast("Please enter an RTSP address beginning with rtsp://");
            return;
        }
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            player = new ExoPlayer.Builder(this).build();
            cctvPlayerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_BUFFERING) status.setText("Connecting to CCTV…");
                    else if (playbackState == Player.STATE_READY) status.setText("CCTV connected • Drag PHONE window to move");
                    else if (playbackState == Player.STATE_ENDED) status.setText("CCTV stream ended");
                }
                @Override public void onPlayerError(PlaybackException error) {
                    status.setText("CCTV connection failed • Check Wi-Fi and RTSP address");
                    toast("CCTV error: " + error.errorCodeName);
                }
            });
            MediaItem item = MediaItem.fromUri(url);
            player.setMediaSource(new RtspMediaSource.Factory().createMediaSource(item));
            player.prepare();
            player.play();
        } catch (Exception e) {
            status.setText("Unable to start CCTV stream");
            toast("Could not start CCTV: " + e.getMessage());
        }
    }

    private void stopCctv() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        status.setText("CCTV stopped • Phone preview remains active");
    }

    private void startPhoneCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(phonePreview.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                provider.unbindAll();
                provider.bindToLifecycle(this,
                        new CameraSelector.Builder().requireLensFacing(lens).build(),
                        preview, imageCapture);
            } catch (Exception e) {
                toast("Phone camera unavailable: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoneSnapshot() {
        if (imageCapture == null) {
            toast("Phone camera is not ready");
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "AIZORA_PHONE_" + stamp + ".jpg");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AIZORA");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override public void onImageSaved(ImageCapture.OutputFileResults output) {
                        toast("Phone photo saved to gallery");
                    }
                    @Override public void onError(ImageCaptureException error) {
                        toast("Snapshot failed: " + error.getMessage());
                    }
                });
    }

    private void toggleFullscreen(Button full) {
        fullscreen = !fullscreen;
        header.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        urlRow.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        controls.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        status.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        full.setText(fullscreen ? "Exit Fullscreen" : "Fullscreen");
        getWindow().getDecorView().setSystemUiVisibility(fullscreen
                ? (View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onBackPressed() {
        if (fullscreen) toggleFullscreen(fullscreenButton);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (player != null) {
            cctvPlayerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
