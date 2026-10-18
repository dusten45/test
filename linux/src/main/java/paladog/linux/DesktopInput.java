package paladog.linux;

import android.view.MotionEvent;
import com.common.AppDelegate;
import com.my.UI.UIInfo;
import org.cocos2d.events.CCTouchDispatcher;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import static org.lwjgl.glfw.GLFW.*;

/** Keyboard and mouse enter the same touch dispatcher as the Android game. */
public final class DesktopInput {
    private static final int MOVEMENT_POINTER=1;
    private static final int TAP_POINTER=2;
    // Paladog's fifth and sixth visible slots reverse their internal ID order.
    // Keep disabled/locked positions in the mapping as well.
    private static final int[] PALADOG_UNITS={12,13,14,15,17,16,18,19,20};
    private record Point(float x,float y) {}
    private final LinkedHashMap<Integer,Point> pointers=new LinkedHashMap<>();
    private final LinkedHashMap<Integer,Integer> movementKeys=new LinkedHashMap<>();
    private final Set<Integer> actionKeys=new HashSet<>();
    private int movementDirection;
    private boolean towerSelection;
    private int pendingLane;
    private boolean observed,observedActive;
    private Object observedScene,observedInfo;
    private int observedMode,observedStep;
    private final int width,height;
    public DesktopInput(int width,int height) { this.width=width; this.height=height; }
    public void mouse(boolean down,float x,float y) {
        if(down) {
            // A real click takes control of the original selection UI. Never
            // replay a queued keyboard lane after that click.
            forgetTowerSelection();
            press(0,new Point(x,y));
        } else release(0);
    }
    public void move(float x,float y) { if(pointers.containsKey(0)) { pointers.put(0,new Point(x,y)); emit(MotionEvent.ACTION_MOVE); } }
    public void clear() {
        // The retained game does not release held controls on ACTION_CANCEL.
        // Release outside every button, then dispatch before the pause changes state.
        if(towerSelection) {
            AppDelegate app=AppDelegate.sharedAppDelegate();
            if(app.g_GI==observedInfo&&app.m_pLastScene==observedScene&&app.m_pGameManager!=null
                &&app.m_pGameManager.m_iGamePlayKind==3&&app.g_GI.iTowerCreateUnitMode>=1
                &&app.g_GI.iTowerCreateUnitMode<=3)app.g_GI.iTowerCreateUnitMode=6;
        }
        forgetTowerSelection();actionKeys.clear();movementKeys.clear();movementDirection=0;
        pointers.replaceAll((id,point)->new Point(-1000,-1000));
        while(!pointers.isEmpty())release(pointers.keySet().iterator().next());
        CCTouchDispatcher.sharedDispatcher().update();
    }
    public void key(int key,int action) {
        if(key==GLFW_KEY_UNKNOWN)return;
        int id=key+1;
        if(action==GLFW_RELEASE) {
            actionKeys.remove(key);
            if(directionForKey(key)!=0)movementKey(key,false);else release(id);
            return;
        }
        if(action!=GLFW_PRESS)return;
        AppDelegate app=AppDelegate.sharedAppDelegate();
        synchronize(app);
        if(!active(app))return;
        int mode=app.m_pGameManager.m_iGamePlayKind;
        if(directionForKey(key)!=0) {
            if(mode!=1&&mode!=3&&mode!=6&&mode!=7)movementKey(key,true);
            return;
        }
        if(!actionKeys.add(key))return;
        if(key>=GLFW_KEY_1&&key<=GLFW_KEY_9) {
            int number=key-GLFW_KEY_1+1;
            if(mode==1) {
                if(number<=5)destiny(app,number);
                return;
            }
            if(mode==3) { tower(app,number);return; }
        }
        Point point=null;
        int ui=-1;
        if(key>=GLFW_KEY_1&&key<=GLFW_KEY_9)ui=unitUI(app,key-GLFW_KEY_1+1);
        if(key==GLFW_KEY_Q)ui=1;
        if(key==GLFW_KEY_W)ui=2;
        if(key==GLFW_KEY_E)ui=3;
        if(key==GLFW_KEY_SPACE)ui=8;
        if(ui>=0)point=uiPoint(app,ui);
        if(point!=null) {
            if(mode==3)forgetTowerSelection();
            press(id,point);
        }
    }
    /** Called once per frame on the render thread, before the retained game. */
    public void update() {
        AppDelegate app=AppDelegate.sharedAppDelegate();
        synchronize(app);
        if(!active(app)||!towerSelection)return;
        int state=app.g_GI.iTowerCreateUnitMode;
        if(state<1||state>3) { forgetTowerSelection();return; }
        if(state==3&&pendingLane!=0&&!hasActionPointer()) {
            int lane=pendingLane;
            forgetTowerSelection();
            tap(gamePoint(240,71+(lane-1)*34));
        }
    }
    private static boolean active(AppDelegate app) {
        return app.m_pGameManager!=null&&app.m_pUIManager!=null&&app.g_GI!=null
            &&!app.g_GI.bPause&&!app.g_GI.bSkillLevelUp&&!app.g_GI.bBossEvent&&app.g_GI.iEventSceneNum==0
            &&app.m_pLastScene instanceof com.my.Layer.GameScene;
    }
    private void synchronize(AppDelegate app) {
        boolean enabled=active(app);
        int mode=app.m_pGameManager==null?-1:app.m_pGameManager.m_iGamePlayKind;
        int step=app.m_pGameManager==null?-1:app.m_pGameManager.m_iGameStep;
        if(observed&&(observedScene!=app.m_pLastScene||observedInfo!=app.g_GI||observedMode!=mode
            ||observedStep!=step||observedActive!=enabled))clear();
        observed=true;observedScene=app.m_pLastScene;observedInfo=app.g_GI;
        observedMode=mode;observedStep=step;observedActive=enabled;
    }
    private static int unitUI(AppDelegate app,int number) {
        return app.g_GI.hkHeroKind==0?PALADOG_UNITS[number-1]:11+number;
    }
    private Point uiPoint(AppDelegate app,int id) {
        UIInfo item=app.m_pUIManager.GetUIInfoByID(id);
        if(item==null||item.m_iCurState!=0||item.m_pSpUp==null)return null;
        float scale=item.m_fScale*(item.m_pSpUp.getHD()?0.5f:1f);
        return gamePoint(item.m_v2BasePos.x+item.m_v2OffsetPos.x*scale,
            item.m_v2BasePos.y-item.m_v2OffsetPos.y*scale);
    }
    private void destiny(AppDelegate app,int number) {
        if(hasActionPointer())return;
        var info=app.g_GI;
        for(int slot=0;slot<info.iDestinyOrder.length;slot++) {
            if(info.iDestinyOrder[slot]!=number-1||info.dwDestinyBox[slot]<=0)continue;
            float x=info.fDestinyX[slot];
            if(!Float.isFinite(x)||x<0||x>480)return;
            // The original hit test chooses the first overlapping array slot.
            // Do not accidentally consume a different item while chips slide.
            for(int earlier=0;earlier<slot;earlier++)
                if(info.iDestinyOrder[earlier]>=0&&Math.abs(x-info.fDestinyX[earlier])<=23)return;
            tap(gamePoint(x,269));
            return;
        }
    }
    private void tower(AppDelegate app,int number) {
        if(hasActionPointer())return;
        int state=app.g_GI.iTowerCreateUnitMode;
        if(state<1||state>3)forgetTowerSelection();
        if(towerSelection) {
            if(pendingLane==0&&number<=5)pendingLane=number;
            update();
            return;
        }
        if(state!=0&&state!=3)return;
        Point point=uiPoint(app,unitUI(app,number));
        if(point==null)return;
        tap(point);
        // A tutorial, disabled control, or game event may reject the first
        // digit. Only retain a lane prefix after the original UI accepts it.
        towerSelection=app.g_GI.iTowerCreateUnitMode==1;
        pendingLane=0;
    }
    private void forgetTowerSelection() { towerSelection=false;pendingLane=0; }
    private boolean hasActionPointer() {
        return pointers.keySet().stream().anyMatch(id->id!=MOVEMENT_POINTER);
    }
    private void tap(Point point) {
        if(hasActionPointer())return;
        press(TAP_POINTER,point);release(TAP_POINTER);
        // Sequential digits may arrive in one GLFW poll. Let the real touch
        // callbacks update their state before translating the next digit.
        CCTouchDispatcher.sharedDispatcher().update();
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
