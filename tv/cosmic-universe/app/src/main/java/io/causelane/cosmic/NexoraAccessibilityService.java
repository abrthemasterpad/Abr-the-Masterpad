package io.causelane.cosmic;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.media.AudioManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.pm.ResolveInfo;
import java.util.List;
import java.util.Locale;

/**
 * Optional user-enabled accessibility bridge for voice control across TV apps.
 * Android requires the TV owner to explicitly enable this service in Accessibility settings.
 */
public final class NexoraAccessibilityService extends AccessibilityService {
    private static volatile NexoraAccessibilityService instance;
    @Override public void onServiceConnected() {
        super.onServiceConnected(); instance=this;
        AccessibilityServiceInfo info=getServiceInfo();
        if(info!=null){info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;setServiceInfo(info);}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    public static boolean dispatch(String raw){NexoraAccessibilityService s=instance;return s!=null&&s.execute(raw);}
    private boolean execute(String raw){
        if(raw==null)return false;String q=raw.trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return false;
        if(q.equals("home")||q.equals("go home")||q.equals("launcher"))return performGlobalAction(GLOBAL_ACTION_HOME);
        if(q.equals("back")||q.equals("go back"))return performGlobalAction(GLOBAL_ACTION_BACK);
        if(q.equals("recent apps")||q.equals("show recent apps"))return performGlobalAction(GLOBAL_ACTION_RECENTS);
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(q.contains("mute")){if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);return true;}
        if(q.contains("unmute")){if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_UNMUTE,0);return true;}
        if(q.contains("volume up")||q.contains("louder")||q.contains("increase volume")){if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI);return true;}
        if(q.contains("volume down")||q.contains("quieter")||q.contains("decrease volume")){if(audio!=null)audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);return true;}
        if(q.startsWith("tap ")||q.startsWith("click ")||q.startsWith("select ")){
            String target=q.replaceFirst("^(tap|click|select)\\\\s+","").trim();AccessibilityNodeInfo root=getRootInActiveWindow();
            return root!=null&&clickMatching(root,target);
        }
        if(q.equals("scroll down")||q.equals("page down")){AccessibilityNodeInfo root=getRootInActiveWindow();return root!=null&&scrollMatching(root,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);}
        if(q.equals("scroll up")||q.equals("page up")){AccessibilityNodeInfo root=getRootInActiveWindow();return root!=null&&scrollMatching(root,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}
        String app=q.replaceFirst("^(open|launch|start|play|watch|switch to|go to)\\\\s+","").trim();
        if(!app.isEmpty()&&launchApp(app))return true;
        return false;
    }
    private boolean launchApp(String query){
        Intent tv=new Intent(Intent.ACTION_MAIN);tv.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvResults=getPackageManager().queryIntentActivities(tv,0);
        List<ResolveInfo> matches=new java.util.ArrayList<>(); if(tvResults!=null)matches.addAll(tvResults);
        Intent mobile=new Intent(Intent.ACTION_MAIN);mobile.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standard=getPackageManager().queryIntentActivities(mobile,0);if(standard!=null)matches.addAll(standard);
        for(ResolveInfo info:matches){if(info.activityInfo==null)continue;String label=String.valueOf(info.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT);
            if(label.contains(query)||query.contains(label)){try{Intent launch=new Intent(Intent.ACTION_MAIN);launch.setClassName(info.activityInfo.packageName,info.activityInfo.name);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);return true;}catch(Exception ignored){}}}
        return false;
    }
    private boolean clickMatching(AccessibilityNodeInfo node,String query){
        CharSequence text=node.getText();CharSequence desc=node.getContentDescription();
        if((text!=null&&text.toString().toLowerCase(Locale.ROOT).contains(query))||(desc!=null&&desc.toString().toLowerCase(Locale.ROOT).contains(query))){
            if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            AccessibilityNodeInfo parent=node.getParent();if(parent!=null&&parent.isClickable())return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null&&clickMatching(child,query))return true;}
        return false;
    }
    private boolean scrollMatching(AccessibilityNodeInfo node,int action){
        if(node.isScrollable()&&node.performAction(action))return true;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null&&scrollMatching(child,action))return true;}
        return false;
    }
}