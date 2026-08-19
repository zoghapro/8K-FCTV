package com.fctv.player8k;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int GOLD = Color.rgb(212,175,55);
    private LinearLayout root, form, topBar;
    private EditText server, username, password, m3uUrl;
    private TextView status, title;
    private ListView list;
    private final List<Item> items = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private String xtreamBase = "", xtreamUser = "", xtreamPass = "";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        showLogin();
    }

    private TextView text(String s, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setPadding(16,12,16,12);
        return v;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(Color.GRAY); e.setTextColor(Color.WHITE);
        e.setSingleLine(true); e.setPadding(18,14,18,14); e.setBackgroundColor(Color.rgb(28,28,28));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,8,0,8); e.setLayoutParams(p);
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(Color.BLACK); b.setBackgroundColor(GOLD); b.setFocusable(true);
        return b;
    }

    private void base() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,24,28,24);
        root.setBackgroundColor(Color.rgb(8,8,8)); setContentView(root);
        title = text("8K FCTV", 28, GOLD); title.setGravity(Gravity.CENTER_HORIZONTAL); root.addView(title);
    }

    private void showLogin() {
        base();
        root.addView(text("Your IPTV Player",18,Color.LTGRAY));
        ScrollView scroll = new ScrollView(this);
        form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(form); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        form.addView(text("XTREAM CODES",17,GOLD));
        server=input("Server URL  http://example.com:8080");
        username=input("Username");
        password=input("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(server); form.addView(username); form.addView(password);
        Button xLogin=button("LOGIN WITH XTREAM CODES"); form.addView(xLogin);
        xLogin.setOnClickListener(v->loginXtream());

        form.addView(text("OR",16,Color.GRAY));
        form.addView(text("M3U PLAYLIST",17,GOLD));
        m3uUrl=input("M3U URL  http://..."); form.addView(m3uUrl);
        Button mLogin=button("LOAD M3U PLAYLIST"); form.addView(mLogin);
        mLogin.setOnClickListener(v->loadM3u());
        status=text("",14,Color.LTGRAY); form.addView(status);
    }

    private void loginXtream() {
        String base;
        try { base=normalizeServer(server.getText().toString()); }
        catch(Throwable t){ status.setText("Invalid server address."); return; }
        String user=username.getText().toString().trim();
        String pass=password.getText().toString().trim();
        if(base.isEmpty()||user.isEmpty()||pass.isEmpty()){
            status.setText("Enter server, username and password."); return;
        }
        connectXtream(base,user,pass,false,null);
    }

    private void connectXtream(String base,String user,String pass,boolean fromM3u,String fallbackM3u) {
        xtreamBase=base; xtreamUser=user; xtreamPass=pass;
        status.setText(fromM3u?"Xtream-style M3U detected. Connecting…":"Connecting…");
        new Thread(()->{
            try {
                String api=xtreamBase+"/player_api.php?username="+enc(xtreamUser)+"&password="+enc(xtreamPass);
                String response=get(api);
                JSONObject o=new JSONObject(response);
                JSONObject ui=o.optJSONObject("user_info");
                if(ui==null) throw new Exception("This server did not return Xtream account data.");
                String auth=ui.optString("auth","");
                String st=ui.optString("status","");
                boolean ok="1".equals(auth) || "Active".equalsIgnoreCase(st);
                if(!ok) throw new Exception(st.isEmpty()?"Login rejected by server":"Account status: "+st);
                runOnUiThread(()->showXtreamHome(fromM3u?"M3U connected through Xtream API":"Connected"));
            } catch(Throwable e){
                if(fromM3u && fallbackM3u!=null){
                    runOnUiThread(()->status.setText("Xtream fallback failed. Trying direct M3U…"));
                    loadDirectM3u(fallbackM3u,"Xtream API: "+friendly(e));
                } else {
                    runOnUiThread(()->status.setText("Login failed: "+friendly(e)));
                }
            }
        }).start();
    }

    private void showXtreamHome(String message) {
        base();
        topBar=new LinearLayout(this); topBar.setOrientation(LinearLayout.HORIZONTAL); root.addView(topBar);
        Button live=button("LIVE TV"); Button movies=button("MOVIES"); Button series=button("SERIES"); Button logout=button("LOGOUT");
        topBar.addView(live,new LinearLayout.LayoutParams(0,-2,1));
        topBar.addView(movies,new LinearLayout.LayoutParams(0,-2,1));
        topBar.addView(series,new LinearLayout.LayoutParams(0,-2,1));
        topBar.addView(logout,new LinearLayout.LayoutParams(0,-2,1));
        status=text(message,14,Color.LTGRAY); root.addView(status);
        list=new ListView(this); list.setBackgroundColor(Color.BLACK); list.setDividerHeight(1);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        live.setOnClickListener(v->loadXtream("get_live_streams","live"));
        movies.setOnClickListener(v->loadXtream("get_vod_streams","movie"));
        series.setOnClickListener(v->loadXtream("get_series","series"));
        logout.setOnClickListener(v->showLogin());
        loadXtream("get_live_streams","live");
    }

    private void loadXtream(String action,String type){
        if(status!=null) status.setText("Loading…");
        new Thread(()->{
            try{
                String api=xtreamBase+"/player_api.php?username="+enc(xtreamUser)+"&password="+enc(xtreamPass)+"&action="+action;
                String response=get(api).trim();
                if(response.startsWith("{")) {
                    JSONObject err=new JSONObject(response);
                    throw new Exception(err.optString("message","Server returned an unexpected response"));
                }
                JSONArray a=new JSONArray(response);
                List<Item> loaded=new ArrayList<>();
                for(int i=0;i<a.length();i++){
                    JSONObject o=a.optJSONObject(i); if(o==null) continue;
                    String name=o.optString("name",o.optString("title","Untitled"));
                    if(type.equals("series")){
                        int sid=o.optInt("series_id",0); if(sid<=0) continue;
                        loaded.add(new Item(name,"",type,sid,o.optString("cover","")));
                    } else {
                        int id=o.optInt("stream_id",0); if(id<=0) continue;
                        String ext=o.optString("container_extension", type.equals("movie")?"mp4":"ts");
                        if(ext.isEmpty()) ext=type.equals("movie")?"mp4":"ts";
                        String direct=o.optString("direct_source","").trim();
                        String url=!direct.isEmpty()?direct:xtreamBase+(type.equals("live")?"/live/":"/movie/")+path(xtreamUser)+"/"+path(xtreamPass)+"/"+id+"."+ext;
                        loaded.add(new Item(name,url,type,id,o.optString("stream_icon",o.optString("cover",""))));
                    }
                }
                runOnUiThread(()->display(loaded,type.equals("live")?"LIVE TV":type.equals("movie")?"MOVIES":"SERIES"));
            }catch(Throwable e){
                runOnUiThread(()->{ if(status!=null) status.setText("Could not load: "+friendly(e)); });
            }
        }).start();
    }

    private void loadSeriesEpisodes(Item series){
        status.setText("Loading episodes…");
        new Thread(()->{
            try{
                String api=xtreamBase+"/player_api.php?username="+enc(xtreamUser)+"&password="+enc(xtreamPass)+"&action=get_series_info&series_id="+series.id;
                JSONObject obj=new JSONObject(get(api));
                Object episodesObj=obj.opt("episodes");
                List<Item> loaded=new ArrayList<>();
                if(episodesObj instanceof JSONObject){
                    JSONObject seasons=(JSONObject)episodesObj;
                    Iterator<String> keys=seasons.keys();
                    while(keys.hasNext()){
                        String season=keys.next(); Object val=seasons.opt(season);
                        if(val instanceof JSONArray) addEpisodes((JSONArray)val,season,loaded);
                    }
                } else if(episodesObj instanceof JSONArray){
                    addEpisodes((JSONArray)episodesObj,"",loaded);
                }
                if(loaded.isEmpty()) throw new Exception("No episodes returned by server");
                runOnUiThread(()->display(loaded,series.name+" • EPISODES"));
            }catch(Throwable e){
                runOnUiThread(()->status.setText("Could not load episodes: "+friendly(e)));
            }
        }).start();
    }

    private void addEpisodes(JSONArray arr,String season,List<Item> out){
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i); if(o==null) continue;
            int id=o.optInt("id",o.optInt("stream_id",0)); if(id<=0) continue;
            String ext=o.optString("container_extension","mp4"); if(ext.isEmpty()) ext="mp4";
            String ep=o.optString("episode_num","");
            String title=o.optString("title",o.optString("name","Episode "+(i+1)));
            String prefix=season.isEmpty()?"":"S"+season+(ep.isEmpty()?"":" E"+ep)+" • ";
            String direct=o.optString("direct_source","").trim();
            String url=!direct.isEmpty()?direct:xtreamBase+"/series/"+path(xtreamUser)+"/"+path(xtreamPass)+"/"+id+"."+ext;
            out.add(new Item(prefix+title,url,"episode",id,""));
        }
    }

    private void loadM3u(){
        String raw=m3uUrl.getText().toString().trim();
        if(raw.isEmpty()){status.setText("Enter an M3U URL.");return;}
        final String playlistUrl=normalizeUrl(raw);

        XtreamLink link=parseXtreamM3u(playlistUrl);
        if(link!=null){
            connectXtream(link.base,link.user,link.pass,true,playlistUrl);
            return;
        }
        loadDirectM3u(playlistUrl,null);
    }

    private void loadDirectM3u(String playlistUrl,String previousError){
        runOnUiThread(()->{ if(status!=null) status.setText("Loading playlist…"); });
        new Thread(()->{
            try{
                String data=get(playlistUrl);
                if(data.trim().isEmpty()) throw new Exception("Playlist is empty");
                String[] lines=data.replace("\r","").split("\n");
                List<Item> loaded=new ArrayList<>(); String name=null,group="";
                URL baseUrl=new URL(playlistUrl);
                for(String original:lines){
                    String line=original.trim();
                    if(line.startsWith("\uFEFF")) line=line.substring(1);
                    if(line.startsWith("#EXTINF")){
                        int c=line.lastIndexOf(','); name=c>=0?line.substring(c+1).trim():"Channel";
                        if(name.isEmpty()) name="Channel";
                        group=attr(line,"group-title");
                    } else if(name!=null && !line.isEmpty() && !line.startsWith("#")){
                        String resolved=line;
                        try { resolved=new URL(baseUrl,line).toString(); } catch(Exception ignored) {}
                        loaded.add(new Item((group.isEmpty()?"":group+" • ")+name,resolved,"m3u",0,""));
                        name=null; group="";
                    }
                }
                if(loaded.isEmpty()) throw new Exception("No playable #EXTINF entries were found");
                runOnUiThread(()->{
                    base();
                    LinearLayout bar=new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); root.addView(bar);
                    Button back=button("BACK"); bar.addView(back); back.setOnClickListener(v->showLogin());
                    status=text("M3U PLAYLIST",15,Color.LTGRAY); root.addView(status);
                    list=new ListView(this); list.setBackgroundColor(Color.BLACK); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
                    display(loaded,"M3U");
                });
            }catch(Throwable e){
                final String msg=(previousError==null?"":previousError+" • ")+friendly(e);
                runOnUiThread(()->status.setText("Could not load M3U: "+msg));
            }
        }).start();
    }

    private XtreamLink parseXtreamM3u(String url){
        try{
            Uri u=Uri.parse(url);
            String p=u.getPath();
            if(p==null || !p.toLowerCase().endsWith("/get.php")) return null;
            String user=u.getQueryParameter("username");
            String pass=u.getQueryParameter("password");
            if(user==null||pass==null||user.isEmpty()||pass.isEmpty()) return null;
            String scheme=u.getScheme(); String authority=u.getEncodedAuthority();
            if(scheme==null||authority==null) return null;
            return new XtreamLink(scheme+"://"+authority,user,pass);
        }catch(Throwable ignored){ return null; }
    }

    private void display(List<Item> loaded,String label){
        items.clear(); items.addAll(loaded);
        List<String> names=new ArrayList<>(); for(Item i:items) names.add(i.name);
        adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){
            @Override public View getView(int p,View c,android.view.ViewGroup g){
                TextView v=(TextView)super.getView(p,c,g); v.setTextColor(Color.WHITE); v.setTextSize(17);
                v.setPadding(20,18,20,18); v.setBackgroundColor(Color.rgb(16,16,16)); return v;
            }};
        list.setAdapter(adapter);
        status.setText(label+" • "+items.size()+" items");
        list.setOnItemClickListener((a,v,p,id)->{
            Item it=items.get(p);
            if(it.type.equals("series")) loadSeriesEpisodes(it); else play(it.url,it.name);
        });
    }

    private void play(String url,String name){
        if(url==null||url.trim().isEmpty()){ Toast.makeText(this,"This item has no stream URL.",Toast.LENGTH_LONG).show(); return; }
        Intent i=new Intent(this,PlayerActivity.class); i.putExtra("url",url); i.putExtra("name",name); startActivity(i);
    }

    private String get(String u)throws Exception{
        String current=normalizeUrl(u);
        for(int redirects=0; redirects<6; redirects++){
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(45000); c.setUseCaches(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 8K-FCTV/1.2");
            c.setRequestProperty("Accept","*/*");
            c.setRequestProperty("Accept-Encoding","identity");
            c.setRequestProperty("Connection","close");
            int code=c.getResponseCode();
            if(code>=300 && code<400){
                String loc=c.getHeaderField("Location"); c.disconnect();
                if(loc==null||loc.trim().isEmpty()) throw new Exception("Redirect without a destination");
                current=new URL(new URL(current),loc).toString(); continue;
            }
            InputStream in=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
            String body=read(in); c.disconnect();
            if(code<200||code>=300) throw new Exception("Server returned HTTP "+code+(body.isEmpty()?"":" • "+shortText(body)));
            return body;
        }
        throw new Exception("Too many redirects");
    }

    private String read(InputStream in)throws Exception{
        if(in==null) return "";
        BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"));
        StringBuilder b=new StringBuilder(); String l;
        while((l=r.readLine())!=null) b.append(l).append('\n');
        r.close(); return b.toString();
    }

    private String normalizeServer(String s){
        s=s==null?"":s.trim(); if(s.isEmpty()) return "";
        if(!s.matches("(?i)^https?://.*")) s="http://"+s;
        try{
            Uri u=Uri.parse(s);
            if(u.getScheme()!=null && u.getEncodedAuthority()!=null && (s.contains("/get.php")||s.contains("/player_api.php"))){
                s=u.getScheme()+"://"+u.getEncodedAuthority();
            }
        }catch(Throwable ignored){}
        while(s.endsWith("/")) s=s.substring(0,s.length()-1);
        return s;
    }

    private String normalizeUrl(String s){
        s=s==null?"":s.trim();
        if(!s.matches("(?i)^https?://.*")) s="http://"+s;
        return s;
    }

    private String enc(String s)throws Exception { return URLEncoder.encode(s,"UTF-8"); }
    private String path(String s){ try{return enc(s).replace("+","%20");}catch(Exception e){return s;} }

    private String attr(String l,String k){
        String q=k+"=\""; int a=l.indexOf(q); if(a<0)return""; a+=q.length(); int b=l.indexOf('"',a); return b>a?l.substring(a,b):"";
    }

    private String friendly(Throwable e){
        String m=e.getMessage(); if(m==null||m.trim().isEmpty()) m=e.getClass().getSimpleName();
        return m.length()>220?m.substring(0,220):m;
    }

    private String shortText(String s){
        s=s.replace('\n',' ').replace('\r',' ').replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();
        return s.length()>100?s.substring(0,100):s;
    }

    static class Item{
        String name,url,type,icon; int id;
        Item(String n,String u,String t,int i,String ic){name=n;url=u;type=t;id=i;icon=ic;}
    }

    static class XtreamLink{
        String base,user,pass;
        XtreamLink(String b,String u,String p){base=b;user=u;pass=p;}
    }
}
