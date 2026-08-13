package com.atlas.wake;

import android.Manifest;
import android.app.Activity;
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
            "OpenWakeWord engine installed.\n\n" +
            "Wake-word detection will be connected next."
        );
        status.setTextSize(20);
        status.setPadding(40, 100, 40, 40);

        setContentView(status);
    }
}
