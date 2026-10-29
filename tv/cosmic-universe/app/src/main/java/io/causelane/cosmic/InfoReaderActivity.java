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
        Button news = button("Tamil News"); news.setOnClickListener(v -> loadNews()); actions.addView(news, new LinearLayout.LayoutParams(0, dp(52), 1)); root.addView(actions);
        ScrollView scroll = new ScrollView(this); output = new TextView(this); output.setText("Choose Prayer Times for the selected city, or load current Tamil headlines.\\n\\nPublic data sources; no paid API key or account required."); output.setTextColor(0xFFE5ECFF); output.setTextSize(17); output.setPadding(dp(10), dp(16), dp(10), dp(16)); scroll.addView(output); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
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
            String shown = result; runOnUiThread(() -> output.setText(shown));
        }).start();
    }
    private void loadNews() {
        output.setText("Loading Tamil headlines…");
        new Thread(() -> {
            HttpURLConnection conn = null; String result;
            try {
                conn = (HttpURLConnection) new URL("https://feeds.bbci.co.uk/tamil/rss.xml").openConnection(); conn.setConnectTimeout(8000); conn.setReadTimeout(8000); conn.setRequestProperty("User-Agent", "NexoraTV/0.4");
                XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser(); parser.setInput(conn.getInputStream(), "UTF-8");
                StringBuilder text = new StringBuilder("TAMIL NEWS · PUBLIC RSS\\n\\n"); boolean item = false; String headline = ""; int count = 0;
                for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT && count < 15; event = parser.next()) {
                    if (event == XmlPullParser.START_TAG && "item".equals(parser.getName())) { item = true; headline = ""; }
                    else if (event == XmlPullParser.START_TAG && item && "title".equals(parser.getName())) headline = parser.nextText();
                    else if (event == XmlPullParser.END_TAG && "item".equals(parser.getName()) && item) { text.append("• ").append(headline.trim()).append("\\n\\n"); count++; item = false; }
                }
                text.append("\\nSource: BBC News Tamil RSS. Headlines are fetched live; article availability and feed uptime depend on the publisher."); result = text.toString();
            } catch (Exception e) { result = "Tamil news feed could not be reached. Check internet access and try again.\\n\\nSource: BBC News Tamil RSS."; }
            finally { if (conn != null) conn.disconnect(); }
            String shown = result; runOnUiThread(() -> output.setText(shown));
        }).start();
    }
    private String read(InputStream in) throws Exception { try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[2048]; int n; while ((n=input.read(b))!=-1) out.write(b,0,n); return out.toString("UTF-8"); } }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF304A80)); b.setFocusable(true); return b; }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
}