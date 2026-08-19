package com.fctv.player8k;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        PlayerView view=new PlayerView(this);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(view);
        String url=getIntent().getStringExtra("url");
        player=new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }
    @Override protected void onStop(){ super.onStop(); if(player!=null){ player.release(); player=null; } }
}
