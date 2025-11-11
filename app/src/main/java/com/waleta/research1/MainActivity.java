package com.waleta.research1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button openBtn = findViewById(R.id.openCameraBtn);
        openBtn.setOnClickListener(v ->
                startActivity(new Intent(this, CameraPreviewActivity.class))
        );
    }
}
