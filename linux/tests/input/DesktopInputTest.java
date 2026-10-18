package paladog.linux;

import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cocos2d.events.CCTouchDispatcher;
import org.cocos2d.protocols.CCMotionEventProtocol;
import static org.lwjgl.glfw.GLFW.*;

/** Exercises real dispatcher events, without constructing or changing a game save. */
public final class DesktopInputTest {
    private static final class Capture implements CCMotionEventProtocol {
        final List<MotionEvent> events=new ArrayList<>();
        final Map<Integer,float[]> active=new LinkedHashMap<>();
        @Override public void onTouch(MotionEvent event) {
            events.add(MotionEvent.obtain(event));
            int action=event.getActionMasked();
            int changed=event.getPointerId(event.getActionIndex());
            require(event.getPointerCount()>=1&&event.getPointerCount()<=2,"Original two-pointer limit");
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
                require(!active.containsKey(changed),"DOWN must start a new contact");
                active.put(changed,new float[2]);
            }
            require(event.getPointerCount()==active.size(),"Every snapshot must include all current contacts");
            for(int i=0;i<event.getPointerCount();i++) {
                int id=event.getPointerId(i);
                require(active.containsKey(id),"Snapshot must not contain an unknown contact");
                active.put(id,new float[]{event.getX(i),event.getY(i)});
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)active.remove(changed);
            require(action!=MotionEvent.ACTION_CANCEL,"Original game does not release controls on CANCEL");
        }
        void drain() { CCTouchDispatcher.sharedDispatcher().update(); }
        void movement(float x) {
            require(active.size()>=1,"Movement contact exists");
            int movementContacts=0;
            for(var entry:active.entrySet())if(entry.getKey()!=0) {
                require(entry.getValue()[0]==x&&entry.getValue()[1]==590,"Movement position matches direction");
                movementContacts++;
            }
            require(movementContacts==1,"All direction keys share exactly one contact");
        }
        void mouseUnchanged() {
            require(active.containsKey(0),"Action contact stays held during movement changes");
            require(active.get(0)[0]==800&&active.get(0)[1]==450,"Action position remains unchanged");
        }
    }
    private static void require(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] arguments) {
        CCTouchDispatcher dispatcher=CCTouchDispatcher.sharedDispatcher();
        Capture capture=new Capture();
        dispatcher.addMotionListener(capture);
        try {
            DesktopInput input=new DesktopInput(960,640);
            input.movementKey(GLFW_KEY_A,true);capture.drain();capture.movement(130);
            int beforeAlias=capture.events.size();
            input.movementKey(GLFW_KEY_LEFT,true);capture.drain();
            require(capture.events.size()==beforeAlias,"A/Left aliases must not consume a second pointer");
            input.mouse(true,800,450);capture.drain();capture.mouseUnchanged();capture.movement(130);
            input.key(GLFW_KEY_UNKNOWN,GLFW_RELEASE);capture.drain();capture.mouseUnchanged();

            int beforeSwitch=capture.events.size();
            input.movementKey(GLFW_KEY_D,true);capture.drain();capture.mouseUnchanged();capture.movement(370);
            assertRestart(capture.events.subList(beforeSwitch,capture.events.size()),370);
            int beforeFallback=capture.events.size();
            input.key(GLFW_KEY_D,GLFW_RELEASE);capture.drain();capture.mouseUnchanged();capture.movement(130);
            assertRestart(capture.events.subList(beforeFallback,capture.events.size()),130);
            int beforeAliasRelease=capture.events.size();
            input.key(GLFW_KEY_A,GLFW_RELEASE);capture.drain();capture.movement(130);
            require(capture.events.size()==beforeAliasRelease,"Releasing one alias must not stop another held alias");
            input.key(GLFW_KEY_LEFT,GLFW_RELEASE);capture.drain();capture.mouseUnchanged();
            require(capture.active.size()==1,"Final direction release leaves the action held");
            input.mouse(false,800,450);capture.drain();require(capture.active.isEmpty(),"All contacts released");

            // The action may start first; movement must still be available.
            input.mouse(true,800,450);input.movementKey(GLFW_KEY_RIGHT,true);capture.drain();
            capture.mouseUnchanged();capture.movement(370);
            int beforeClear=capture.events.size();
            input.clear(); // Must dispatch immediately, before the renderer is paused.
            require(capture.active.isEmpty(),"Focus loss releases both contacts synchronously");
            for(MotionEvent event:capture.events.subList(beforeClear,capture.events.size())) {
                for(int i=0;i<event.getPointerCount();i++)require(event.getX(i)<0&&event.getY(i)<0,
                    "Focus cleanup releases outside buttons instead of activating a click");
            }
            int afterClear=capture.events.size();
            input.key(GLFW_KEY_RIGHT,GLFW_RELEASE);input.mouse(false,800,450);capture.drain();
            require(capture.events.size()==afterClear,"Late physical releases cannot resurrect cleared contacts");
            input.movementKey(GLFW_KEY_A,true);capture.drain();capture.movement(130);
            int beforeRepeat=capture.events.size();
            input.movementKey(GLFW_KEY_A,true);capture.drain();
            require(capture.events.size()==beforeRepeat,"Repeated key press must not restart a held movement");
            input.clear();require(capture.active.isEmpty(),"Input works again after focus cleanup");
            System.out.println("DesktopInputTest passed: aliases, movement/action coexistence, fresh-DOWN fallback, focus cleanup; "+capture.events.size()+" actual dispatcher events");
        } finally { dispatcher.removeMotionListener(capture); }
    }
    private static void assertRestart(List<MotionEvent> events,float expectedX) {
        int release=-1,begin=-1;
        for(int i=0;i<events.size();i++) {
            MotionEvent event=events.get(i);
            int action=event.getActionMasked();
            require(event.getPointerId(event.getActionIndex())!=0||action==MotionEvent.ACTION_MOVE,
                "Direction change must not lift or restart the held action");
            if(action==MotionEvent.ACTION_POINTER_UP)release=i;
            if(action==MotionEvent.ACTION_POINTER_DOWN) {
                begin=i;require(event.getX(event.getActionIndex())==expectedX,"Fresh DOWN targets the desired direction");
            }
        }
        require(release>=0&&begin>release,"Direction changes require release then fresh DOWN, not MOVE alone");
    }
}
