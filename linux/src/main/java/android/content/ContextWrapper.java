package android.content;

import android.content.res.AssetManager;
import android.content.res.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class ContextWrapper extends Context {
    private Context base;
    public ContextWrapper() { }
    public ContextWrapper(Context base) { this.base = base; }
    protected void attachBaseContext(Context base) { this.base = base; }
    public Context getBaseContext() { return base == null ? this : base; }
    @Override public AssetManager getAssets() { return base == null ? super.getAssets() : base.getAssets(); }
    @Override public Resources getResources() { return base == null ? super.getResources() : base.getResources(); }
    @Override public Object getSystemService(String name) { return base == null ? super.getSystemService(name) : base.getSystemService(name); }
    @Override public Context getApplicationContext() { return base == null ? super.getApplicationContext() : base.getApplicationContext(); }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return base == null ? super.getSharedPreferences(name, mode) : base.getSharedPreferences(name, mode);
    }
    @Override public File getFilesDir() { return base == null ? super.getFilesDir() : base.getFilesDir(); }
    @Override public File getFileStreamPath(String name) { return base == null ? super.getFileStreamPath(name) : base.getFileStreamPath(name); }
    @Override public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return base == null ? super.openFileInput(name) : base.openFileInput(name);
    }
    @Override public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        return base == null ? super.openFileOutput(name, mode) : base.openFileOutput(name, mode);
    }
}
