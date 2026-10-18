package android.os;
import java.util.HashMap;
public class Bundle {
    private final HashMap<String,Object> values = new HashMap<>();
    public Bundle() {}
    public Bundle(Bundle other) { values.putAll(other.values); }
    public Object get(String key) { return values.get(key); }
    public String getString(String key) { return (String) values.get(key); }
    public int getInt(String key) { return getInt(key,0); }
    public int getInt(String key,int fallback) { Object v=values.get(key); return v==null?fallback:(Integer)v; }
    public boolean getBoolean(String key) { Object v=values.get(key); return v!=null&&(Boolean)v; }
    public void putString(String key,String value) { values.put(key,value); }
    public void putInt(String key,int value) { values.put(key,value); }
    public void putBoolean(String key,boolean value) { values.put(key,value); }
    public boolean containsKey(String key) { return values.containsKey(key); }
}
