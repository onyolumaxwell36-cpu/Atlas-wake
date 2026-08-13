package com.atlas.wake;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setText(
            "ATLAS Wake\n\n" +
            "Wake-word system is ready."
        );
        text.setTextSize(22);
        text.setPadding(40, 100, 40, 40);

        setContentView(text);
    }
}
