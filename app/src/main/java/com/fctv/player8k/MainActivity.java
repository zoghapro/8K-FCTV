package com.fctv.player8k;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int GOLD = Color.rgb(212,175,55);
    private LinearLayout root, form, topBar;
    private EditText server, username, password, m3uUrl;
    private TextView status, title;
    private ListView list;
    private final List<Item> items = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private String xtreamBase, xtreamUser, xtreamPass;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        showLogin();
    }

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setPadding(16,12,16,12); return v;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(Color.GRAY); e.setTextColor(Color.WHITE); e.setSingleLine(true); e.setPadding(18,14,18,14); e.setBackgroundColor(Color.rgb(28,28,28));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,8,0,8); e.setLayoutParams(p); return e;
    }

    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextColor(Color.BLACK); b.setBackgroundColor(GOLD); b.setFocusable(true); return b;
    }

    private void base() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,24,28,24); root.setBackgroundColor(Color.rgb(8,8,8)); setContentView(root);
        title = text("8K FCTV", 28, GOLD); title.setGravity(Gravity.CENTER_HORIZONTAL); root.addView(title);
    }

    private void showLogin() {
        base();
        root.addView(text("Your IPTV Player",18,Color.LTGRAY));
        form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); root.addView(form,new LinearLayout.LayoutParams(-1,0,1));

        TextView xt = text("XTREAM CODES",17,GOLD); form.addView(xt);
        server=input("Server URL  http://example.com:8080"); username=input("Username"); password=input("Password"); password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(server); form.addView(username); form.addView(password);
        Button xLogin=button("LOGIN WITH XTREAM CODES"); form.addView(xLogin); xLogin.setOnClickListener(v->loginXtream());

        form.addView(text("OR",16,Color.GRAY));
        form.addView(text("M3U PLAYLIST",17,GOLD)); m3uUrl=input("M3U URL"); form.addView(m3uUrl);
        Button mLogin=button("LOAD M3U PLAYLIST"); form.addView(mLogin); mLogin.setOnClickListener(v->loadM3u());
        status=text("",14,Color.LTGRAY); form.addView(status);
    }

    private void loginXtream() {
        xtreamBase=normalize(server.getText().toString()); xtreamUser=username.getText().toString().trim(); xtreamPass=password.getText().toString().trim();
        if(xtreamBase.isEmpty()||xtreamUser.isEmpty()||xtreamPass.isEmpty()){ status.setText("Enter server, username and password."); return; }
        status.setText("Connecting…");
        new Thread(()->{
            try {
                String api=xtreamBase+"/player_api.php?username="+enc(xtreamUser)+"&password="+enc(xtreamPass);
                JSONObject o=new JSONObject(get(api)); JSONObject ui=o.optJSONObject("user_info");
                if(ui==null || !"Active".equalsIgnoreCase(ui.optString("status"))) throw new Exception("Account not active");
                runOnUiThread(this::showXtreamHome);
            } catch(Exception e){ runOnUiThread(()->status.setText("Login failed: "+e.getMessage())); }
        }).start();
    }

    private void showXtreamHome() {
        base();
        topBar=new LinearLayout(this); topBar.setOrientation(LinearLayout.HORIZONTAL); root.addView(topBar);
        Button live=button("LIVE TV"); Button movies=button("MOVIES"); Button series=button("SERIES"); Button logout=button("LOGOUT");
        topBar.addView(live,new LinearLayout.LayoutParams(0,-2,1)); topBar.addView(movies,new LinearLayout.LayoutParams(0,-2,1)); topBar.addView(series,new LinearLayout.LayoutParams(0,-2,1)); topBar.addView(logout,new LinearLayout.LayoutParams(0,-2,1));
        status=text("Choose a section",14,Color.LTGRAY); root.addView(status);
        list=new ListView(this); list.setBackgroundColor(Color.BLACK); list.setDividerHeight(1); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        live.setOnClickListener(v->loadXtream("get_live_streams","live")); movies.setOnClickListener(v->loadXtream("get_vod_streams","movie")); series.setOnClickListener(v->loadXtream("get_series","series")); logout.setOnClickListener(v->showLogin());
        loadXtream("get_live_streams","live");
    }

    private void loadXtream(String action,String type){
        status.setText("Loading…");
        new Thread(()->{
            try{
                String api=xtreamBase+"/player_api.php?username="+enc(xtreamUser)+"&password="+enc(xtreamPass)+"&action="+action;
                JSONArray a=new JSONArray(get(api)); List<Item> loaded=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject o=a.getJSONObject(i); String name=o.optString("name",o.optString("title","Untitled"));
                    if(type.equals("series")){ loaded.add(new Item(name,"",type,o.optInt("series_id",0),"")); }
                    else {
                        int id=o.optInt("stream_id",0); String ext=o.optString("container_extension", type.equals("movie")?"mp4":"ts");
                        String url=xtreamBase+(type.equals("live")?"/live/":"/movie/")+enc(xtreamUser)+"/"+enc(xtreamPass)+"/"+id+"."+ext;
                        loaded.add(new Item(name,url,type,id,o.optString("stream_icon",o.optString("cover",""))));
                    }
                }
                runOnUiThread(()->display(loaded,type.toUpperCase()));
            }catch(Exception e){ runOnUiThread(()->status.setText("Could not load: "+e.getMessage())); }
        }).start();
    }

    private void loadM3u(){
        String url=m3uUrl.getText().toString().trim(); if(url.isEmpty()){status.setText("Enter an M3U URL.");return;} status.setText("Loading playlist…");
        new Thread(()->{
            try{
                String data=get(url); String[] lines=data.replace("\r","").split("\n"); List<Item> loaded=new ArrayList<>(); String name=null,group="";
                for(String line:lines){ line=line.trim(); if(line.startsWith("#EXTINF")){ int c=line.lastIndexOf(','); name=c>=0?line.substring(c+1).trim():"Channel"; group=attr(line,"group-title"); }
                    else if(name!=null && !line.isEmpty() && !line.startsWith("#")){ loaded.add(new Item((group.isEmpty()?"":group+" • ")+name,line,"m3u",0,"")); name=null; }
                }
                runOnUiThread(()->{ base(); status=text("M3U PLAYLIST",15,Color.LTGRAY); root.addView(status); list=new ListView(this); root.addView(list,new LinearLayout.LayoutParams(-1,0,1)); display(loaded,"M3U"); });
            }catch(Exception e){ runOnUiThread(()->status.setText("Could not load M3U: "+e.getMessage())); }
        }).start();
    }

    private void display(List<Item> loaded,String label){
        items.clear(); items.addAll(loaded); List<String> names=new ArrayList<>(); for(Item i:items) names.add(i.name);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){ @Override public View getView(int p,View c,android.view.ViewGroup g){ TextView v=(TextView)super.getView(p,c,g); v.setTextColor(Color.WHITE); v.setTextSize(17); v.setPadding(20,18,20,18); v.setBackgroundColor(Color.rgb(16,16,16)); return v; }};
        list.setAdapter(adapter); status.setText(label+" • "+items.size()+" items");
        list.setOnItemClickListener((a,v,p,id)->{ Item it=items.get(p); if(it.type.equals("series")){ Toast.makeText(this,"Series episode browser will be added in the next build.",Toast.LENGTH_LONG).show(); } else play(it.url,it.name); });
    }

    private void play(String url,String name){ Intent i=new Intent(this,PlayerActivity.class); i.putExtra("url",url); i.putExtra("name",name); startActivity(i); }
    private String get(String u)throws Exception{ HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent","8K-FCTV/1.0"); BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream())); StringBuilder b=new StringBuilder(); String l; while((l=r.readLine())!=null)b.append(l).append('\n'); r.close(); return b.toString(); }
    private String normalize(String s){ s=s.trim(); while(s.endsWith("/"))s=s.substring(0,s.length()-1); return s; }
    private String enc(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private String attr(String l,String k){ String q=k+"=\""; int a=l.indexOf(q); if(a<0)return""; a+=q.length(); int b=l.indexOf('"',a); return b>a?l.substring(a,b):""; }
    static class Item{ String name,url,type,icon; int id; Item(String n,String u,String t,int i,String ic){name=n;url=u;type=t;id=i;icon=ic;} }
}
