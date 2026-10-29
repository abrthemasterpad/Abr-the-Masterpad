package io.causelane.cosmic;
import android.app.Activity; import android.os.Bundle; import android.graphics.Color; import android.graphics.Typeface; import android.webkit.WebView; import android.webkit.WebViewClient; import android.widget.LinearLayout; import android.widget.TextView; import android.widget.Button; import android.view.ViewGroup;
/** Newspaper-style article reader. Android WebView caching is enabled for repeat/offline visits where publisher cache headers allow it. */
public final class ArticleReaderActivity extends Activity {
 @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(0xFF101C30);
  LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFFF5F0E6); root.setPadding(14,10,14,10);
  TextView masthead=new TextView(this); masthead.setText("NEXORA  /  THE DAILY READER"); masthead.setTextColor(0xFF1C2A3D); masthead.setTextSize(20); masthead.setTypeface(Typeface.SERIF,Typeface.BOLD); root.addView(masthead);
  TextView caption=new TextView(this); caption.setText("Publisher article · cached web content where available"); caption.setTextColor(0xFF5C6572); caption.setTextSize(11); root.addView(caption);
  WebView web=new WebView(this); web.setBackgroundColor(0xFFF5F0E6); web.getSettings().setJavaScriptEnabled(false); web.getSettings().setDomStorageEnabled(true); web.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT); web.setWebViewClient(new WebViewClient());
  String url=getIntent().getStringExtra("url"); if(url==null || !(url.startsWith("https://")||url.startsWith("http://"))) url="https://www.bbc.com/news";
  web.loadUrl(url); root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  Button back=new Button(this); back.setText("Back to headlines"); back.setOnClickListener(v->finish()); root.addView(back); setContentView(root);
 }
 @Override public void onBackPressed(){ super.onBackPressed(); }
}