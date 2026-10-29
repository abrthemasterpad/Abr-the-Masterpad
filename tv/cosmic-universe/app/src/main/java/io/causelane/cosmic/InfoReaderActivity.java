package io.causelane.cosmic;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.view.ViewGroup;
import android.content.SharedPreferences;
import org.json.JSONArray;
import android.text.TextUtils;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Public-source prayer-time and Tamil RSS reader. Network access is optional; the rest of the launcher remains local. */
public final class InfoReaderActivity extends Activity {
    private LinearLayout root;
    private TextView output;
    private LinearLayout newsList;
    private String selectedRegion = "Tamil News";
    private String selectedFeed = "https://feeds.bbci.co.uk/tamil/rss.xml";
    private EditText city, country;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF081226);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(18)); root.setBackgroundColor(0xFF0B1530);
        TextView title = new TextView(this); title.setText("NEXORA · DAILY INFORMATION"); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setTypeface(Typeface.DEFAULT_BOLD); root.addView(title);
        city = new EditText(this); city.setSingleLine(true); city.setText("Chennai"); city.setHint("City"); city.setTextColor(Color.WHITE); city.setHintTextColor(0xFF9FB1D9); city.setBackgroundColor(0xFF1B2948); root.addView(city);
        country = new EditText(this); country.setSingleLine(true); country.setText("India"); country.setHint("Country"); country.setTextColor(Color.WHITE); country.setHintTextColor(0xFF9FB1D9); country.setBackgroundColor(0xFF1B2948); root.addView(country);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button prayer = button("Prayer Times"); prayer.setOnClickListener(v -> loadPrayer()); actions.addView(prayer, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button news = button("Choose News"); news.setOnClickListener(v -> chooseNewsFeed()); actions.addView(news, new LinearLayout.LayoutParams(0, dp(52), 1)); root.addView(actions);
        LinearLayout newsActions = new LinearLayout(this); newsActions.setOrientation(LinearLayout.HORIZONTAL);
        Button cached = button("Offline Cache"); cached.setOnClickListener(v -> showCachedNews()); newsActions.addView(cached, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button refresh = button("Refresh"); refresh.setOnClickListener(v -> loadNews(selectedRegion, selectedFeed)); newsActions.addView(refresh, new LinearLayout.LayoutParams(0, dp(46), 1)); root.addView(newsActions);
        ScrollView scroll = new ScrollView(this); newsList = new LinearLayout(this); newsList.setOrientation(LinearLayout.VERTICAL); newsList.setPadding(dp(4),dp(8),dp(4),dp(12));
        output = new TextView(this); output.setText("Choose Prayer Times or select a news region. Headlines can be cached for offline reading."); output.setTextColor(0xFF1C2A3D); output.setTextSize(16); output.setPadding(dp(10), dp(16), dp(10), dp(16)); newsList.addView(output); scroll.addView(newsList); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button back = button("Back to workspace"); back.setOnClickListener(v -> finish()); root.addView(back); setContentView(root);
    }
    private void loadPrayer() {
        String c = city.getText().toString().trim(); String co = country.getText().toString().trim();
        if (c.isEmpty() || co.isEmpty()) { output.setText("Enter a city and country first."); return; }
        output.setText("Loading prayer times for " + c + "…");
        new Thread(() -> {
            HttpURLConnection conn = null; String result;
            try {
                String endpoint = "https://api.aladhan.com/v1/timingsByCity?city=" + URLEncoder.encode(c, "UTF-8") + "&country=" + URLEncoder.encode(co, "UTF-8") + "&method=3";
                conn = (HttpURLConnection) new URL(endpoint).openConnection(); conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
                JSONObject data = new JSONObject(read(conn.getInputStream())).getJSONObject("data"); JSONObject t = data.getJSONObject("timings");
                result = "ISLAMIC PRAYER TIMES\\n" + data.getJSONObject("date").optString("readable") + " · " + c + ", " + co + "\\n\\n"
                    + "Fajr       " + t.optString("Fajr") + "\\nSunrise    " + t.optString("Sunrise") + "\\nDhuhr      " + t.optString("Dhuhr")
                    + "\\nAsr        " + t.optString("Asr") + "\\nMaghrib    " + t.optString("Maghrib") + "\\nIsha       " + t.optString("Isha")
                    + "\\n\\nCalculation method: Muslim World League (method 3). Verify local mosque timings where precision matters.";
            } catch (Exception e) { result = "Prayer times could not be loaded. Check the internet connection and city/country spelling.\\n\\nSource: AlAdhan public prayer-time API."; }
            finally { if (conn != null) conn.disconnect(); }
            String shown = result; runOnUiThread(() -> { newsList.removeAllViews(); output = new TextView(this); output.setText(shown); output.setTextColor(0xFF17243A); output.setTextSize(18); output.setTypeface(Typeface.SERIF); output.setPadding(dp(10),dp(18),dp(10),dp(18)); newsList.addView(output); });
        }).start();
    }
    private void chooseNewsFeed() {
        new android.app.AlertDialog.Builder(this).setTitle("Choose a news region")
                .setItems(new String[]{"Tamil News", "India News", "World News"}, (dialog, which) -> {
                    if (which == 0) loadNews("Tamil News", "https://feeds.bbci.co.uk/tamil/rss.xml");
                    else if (which == 1) loadNews("India News", "https://feeds.bbci.co.uk/news/world/asia/india/rss.xml");
                    else loadNews("World News", "https://feeds.bbci.co.uk/news/world/rss.xml");
                }).show();
    }
    private void loadNews(String region, String feedUrl) {
        selectedRegion = region; selectedFeed = feedUrl;
        newsList.removeAllViews(); output = new TextView(this); output.setText("Loading " + region + " headlines…"); output.setTextColor(0xFF1C2A3D); output.setTextSize(16); newsList.addView(output);
        new Thread(() -> {
            HttpURLConnection conn = null; JSONArray items = new JSONArray(); String error = null;
            try {
                conn = (HttpURLConnection) new URL(feedUrl).openConnection(); conn.setConnectTimeout(8000); conn.setReadTimeout(8000); conn.setRequestProperty("User-Agent", "NexoraTV/0.5");
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser(); parser.setInput(conn.getInputStream(), "UTF-8");
                boolean inItem=false; String title="", link="", description="";
                for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT && items.length()<20;event=parser.next()){
                    if(event==XmlPullParser.START_TAG && "item".equals(parser.getName())){inItem=true;title="";link="";description="";}
                    else if(event==XmlPullParser.START_TAG && inItem && "title".equals(parser.getName())) title=parser.nextText();
                    else if(event==XmlPullParser.START_TAG && inItem && "link".equals(parser.getName())) link=parser.nextText();
                    else if(event==XmlPullParser.START_TAG && inItem && "description".equals(parser.getName())) description=parser.nextText();
                    else if(event==XmlPullParser.END_TAG && "item".equals(parser.getName()) && inItem){
                        JSONObject item=new JSONObject(); item.put("title",title.trim()); item.put("url",link.trim()); item.put("description",android.text.Html.fromHtml(description,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()); items.put(item); inItem=false;
                    }
                }
                if(items.length()==0) error="The publisher feed returned no articles.";
                getSharedPreferences("nexora_news_cache",MODE_PRIVATE).edit().putString(region,items.toString()).putLong(region+"_saved_at",System.currentTimeMillis()).apply();
            } catch(Exception e){error="Could not reach the live feed. Showing cached headlines if available.";}
            finally{if(conn!=null)conn.disconnect();}
            final String issue=error; final JSONArray fetched=items;
            runOnUiThread(()->{if(fetched.length()>0) renderNews(region,fetched,issue==null?"Updated from public RSS feed.":issue); else showCachedNews();});
        }).start();
    }
    private void showCachedNews(){
        String region=selectedRegion; String raw=getSharedPreferences("nexora_news_cache",MODE_PRIVATE).getString(region,"[]");
        try{JSONArray items=new JSONArray(raw); if(items.length()==0){newsList.removeAllViews();TextView empty=new TextView(this);empty.setText("No offline headlines saved for "+region+". Connect to the internet and press Refresh.");empty.setTextColor(0xFF1C2A3D);empty.setTextSize(16);newsList.addView(empty);}else renderNews(region,items,"OFFLINE CACHE · saved "+android.text.format.DateFormat.format("d MMM, h:mm a",getSharedPreferences("nexora_news_cache",MODE_PRIVATE).getLong(region+"_saved_at",0)));}
        catch(Exception e){output.setText("Offline cache could not be read.");}
    }
    private void renderNews(String region,JSONArray items,String statusText){
        newsList.removeAllViews(); TextView mast=new TextView(this); mast.setText(region.toUpperCase(java.util.Locale.ROOT)+"\nTHE DAILY READER"); mast.setTextColor(0xFF17243A); mast.setTextSize(24); mast.setTypeface(Typeface.SERIF,Typeface.BOLD); mast.setPadding(dp(8),dp(10),dp(8),dp(10)); newsList.addView(mast);
        TextView statusLine=new TextView(this);statusLine.setText(statusText+"\nSource: BBC public RSS. Select a headline to open the publisher article.");statusLine.setTextColor(0xFF596579);statusLine.setTextSize(11);newsList.addView(statusLine);
        for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue; final String url=item.optString("url","");
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(i%2==0?0xFFF4EBDD:0xFFEAE3D5);
            TextView headline=new TextView(this);headline.setText(item.optString("title","Untitled story"));headline.setTextColor(0xFF17243A);headline.setTextSize(i<3?19:16);headline.setTypeface(Typeface.SERIF,Typeface.BOLD);card.addView(headline);
            String summary=item.optString("description","");if(!summary.isEmpty()){TextView desc=new TextView(this);desc.setText(summary);desc.setTextColor(0xFF485364);desc.setTextSize(13);desc.setMaxLines(3);desc.setEllipsize(TextUtils.TruncateAt.END);card.addView(desc);}
            Button read=button("READ ARTICLE  →");read.setOnClickListener(v->{if(url.startsWith("https://")||url.startsWith("http://")){Intent intent=new Intent(this,ArticleReaderActivity.class);intent.putExtra("url",url);startActivity(intent);}else Toast.makeText(this,"Publisher did not provide a valid article link.",Toast.LENGTH_SHORT).show();});card.addView(read);
            newsList.addView(card,new LinearLayout.LayoutParams(-1,-2)); View divider=new View(this);divider.setBackgroundColor(0xFFCEC5B5);newsList.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
    }
    private String read(InputStream in) throws Exception { try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[2048]; int n; while ((n=input.read(b))!=-1) out.write(b,0,n); return out.toString("UTF-8"); } }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF304A80)); b.setFocusable(true); return b; }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
}