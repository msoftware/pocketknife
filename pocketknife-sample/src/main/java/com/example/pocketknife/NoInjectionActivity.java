package com.example.pocketknife;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import pocketknife.PocketKnife;

public class NoInjectionActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_activity);

        PocketKnife.injectExtras(this);
    }
}
