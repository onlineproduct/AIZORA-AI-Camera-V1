package com.aizora.aicamera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
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
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recording;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends ComponentActivity {
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private Button recordButton;
    private int lens = CameraSelector.LENS_FACING_BACK;
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) startCamera();
            else toast("Camera permission is required");
        });

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101522);
        TextView title = new TextView(this);
        title.setText("AIZORA AI Camera  •  V1.0");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(22, 22, 12, 22);
        root.addView(title);
        previewView = new PreviewView(this);
        root.addView(previewView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(8, 18, 8, 25);
        Button photo = new Button(this); photo.setText("PHOTO");
        recordButton = new Button(this); recordButton.setText("RECORD");
        Button flip = new Button(this); flip.setText("FLIP");
        controls.addView(photo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        controls.addView(recordButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        controls.addView(flip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(controls);
        setContentView(root);
        photo.setOnClickListener(v -> takePhoto());
        recordButton.setOnClickListener(v -> toggleVideo());
        flip.setOnClickListener(v -> { if (recording != null) { toast("Stop recording first"); return; }
            lens = lens == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK; startCamera(); });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else permissions.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                videoCapture = VideoCapture.withOutput(new Recorder.Builder().build());
                provider.unbindAll();
                provider.bindToLifecycle(this, new CameraSelector.Builder().requireLensFacing(lens).build(), preview, imageCapture, videoCapture);
            } catch (Exception e) { toast("Camera unavailable: " + e.getMessage()); }
        }, ContextCompat.getMainExecutor(this));
    }

    private String timestamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private void takePhoto() {
        if (imageCapture == null) { toast("Camera not ready"); return; }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "AIZORA_" + timestamp() + ".jpg");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/AIZORA");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults output) { toast("Photo saved to gallery"); }
            @Override public void onError(ImageCaptureException e) { toast("Photo failed: " + e.getMessage()); }
        });
    }

    private void toggleVideo() {
        if (recording != null) { recording.stop(); return; }
        if (videoCapture == null) { toast("Camera not ready"); return; }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "AIZORA_" + timestamp() + ".mp4");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AIZORA");
        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build();
        PendingRecording pending = videoCapture.getOutput().prepareRecording(this, options);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled();
        recording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Start) recordButton.setText("STOP");
            if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize finish = (VideoRecordEvent.Finalize) event;
                toast(finish.hasError() ? "Video failed: " + finish.getError() : "Video saved to gallery");
                recording = null;
                recordButton.setText("RECORD");
            }
        });
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy() { if (recording != null) recording.stop(); super.onDestroy(); }
}
