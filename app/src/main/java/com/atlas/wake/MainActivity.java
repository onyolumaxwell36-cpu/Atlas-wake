package com.atlas.wake;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        status = new TextView(this);
        status.setText(
            "ATLAS Wake\n\n" +
            "Microphone permission is required.\n\n" +
            "Wake-word detection will start here."
        );
        status.setTextSize(20);
        status.setPadding(40, 100, 40, 40);

        setContentView(status);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                100
            );
        }
    }
}
