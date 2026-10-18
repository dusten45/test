package paladog.linux;

import android.os.Handler;
import android.view.Display;
import com.Paladog.KorGG.Paladog;
import com.common.AppDelegate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.cocos2d.opengl.GLSurfaceView;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.BufferUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.Security;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Linux desktop entry point. Runs the original converted game on the JVM. */
public final class Main {
    public static final class GameActivity extends Paladog {
        public void create() { super.onCreate(null); }
        public void resumeGame() { super.onResume(); }
        public void pauseGame() { super.onPause(); }
    }
    public static void main(String[] args) throws Exception {
        double limit=0;String snapshot=null;
        for(int i=0;i<args.length;i++) {
            switch(args[i]) {
                case "--mute" -> System.setProperty("paladog.audio","off");
                case "--data-dir" -> System.setProperty("paladog.data",args[++i]);
                case "--assets" -> System.setProperty("paladog.assets",args[++i]);
                case "--max-seconds" -> limit=Double.parseDouble(args[++i]);
                case "--screenshot" -> snapshot=args[++i];
                case "--trace" -> System.setProperty("paladog.trace","true");
                case "--help" -> { System.out.println("Paladog Linux\nMouse: menus and game controls\nA/D or arrows: movement; 1-9: units from left to right\nDestiny: 1-5 select items from the bottom of the stack\nFive-lane mode: unit number, then lane 1-5 from top to bottom (11, 12, 35)\nQ/W/E: magic; Space: jump; Esc: game back/quit\nOptions: --mute --data-dir PATH --assets APK-DIRECTORY\nTest options: --max-seconds N --screenshot PNG --trace");return; }
                default -> throw new IllegalArgumentException("Unknown option: "+args[i]);
            }
        }
        if(!System.getProperty("os.name").equals("Linux"))throw new UnsupportedOperationException("This build targets Linux.");
        Path data=android.content.Context.dataPath();
        Files.createDirectories(data);
        try(FileChannel channel=FileChannel.open(data.resolve(".instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            FileLock lock=channel.tryLock()) {
            if(lock==null)throw new IllegalStateException("This Paladog save directory is already open in another process.");
            runGame(limit,snapshot);
        }
        System.exit(0);
    }
    private static void runGame(double limit,String snapshot) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        android.content.Context.registerSystemService("vibrator",new android.os.Vibrator());
        String fontPath=System.getProperty("paladog.font");
        if(fontPath!=null) {
            java.awt.Font font=java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,Path.of(fontPath).toFile()).deriveFont(15f);
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            for(String key:new String[]{"Label.font","Button.font","TextArea.font","OptionPane.messageFont","OptionPane.buttonFont"})
                javax.swing.UIManager.put(key,font);
        }
        GLFWErrorCallback.createPrint(System.err).set();
        // The first Linux backend uses X11 (including XWayland). Local Swing
        // dialogs and the game window therefore use the same display server.
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);
        if(!glfwInit())throw new IllegalStateException("GLFW could not open a Linux display. Run in a desktop session.");
        long window=0;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,2);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,1);
            glfwWindowHint(GLFW_RESIZABLE,GLFW_FALSE);
            window=glfwCreateWindow(Display.width,Display.height,"Paladog — Linux",NULL,NULL);
            if(window==NULL)throw new IllegalStateException("OpenGL 2.1 compatibility context unavailable");
            glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(1);
            System.out.println("[desktop] OpenGL "+glGetString(GL_VERSION)+"; "+glGetString(GL_RENDERER));
            GameActivity activity=new GameActivity();
            activity.create();
            GLSurfaceView view=(GLSurfaceView)activity.getContentView();
            var renderer=view.getRenderer();
            var gl=GLBridge.create();
            renderer.onSurfaceCreated(gl,null);
            renderer.onSurfaceChanged(gl,Display.width,Display.height);
            activity.resumeGame();
            DesktopInput input=new DesktopInput(Display.width,Display.height);
            glfwSetCursorPosCallback(window,(w,x,y)->input.move((float)x,(float)y));
            glfwSetMouseButtonCallback(window,(w,button,action,mods)->{
                if(button!=GLFW_MOUSE_BUTTON_LEFT)return;
                double[] x={0},y={0};glfwGetCursorPos(w,x,y);
                input.mouse(action==GLFW_PRESS,(float)x[0],(float)y[0]);
            });
            glfwSetKeyCallback(window,(w,key,scancode,action,mods)->{
                if(key==GLFW_KEY_ESCAPE&&action==GLFW_PRESS) { input.clear();activity.onBackPressed(); }
                else input.key(key,action);
            });
            glfwSetWindowCloseCallback(window,w->{ glfwSetWindowShouldClose(w,false);input.clear();activity.onBackPressed(); });
            glfwSetWindowFocusCallback(window,(w,focused)->{
                if(!focused) { input.clear();activity.pauseGame(); } else activity.resumeGame();
            });
            long started=System.nanoTime(),lastTrace=0,frames=0;
            while(!activity.isFinishing()) {
                long frameStart=System.nanoTime();
                glfwPollEvents();Handler.pump();view.drainEvents();input.update();
                if(!view.isPaused())renderer.onDrawFrame(gl);
                boolean timeExpired=limit>0&&(frameStart-started)/1e9>=limit;
                if(timeExpired&&snapshot!=null)capture(snapshot,Display.width,Display.height);
                glfwSwapBuffers(window);frames++;
                if(Boolean.getBoolean("paladog.trace")&&frameStart-lastTrace>2_000_000_000L) {
                    trace(frames);lastTrace=frameStart;
                }
                if(timeExpired) {
                    activity.SaveDataFile();
                    System.out.println("[desktop] Timed test ended after "+frames+" frames; called original SaveDataFile");
                    break;
                }
                long remain=16_666_667L-(System.nanoTime()-frameStart);
                if(remain>0)Thread.sleep(remain/1_000_000L,(int)(remain%1_000_000L));
            }
        } finally {
            if(window!=0) { org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);glfwDestroyWindow(window); }
            glfwTerminate();
        }
    }
    private static void trace(long frame) {
        AppDelegate a=AppDelegate.sharedAppDelegate();
        if(a.g_GI==null)return;
        String hero=a.m_pGameManager==null||a.m_pGameManager.m_pPlayer==null?"none":
            a.m_pGameManager.m_pPlayer.m_vPos.x+":"+a.m_pGameManager.m_pPlayer.m_iState;
        System.out.println("[state] frame="+frame+" mode="+a.g_GI.gmGameMode+" layer="+a.g_GI.iCurLayer+" loaded="+a.g_bLoad+" step="+(a.m_pGameManager==null?-1:a.m_pGameManager.m_iGameStep)+" paused="+a.g_GI.bPause+" hero="+hero);
        if(a.m_pGameManager!=null&&a.m_pCharManager!=null&&a.m_pCharManager.m_pAllyList!=null) {
            StringBuilder allies=new StringBuilder();
            for(var unit:a.m_pCharManager.m_pAllyList)allies.append(unit.m_iID).append(':').append(unit.m_dwKind).append('@').append(unit.m_iLine).append(',');
            System.out.println("[controls] kind="+a.m_pGameManager.m_iGamePlayKind+" food="+a.m_pGameManager.m_fFoodCur+" tower="+a.g_GI.iTowerCreateUnitMode+" allies="+allies+" boxes="+java.util.Arrays.toString(a.g_GI.dwDestinyBox)+" order="+java.util.Arrays.toString(a.g_GI.iDestinyOrder));
        }
    }
    private static void capture(String file,int width,int height) throws Exception {
        ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);
        glReadBuffer(GL_BACK);glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int i=(y*width+x)*4;
            image.setRGB(x,height-y-1,0xff000000|((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255));
        }
        Path path=Path.of(file).toAbsolutePath();Files.createDirectories(path.getParent());ImageIO.write(image,"PNG",path.toFile());
    }
}
