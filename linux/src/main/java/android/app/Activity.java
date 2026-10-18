package android.app;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.*;
public class Activity extends ContextWrapper {
    public static final int RESULT_OK=-1,RESULT_CANCELED=0;
    private final Window window=new Window();
    private final WindowManager windowManager=()->new Display();
    private View content;
    private boolean finishing;
    private Intent intent=new Intent(),resultData;
    private Activity resultParent;
    private int resultRequest,resultCode=RESULT_CANCELED;
    public Activity() { super(null); }
    protected void onCreate(Bundle state) {}
    protected void onStart() {}
    protected void onStop() {}
    protected void onPause() {}
    protected void onResume() {}
    protected void onDestroy() {}
    protected void onActivityResult(int request,int result,Intent data) {}
    public void onConfigurationChanged(Configuration configuration) {}
    public void onWindowFocusChanged(boolean focus) {}
    public void onBackPressed() { finish(); }
    public boolean requestWindowFeature(int feature) { return true; }
    public Window getWindow() { return window; }
    public WindowManager getWindowManager() { return windowManager; }
    public void setRequestedOrientation(int orientation) {}
    public void setContentView(View view) { content=view; view.layout(0,0,Display.width,Display.height); }
    public View getContentView() { return content; }
    public View findViewById(int id) { return content!=null&&content.getId()==id?content:null; }
    public void runOnUiThread(Runnable runnable) { new android.os.Handler().post(runnable); }
    public boolean isFinishing() { return finishing; }
    public void finish() {
        if(finishing)return;
        finishing=true;
        if(resultParent!=null)resultParent.onActivityResult(resultRequest,resultCode,resultData);
    }
    public Intent getIntent() { return intent; }
    public void setResult(int code,Intent data) { resultCode=code;resultData=data; }
    public void setResult(int code) { setResult(code,null); }
    public void setVolumeControlStream(int stream) {}
    public void startActivity(Intent intent) { startActivityForResult(intent,-1); }
    public void startActivityForResult(Intent intent,int request) {
        ComponentName target=intent.getComponent();
        if(target==null||!getPackageName().equals(target.getPackageName()))throw new UnsupportedOperationException("External activities are disabled");
        String name=target.getClassName();
        if(!name.matches("com\\.Paladog\\.KorGG\\.MessageBox[23]?Activity"))
            throw new UnsupportedOperationException("Unsupported desktop activity: "+name);
        try {
            // Run the original dialog activity and button callbacks: these also
            // carry save/resume state transitions, beyond onActivityResult.
            Activity child=(Activity)Class.forName(name).getDeclaredConstructor().newInstance();
            child.intent=intent;child.resultParent=this;child.resultRequest=request;
            child.onCreate(null);
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Cannot open local game dialog",error); }
    }
}
