package com.ispiral.verid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    Button livenessTestingActivityBTN;
    Button documentVerificationActivityBTN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        livenessTestingActivityBTN = findViewById(R.id.livenessTestingActivityBTN);
        documentVerificationActivityBTN = findViewById(R.id.documentVerificationActivityBTN);

        livenessTestingActivityBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, LivenessTesting.class);
                MainActivity.this.startActivity(myIntent);
            }
        });

        documentVerificationActivityBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this, DocumentVerification.class);
                MainActivity.this.startActivity(myIntent);
            }
        });
    }
}
