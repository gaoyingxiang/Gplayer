package com.xyaokj.myplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;

import com.xyaokj.gvideolibrary.Gpalyer;

public class MainActivity extends AppCompatActivity {

    private Gpalyer mGPlayer;
    String videoUrl = "http://tanzi27niu.cdsb.mobi/wps/wp-content/uploads/2017/05/2017-05-17_17-33-30.mp4";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mGPlayer = findViewById(R.id.g_palyer);
        mGPlayer.setVideoDataSource(Uri.parse(videoUrl),"测试");
        mGPlayer.start();
    }
}
