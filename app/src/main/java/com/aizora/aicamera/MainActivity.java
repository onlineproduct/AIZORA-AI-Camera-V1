package com.aizora.aicamera;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.rgb(15, 23, 42));

        TextView title = new TextView(this);
        title.setText("AIZORA AI CAMERA\nSTARTUP DIAGNOSTIC");
        title.setTextSize(23);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText("\nTEST MODE: VLC and camera features are disabled.\nIf this screen stays open, the basic Android app startup works.");
        status.setTextSize(16);
        status.setTextColor(Color.rgb(147, 197, 253));
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        Button button = new Button(this);
        button.setText("TEST BUTTON");
        button.setOnClickListener(v -> status.setText("BUTTON WORKS. Please report that the diagnostic screen stays open."));
        root.addView(button);
        setContentView(root);
    }
}
