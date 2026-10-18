package android.content;
import android.os.Bundle;
public class Intent {
    private ComponentName component;
    private final Bundle extras=new Bundle();
    private String action;
    public Intent() {}
    public Intent(String action) { this.action=action; }
    public Intent(Context context,Class<?> type) { component=new ComponentName(context,type); }
    public ComponentName getComponent() { return component; }
    public Intent setComponent(ComponentName value) { component=value; return this; }
    public Intent setClass(Context context,Class<?> type) { component=new ComponentName(context,type); return this; }
    public Intent setPackage(String value) { return this; }
    public Intent setFlags(int flags) { return this; }
    public String getAction() { return action; }
    public Intent putExtra(String key,String value) { extras.putString(key,value); return this; }
    public Intent putExtra(String key,int value) { extras.putInt(key,value); return this; }
    public Intent putExtra(String key,boolean value) { extras.putBoolean(key,value); return this; }
    public String getStringExtra(String key) { return extras.getString(key); }
    public int getIntExtra(String key,int fallback) { return extras.getInt(key,fallback); }
    public Bundle getExtras() { return extras; }
}
