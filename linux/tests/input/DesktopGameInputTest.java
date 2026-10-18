package paladog.linux;

import android.view.MotionEvent;
import com.common.AppDelegate;
import com.my.Char.CharInfo;
import com.my.Game.GameManager;
import com.my.Layer.GameLayer;
import com.my.Layer.GameScene;
import com.my.Layer.GameUILayer;
import com.my.Sound.SoundManager;
import com.my.Struct.GAMEINFO;
import com.my.Struct.VEC2;
import com.my.UI.UIInfo;
import com.my.UI.UIManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.cocos2d.events.CCTouchDispatcher;
import org.cocos2d.nodes.CCDirector;
import org.cocos2d.nodes.CCSprite;
import org.cocos2d.opengl.CCTexture2D;
import org.cocos2d.protocols.CCMotionEventProtocol;
import org.cocos2d.types.CGPoint;
import org.cocos2d.types.CGSize;
import sun.misc.Unsafe;
import static org.lwjgl.glfw.GLFW.*;

/** Tests retained GameUILayer/GameLayer bytecode, with rendering/audio/spawn sinks.
 * Fixtures never read or write a save. Unit cost/balance is not replaced in production.
 */
public final class DesktopGameInputTest {
    private static final Unsafe UNSAFE;
    static {
        try { Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);UNSAFE=(Unsafe)field.get(null); }
        catch(ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
    private record Spawn(int unit,int lane,boolean free) {}
    public static final class RecordingApp extends AppDelegate {
        List<Spawn> spawns;
        @Override public CharInfo CreateUnit(int unit,int lane,boolean free,float offset) {
            spawns.add(new Spawn(unit,lane,free));return null;
        }
    }
    public static final class SilentSound extends SoundManager {
        @Override public void PlayCommon(int id) {}
    }
    public static final class SpriteSink extends CCSprite {
        private SpriteSink() { super((CCTexture2D)null); }
        @Override public void setPosition(CGPoint position) {}
        @Override public void setVisible(boolean visible) {}
        @Override public void setScaleX(float scale) {}
        @Override public boolean getHD() { return false; }
    }
    public static final class OriginalUI extends GameUILayer {
        int mace=-1;
        private OriginalUI() {}
        @Override protected boolean UseMace(int index) { mace=index;return true; }
        @Override protected boolean CheckFoodManaRed(int x,int y) { return false; }
        void prepare() throws Exception { m_SpData=sprites(73); }
        void frame() { TowerProc(1f/60); }
        int selectedUnit() { return m_iTowerReadyUnitNum; }
        int selectedLane() { return m_iTowerSelLine; }
    }
    public static final class OriginalWorld extends GameLayer {
        private OriginalWorld() {}
        void prepare() throws Exception {
            m_SpData=sprites(86);m_fTowerCreateRailX=new float[5];m_fTowerCreateRailSpeed=new float[5];
        }
        void frame() { TowerProc(1f/60); }
    }
    private static final class Fixture implements AutoCloseable {
        final RecordingApp app;
        final OriginalUI ui;
        final OriginalWorld world;
        final DesktopInput input=new DesktopInput(960,640);
        final CCTouchDispatcher dispatcher=CCTouchDispatcher.sharedDispatcher();
        final CCMotionEventProtocol bridge;
        final List<MotionEvent> events=new ArrayList<>();
        Fixture(int mode) throws Exception {
            app=allocate(RecordingApp.class);app.spawns=new ArrayList<>();
            Field singleton=AppDelegate.class.getDeclaredField("_sharedAppDelegate");singleton.setAccessible(true);singleton.set(null,app);
            app.g_GI=new GAMEINFO();app.g_GI.fScreenW=480;app.g_GI.fScreenH=320;
            app.g_GI.fScreenScaleW=2;app.g_GI.fScreenScaleH=2;app.g_GI.hkHeroKind=0;
            app.m_pGameManager=allocate(GameManager.class);app.m_pGameManager.m_iGamePlayKind=mode;
            app.m_pGameManager.m_iGameStep=5;app.m_pGameManager.m_iPlayerID=-1;
            app.m_pUIManager=new UIManager();app.m_pUIManager.Initialize();
            app.m_pSoundManager=allocate(SilentSound.class);app.m_pLastScene=allocate(GameScene.class);
            ui=allocate(OriginalUI.class);ui.prepare();world=allocate(OriginalWorld.class);world.prepare();
            CCDirector director=CCDirector.sharedDirector();director.setScreenSize(480,320);director.setGameScale(1,1);
            Field surface=CCDirector.class.getDeclaredField("surfaceSize_");surface.setAccessible(true);surface.set(director,CGSize.make(960,640));
            if(mode!=1) {
                ArrayList<UIInfo> buttons=new ArrayList<>();
                for(int id=12;id<=20;id++) {
                    UIInfo button=new UIInfo();button.m_iID=id;button.m_iKind=1;button.m_fScale=1;
                    button.m_pLayer=ui;button.m_pSpUp=allocate(SpriteSink.class);
                    int display=id==16?5:id==17?4:id-12;
                    button.m_v2BasePos=VEC2.make(36+display*51,mode==3?293:238);
                    button.m_v2Size=VEC2.make(44,44);buttons.add(button);
                }
                Field list=UIManager.class.getDeclaredField("pUIInfoList");list.setAccessible(true);list.set(app.m_pUIManager,buttons);
            }
            bridge=event->{
                events.add(MotionEvent.obtain(event));
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> ui.ccTouchesBegan(event);
                    case MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP -> ui.ccTouchesEnded(event);
                    default -> {}
                }
            };
            dispatcher.addMotionListener(bridge);input.update();
        }
        void press(int number) { input.key(GLFW_KEY_1+number-1,GLFW_PRESS); }
        void release(int number) { input.key(GLFW_KEY_1+number-1,GLFW_RELEASE); }
        void digit(int number) { press(number);release(number);dispatcher.update(); }
        void frame() { input.update();world.frame();ui.frame(); }
        void expectSpawn(int index,int unit,int lane) {
            require(app.spawns.size()==index+1,"Exactly one summon request per completed command");
            require(app.spawns.get(index).equals(new Spawn(unit,lane,false)),"Original summon arguments: "+app.spawns);
        }
        void box(int slot,int order,long value,float x) {
            app.g_GI.iDestinyOrder[slot]=order;app.g_GI.dwDestinyBox[slot]=value;app.g_GI.fDestinyX[slot]=x;
        }
        @Override public void close() { input.clear();dispatcher.removeMotionListener(bridge); }
    }
    private static <T> T allocate(Class<T> type) throws InstantiationException { return type.cast(UNSAFE.allocateInstance(type)); }
    private static CCSprite[] sprites(int count) throws Exception {
        CCSprite[] result=new CCSprite[count];for(int i=0;i<count;i++)result[i]=allocate(SpriteSink.class);return result;
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    public static void main(String[] arguments) throws Exception {
        normalOrder();destinyOrder();towerSequence();towerCancellation();
        System.out.println("DesktopGameInputTest passed: original UI callbacks, visual unit order, Destiny recycled slots/compaction, fast 11/12/35 and every lane, rejected selection, pause/focus/mouse/mode cancellation");
    }
    private static void normalOrder() throws Exception {
        try(Fixture f=new Fixture(0)) {
            f.digit(5);f.expectSpawn(0,5,-1);f.digit(6);f.expectSpawn(1,4,-1);
            f.app.m_pUIManager.GetUIInfoByID(17).ChangeState(2);
            f.digit(5);require(f.app.spawns.size()==2,"Disabled fifth slot does not shift sixth onto key 5");
            f.digit(6);f.expectSpawn(2,4,-1);
            // Darkdog's IDs remain in their original order. Use touch coordinates
            // rather than its CreateUnit path, which needs a real Darkdog hero.
            f.app.g_GI.hkHeroKind=2;f.app.m_pGameManager.m_iGameStep=0;
            f.digit(5);
            MotionEvent down=f.events.get(f.events.size()-2);
            require(down.getX()==(36+5*51)*2,"Darkdog key 5 preserves UI ID16");
        }
    }
    private static void destinyOrder() throws Exception {
        try(Fixture f=new Fixture(1)) {
            Arrays.fill(f.app.g_GI.iDestinyOrder,-1);
            f.box(7,0,1,43);f.box(2,1,3,92);f.box(5,2,6,141);
            f.input.key(GLFW_KEY_D,GLFW_PRESS);require(f.events.isEmpty(),"Destiny cannot retain a movement pointer");
            f.digit(5);f.digit(6);require(f.events.isEmpty(),"Missing stack and unspecified keys are no-ops");
            f.digit(1);f.expectSpawn(0,0,-1);
            require(f.app.g_GI.iDestinyOrder[2]==0&&f.app.g_GI.iDestinyOrder[5]==1,"Original consumer compacts order after bottom removal");
            f.digit(2);f.expectSpawn(1,5,-1);
            require(f.app.g_GI.dwDestinyBox[5]==0&&f.app.g_GI.dwDestinyBox[2]==3,"Select logical order, not recycled array index");
            f.digit(1);f.expectSpawn(2,2,-1);
            f.box(4,0,22,43);f.app.g_GI.dwEquip[1]=22;f.digit(1);
            require(f.ui.mace==1&&f.app.g_GI.dwDestinyBox[4]==0,"Original Destiny consumer routes a magic item to its equipped slot");
            f.box(4,0,1,505);int events=f.events.size();f.digit(1);require(f.events.size()==events,"Offscreen incoming chip is not used");
            f.box(0,1,3,43);f.box(4,0,1,43);f.digit(1);
            require(f.app.g_GI.dwDestinyBox[4]==1,"Overlapping earlier array slot cannot steal keyboard selection");
            f.app.g_GI.iDestinyOrder[0]=-1;f.app.g_GI.dwDestinyBox[0]=0;
            f.app.m_pGameManager.m_iGameStep=30;f.app.m_pGameManager.m_iTutorialStep=35;
            f.digit(1);require(f.app.g_GI.dwDestinyBox[4]==1,"Original magic-only tutorial rejects a unit chip");
            f.app.m_pGameManager.m_iTutorialStep=37;f.digit(1);f.expectSpawn(3,0,-1);
            require(f.app.m_pGameManager.m_iTutorialStep==38,"Original tutorial advances after permitted chip");
        }
    }
    private static void towerSequence() throws Exception {
        try(Fixture f=new Fixture(3)) {
            f.input.key(GLFW_KEY_A,GLFW_PRESS);require(f.events.isEmpty(),"Tower cannot retain a movement pointer");
            f.digit(1);f.input.key(GLFW_KEY_LEFT_SHIFT,GLFW_PRESS);f.input.key(GLFW_KEY_LEFT_SHIFT,GLFW_RELEASE);f.digit(1);
            require(f.app.g_GI.iTowerCreateUnitMode==1&&f.app.spawns.isEmpty(),"Fast 11 waits for original lane setup");
            f.frame();require(f.app.g_GI.iTowerCreateUnitMode==3,"Original world/UI transition makes lanes ready");
            f.frame();f.expectSpawn(0,0,1);
            f.digit(1);f.digit(2);f.frame();f.frame();f.expectSpawn(1,0,2);
            f.press(3);f.press(5);f.release(3);f.release(5);f.frame();f.frame();f.expectSpawn(2,2,5);
            for(int lane=1;lane<=5;lane++) {
                f.digit(5);f.digit(lane);f.frame();f.frame();f.expectSpawn(lane+2,5,lane);
            }
            f.digit(1);f.input.key(GLFW_KEY_1,GLFW_REPEAT);f.frame();
            require(f.app.g_GI.iTowerCreateUnitMode==3,"OS key repeat cannot become a lane digit");
            f.digit(9);f.frame();require(f.app.g_GI.iTowerCreateUnitMode==3,"Lane digits outside 1..5 are ignored");
            f.digit(4);f.frame();f.expectSpawn(8,0,4);
            f.app.m_pUIManager.GetUIInfoByID(12).ChangeState(2);f.digit(1);
            require(f.app.g_GI.iTowerCreateUnitMode==0,"Disabled first unit does not leave a pending prefix");
            f.digit(2);require(f.ui.selectedUnit()==1,"Next key after failed selection starts a new unit selection");
        }
    }
    private static void towerCancellation() throws Exception {
        try(Fixture f=new Fixture(3)) {
            f.digit(1);f.digit(5);f.input.clear();
            require(f.app.g_GI.iTowerCreateUnitMode==6,"Focus cleanup requests the original selection-reset state");
            f.app.g_GI.iTowerCreateUnitMode=0;f.input.update();require(f.app.spawns.isEmpty(),"Cancelled lane never replays");
            f.digit(2);f.digit(3);f.app.g_GI.bPause=true;f.input.update();
            require(f.app.g_GI.iTowerCreateUnitMode==6,"Pause cancels an unfinished command");
            f.app.g_GI.bPause=false;f.app.g_GI.iTowerCreateUnitMode=0;f.input.update();
            f.digit(1);f.digit(5);f.frame();
            f.input.mouse(true,480,210);f.input.mouse(false,480,210);f.dispatcher.update();
            f.frame();f.expectSpawn(0,0,2); // Real click takes lane 2, replacing queued lane 5.
            f.digit(1);f.digit(5);f.app.m_pGameManager.m_iGamePlayKind=1;f.input.update();
            f.app.m_pGameManager.m_iGamePlayKind=3;f.app.g_GI.iTowerCreateUnitMode=3;f.input.update();
            require(f.app.spawns.size()==1,"Mode transition discards queued lane");
            f.app.g_GI.iTowerCreateUnitMode=0;f.input.mouse(true,10,10);f.dispatcher.update();
            int before=f.events.size();f.digit(1);require(f.events.size()==before,"Held mouse blocks keyboard selection without creating phantom contacts");
            f.input.mouse(false,10,10);f.dispatcher.update();
            f.app.m_pGameManager.m_iGameStep=30;f.app.m_pGameManager.m_iTutorialStep=53;
            f.digit(1);require(f.app.g_GI.iTowerCreateUnitMode==0,"Original tutorial rejects premature unit selection");
            f.app.m_pGameManager.m_iTutorialStep=54;f.digit(1);f.digit(1);f.frame();f.frame();f.expectSpawn(1,0,1);
            require(f.app.m_pGameManager.m_iTutorialStep==55,"Original tower tutorial advances after keyboard command");
        }
    }
}
