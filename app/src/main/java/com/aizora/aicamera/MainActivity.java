package com.aizora.aicamera;

import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public class MainActivity extends ComponentActivity {
    private static final String DEFAULT_URL = "rtsp://192.168.0.102:554/live/ch00_1";
    private VLCVideoLayout videoLayout;
    private EditText urlInput;
    private TextView statusView;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private boolean fullScreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildInterface();
        initializePlayer();
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

        buttons.addView(connect, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(full, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("Ready — connect to a camera on the same network");
        statusView.setTextColor(Color.rgb(147, 197, 253));
        statusView.setTextSize(13);
        statusView.setPadding(0, 0, 0, dp(8));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        videoLayout = new VLCVideoLayout(this);
        videoLayout.setBackgroundColor(Color.BLACK);
        root.addView(videoLayout, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView note = new TextView(this);
        note.setText("RTSP viewer • Camera and phone should be on a reachable network");
        note.setTextColor(Color.LTGRAY);
        note.setTextSize(11);
        note.setPadding(0, dp(8), 0, 0);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        connect.setOnClickListener(v -> connectCamera());
        stop.setOnClickListener(v -> stopCamera());
        full.setOnClickListener(v -> toggleFullScreen());
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
                        setStatus("Opening camera stream...");
                        break;
                    case MediaPlayer.Event.Buffering:
                        setStatus("Buffering: " + (int) event.getBuffering() + "%");
                        break;
                    case MediaPlayer.Event.Playing:
                        setStatus("Video is playing");
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        setStatus("Stream error — check RTSP URL, Wi-Fi and camera access");
                        break;
                    case MediaPlayer.Event.Stopped:
                        setStatus("Stopped");
                        break;
                    default:
                        break;
                }
            }));
            mediaPlayer.attachViews(videoLayout, null, false, false);
        } catch (Exception e) {
            setStatus("Player initialization failed: " + safeMessage(e));
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
            setStatus("Connecting to camera...");
            mediaPlayer.play();
        } catch (Exception e) {
            setStatus("Could not start stream: " + safeMessage(e));
        }
    }

    private void stopCamera() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
        }
        setStatus("Stopped");
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onStop() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) { }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
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
