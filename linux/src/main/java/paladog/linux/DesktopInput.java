package paladog.linux;

import android.view.MotionEvent;
import com.common.AppDelegate;
import com.my.UI.UIInfo;
import org.cocos2d.events.CCTouchDispatcher;
import java.util.LinkedHashMap;
import static org.lwjgl.glfw.GLFW.*;

/** Keyboard and mouse enter the same touch dispatcher as the Android game. */
public final class DesktopInput {
    private static final int MOVEMENT_POINTER=1;
    private record Point(float x,float y) {}
    private final LinkedHashMap<Integer,Point> pointers=new LinkedHashMap<>();
    private final LinkedHashMap<Integer,Integer> movementKeys=new LinkedHashMap<>();
    private int movementDirection;
    private final int width,height;
    public DesktopInput(int width,int height) { this.width=width; this.height=height; }
    public void mouse(boolean down,float x,float y) { if(down)press(0,new Point(x,y));else release(0); }
    public void move(float x,float y) { if(pointers.containsKey(0)) { pointers.put(0,new Point(x,y)); emit(MotionEvent.ACTION_MOVE); } }
    public void clear() {
        // The retained game does not release held controls on ACTION_CANCEL.
        // Release outside every button, then dispatch before the pause changes state.
        movementKeys.clear();movementDirection=0;
        pointers.replaceAll((id,point)->new Point(-1000,-1000));
        while(!pointers.isEmpty())release(pointers.keySet().iterator().next());
        CCTouchDispatcher.sharedDispatcher().update();
    }
    public void key(int key,int action) {
        if(key==GLFW_KEY_UNKNOWN)return;
        int id=key+1;
        if(action==GLFW_RELEASE) {
            if(directionForKey(key)!=0)movementKey(key,false);else release(id);
            return;
        }
        if(action!=GLFW_PRESS)return;
        AppDelegate app=AppDelegate.sharedAppDelegate();
        if(app.m_pGameManager==null||app.m_pUIManager==null||app.g_GI==null||app.g_GI.bPause
            ||!(app.m_pLastScene instanceof com.my.Layer.GameScene))return;
        if(directionForKey(key)!=0) { movementKey(key,true);return; }
        Point point=null;
        int ui=-1;
        if(key>=GLFW_KEY_1&&key<=GLFW_KEY_9)ui=12+(key-GLFW_KEY_1);
        if(key==GLFW_KEY_Q)ui=1;
        if(key==GLFW_KEY_W)ui=2;
        if(key==GLFW_KEY_E)ui=3;
        if(key==GLFW_KEY_SPACE)ui=8;
        if(ui>=0) {
            UIInfo item=app.m_pUIManager.GetUIInfoByID(ui);
            if(item!=null&&item.m_iCurState!=2&&item.m_pSpUp!=null) {
                float scale=item.m_fScale*(item.m_pSpUp.getHD()?0.5f:1f);
                point=gamePoint(item.m_v2BasePos.x+item.m_v2OffsetPos.x*scale,item.m_v2BasePos.y-item.m_v2OffsetPos.y*scale);
            }
        }
        if(point!=null)press(id,point);
    }
    private static int directionForKey(int key) {
        if(key==GLFW_KEY_A||key==GLFW_KEY_LEFT)return -1;
        if(key==GLFW_KEY_D||key==GLFW_KEY_RIGHT)return 1;
        return 0;
    }
    // Kept separate from the scene gate so the touch contract can be exercised
    // through the actual dispatcher without constructing a battle or save file.
    void movementKey(int key,boolean down) {
        int direction=directionForKey(key);
        if(direction==0)return;
        if(down) {
            if(movementKeys.containsKey(key))return;
            movementKeys.put(key,direction);
        } else movementKeys.remove(key);
        int desired=0;
        for(int held:movementKeys.values())desired=held;
        if(desired==movementDirection)return;
        // The original release stops movement. A MOVE cannot restart a stopped
        // hero, so switching direction or falling back needs a fresh DOWN.
        release(MOVEMENT_POINTER);
        movementDirection=desired;
        if(desired!=0)press(MOVEMENT_POINTER,gamePoint(desired<0?65:185,295));
    }
    private Point gamePoint(float x,float y) { return new Point(x*width/480f,y*height/320f); }
    private void press(int id,Point point) {
        if(pointers.containsKey(id)||pointers.size()>=2)return;
        // The game accepts two touches. Reserve one for all movement aliases,
        // leaving one for a mouse, summon, magic, or jump action.
        if(id!=MOVEMENT_POINTER&&pointers.keySet().stream().anyMatch(p->p!=MOVEMENT_POINTER))return;
        pointers.put(id,point);
        emit(pointers.size()==1?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_POINTER_DOWN|((pointers.size()-1)<<8));
    }
    private void release(int id) {
        if(!pointers.containsKey(id))return;
        int index=0;for(int key:pointers.keySet()) { if(key==id)break;index++; }
        emit(pointers.size()==1?MotionEvent.ACTION_UP:MotionEvent.ACTION_POINTER_UP|(index<<8));
        pointers.remove(id);
        if(!pointers.isEmpty())emit(MotionEvent.ACTION_MOVE);
    }
    private void emit(int action) {
        int n=pointers.size(),i=0;int[] ids=new int[n];float[] x=new float[n],y=new float[n];
        for(var entry:pointers.entrySet()) { ids[i]=entry.getKey();x[i]=entry.getValue().x;y[i]=entry.getValue().y;i++; }
        CCTouchDispatcher.sharedDispatcher().queueMotionEvent(new MotionEvent(action,ids,x,y));
    }
}
