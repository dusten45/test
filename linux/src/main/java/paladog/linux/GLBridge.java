package paladog.linux;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11ExtensionPack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;
import org.cocos2d.opengl.GLResourceHelper;

/** Maps the original OpenGL ES 1.x renderer to desktop OpenGL's compatibility profile. */
public final class GLBridge implements InvocationHandler {
    private final Map<String,Buffer> pointerBuffers=new HashMap<>();
    private FloatBuffer pointSizes;
    private int pointSizeStride=1;
    private boolean pointSizeArray;
    private final Thread contextThread=Thread.currentThread();
    private GLBridge() {}

    public static GL10 create() {
        return (GL10)Proxy.newProxyInstance(GLBridge.class.getClassLoader(),
            new Class<?>[]{javax.microedition.khronos.opengles.GL11.class, GL11ExtensionPack.class},new GLBridge());
    }

    @Override public Object invoke(Object proxy,Method method,Object[] args) {
        String name=method.getName();
        Object[] a=args == null ? new Object[0] : args;
        if(name.startsWith("gl") && Thread.currentThread()!=contextThread) {
            if(name.equals("glDeleteTextures")||name.equals("glDeleteBuffers")||name.equals("glDeleteFramebuffersOES")||name.equals("glDeleteRenderbuffersOES")) {
                // A few retained cocos2d finalizers bypass GLResourceHelper. Keep their cleanup on the context thread.
                int count=i(a,0);int[] ids=new int[count];
                for(int n=0;n<count;n++)ids[n]=a[1] instanceof int[] ? ((int[])a[1])[i(a,2)+n] : ((IntBuffer)a[1]).get(((IntBuffer)a[1]).position()+n);
                Object[] deletion={count,ids,0};
                GLResourceHelper.sharedHelper().perform(gl->invoke(proxy,method,deletion));
                return null;
            }
            throw new IllegalStateException("OpenGL call from non-context thread: "+Thread.currentThread().getName()+" "+name);
        }
        switch(name) {
            case "toString": return "Paladog desktop OpenGL bridge";
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy==a[0];
            case "glGetString": return GL11.glGetString(i(a,0));
            case "glGetError": return GL11.glGetError();
            case "glEnable": GL11.glEnable(i(a,0));break;
            case "glDisable": GL11.glDisable(i(a,0));break;
            case "glEnableClientState": if(i(a,0)==35740)pointSizeArray=true;else GL11.glEnableClientState(i(a,0));break;
            case "glDisableClientState": if(i(a,0)==35740)pointSizeArray=false;else GL11.glDisableClientState(i(a,0));break;
            case "glHint": GL11.glHint(i(a,0),i(a,1));break;
            case "glBlendFunc": GL11.glBlendFunc(i(a,0),i(a,1));break;
            case "glClear": GL11.glClear(i(a,0));break;
            case "glClearColor": GL11.glClearColor(f(a,0),f(a,1),f(a,2),f(a,3));break;
            case "glClearDepthf": GL11.glClearDepth(f(a,0));break;
            case "glClearStencil": GL11.glClearStencil(i(a,0));break;
            case "glColor4f": GL11.glColor4f(f(a,0),f(a,1),f(a,2),f(a,3));break;
            case "glColorMask": GL11.glColorMask(b(a,0),b(a,1),b(a,2),b(a,3));break;
            case "glDepthFunc": GL11.glDepthFunc(i(a,0));break;
            case "glDepthMask": GL11.glDepthMask(b(a,0));break;
            case "glDepthRangef": GL11.glDepthRange(f(a,0),f(a,1));break;
            case "glAlphaFunc": GL11.glAlphaFunc(i(a,0),f(a,1));break;
            case "glMatrixMode": GL11.glMatrixMode(i(a,0));break;
            case "glLoadIdentity": GL11.glLoadIdentity();break;
            case "glPushMatrix": GL11.glPushMatrix();break;
            case "glPopMatrix": GL11.glPopMatrix();break;
            case "glLoadMatrixf": GL11.glLoadMatrixf(matrix(a));break;
            case "glMultMatrixf": GL11.glMultMatrixf(matrix(a));break;
            case "glTranslatef": GL11.glTranslatef(f(a,0),f(a,1),f(a,2));break;
            case "glRotatef": GL11.glRotatef(f(a,0),f(a,1),f(a,2),f(a,3));break;
            case "glScalef": GL11.glScalef(f(a,0),f(a,1),f(a,2));break;
            case "glOrthof": GL11.glOrtho(f(a,0),f(a,1),f(a,2),f(a,3),f(a,4),f(a,5));break;
            case "glFrustumf": GL11.glFrustum(f(a,0),f(a,1),f(a,2),f(a,3),f(a,4),f(a,5));break;
            case "glViewport": GL11.glViewport(i(a,0),i(a,1),i(a,2),i(a,3));break;
            case "glScissor": GL11.glScissor(i(a,0),i(a,1),i(a,2),i(a,3));break;
            case "glBindTexture": GL11.glBindTexture(i(a,0),i(a,1));break;
            // ES glTexParameterx receives enum values directly for the game's filters/wrap modes.
            case "glTexParameterx": case "glTexParameteri": GL11.glTexParameteri(i(a,0),i(a,1),i(a,2));break;
            case "glTexParameterf": GL11.glTexParameterf(i(a,0),i(a,1),f(a,2));break;
            case "glTexEnvi": case "glTexEnvx": GL11.glTexEnvi(i(a,0),i(a,1),i(a,2));break;
            case "glTexEnvf": GL11.glTexEnvf(i(a,0),i(a,1),f(a,2));break;
            case "glActiveTexture": GL13.glActiveTexture(i(a,0));break;
            case "glClientActiveTexture": GL13.glClientActiveTexture(i(a,0));break;
            case "glPixelStorei": GL11.glPixelStorei(i(a,0),i(a,1));break;
            case "glGenTextures": ids(a,0,true);break;
            case "glDeleteTextures": ids(a,0,false);break;
            case "glGenBuffers": ids(a,1,true);break;
            case "glDeleteBuffers": ids(a,1,false);break;
            case "glGenFramebuffersOES": ids(a,2,true);break;
            case "glDeleteFramebuffersOES": ids(a,2,false);break;
            case "glGenRenderbuffersOES": ids(a,3,true);break;
            case "glDeleteRenderbuffersOES": ids(a,3,false);break;
            case "glBindBuffer": GL15.glBindBuffer(i(a,0),i(a,1));break;
            case "glBufferData": GL15.nglBufferData(i(a,0),i(a,1),address(a[2]),i(a,3));break;
            case "glBufferSubData": GL15.nglBufferSubData(i(a,0),i(a,1),i(a,2),address(a[3]));break;
            case "glBindFramebufferOES": EXTFramebufferObject.glBindFramebufferEXT(i(a,0),i(a,1));break;
            case "glBindRenderbufferOES": EXTFramebufferObject.glBindRenderbufferEXT(i(a,0),i(a,1));break;
            case "glFramebufferTexture2DOES": EXTFramebufferObject.glFramebufferTexture2DEXT(i(a,0),i(a,1),i(a,2),i(a,3),i(a,4));break;
            case "glFramebufferRenderbufferOES": EXTFramebufferObject.glFramebufferRenderbufferEXT(i(a,0),i(a,1),i(a,2),i(a,3));break;
            case "glRenderbufferStorageOES": EXTFramebufferObject.glRenderbufferStorageEXT(i(a,0),i(a,1),i(a,2),i(a,3));break;
            case "glCheckFramebufferStatusOES": return EXTFramebufferObject.glCheckFramebufferStatusEXT(i(a,0));
            case "glGenerateMipmapOES": EXTFramebufferObject.glGenerateMipmapEXT(i(a,0));break;
            case "glGetIntegerv": getIntegers(a);break;
            case "glVertexPointer": GL11.nglVertexPointer(i(a,0),i(a,1),i(a,2),pointer(name,a[3]));break;
            case "glColorPointer": GL11.nglColorPointer(i(a,0),i(a,1),i(a,2),pointer(name,a[3]));break;
            case "glTexCoordPointer": GL11.nglTexCoordPointer(i(a,0),i(a,1),i(a,2),pointer(name,a[3]));break;
            case "glNormalPointer": GL11.nglNormalPointer(i(a,0),i(a,1),pointer(name,a[2]));break;
            case "glPointSizePointerOES":
                if(i(a,0)!=GL11.GL_FLOAT)throw unsupported(method);
                Buffer sizes=(Buffer)a[2];
                pointSizes=sizes instanceof FloatBuffer ? ((FloatBuffer)sizes).duplicate() : ((ByteBuffer)sizes).duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
                pointSizeStride=i(a,1)==0 ? 1 : i(a,1)/4;
                break;
            case "glDrawArrays":
                if(i(a,0)==GL11.GL_POINTS && pointSizeArray && pointSizes!=null) {
                    // Desktop compatibility GL has no ES point-size client array. Preserve each particle size.
                    float old=GL11.glGetFloat(GL11.GL_POINT_SIZE);
                    for(int n=0;n<i(a,2);n++) { GL11.glPointSize(Math.max(1,pointSizes.get(pointSizes.position()+(i(a,1)+n)*pointSizeStride))); GL11.glDrawArrays(GL11.GL_POINTS,i(a,1)+n,1); }
                    GL11.glPointSize(old);
                } else GL11.glDrawArrays(i(a,0),i(a,1),i(a,2));
                break;
            case "glDrawElements": GL11.nglDrawElements(i(a,0),i(a,1),i(a,2),address(a[3]));break;
            case "glReadPixels": GL11.nglReadPixels(i(a,0),i(a,1),i(a,2),i(a,3),i(a,4),i(a,5),address(a[6]));break;
            case "glTexImage2D": GL11.nglTexImage2D(i(a,0),i(a,1),i(a,2),i(a,3),i(a,4),i(a,5),i(a,6),i(a,7),address(a[8]));break;
            case "glTexSubImage2D": GL11.nglTexSubImage2D(i(a,0),i(a,1),i(a,2),i(a,3),i(a,4),i(a,5),i(a,6),i(a,7),address(a[8]));break;
            case "glCompressedTexImage2D": GL13.nglCompressedTexImage2D(i(a,0),i(a,1),i(a,2),i(a,3),i(a,4),i(a,5),i(a,6),address(a[7]));break;
            case "glLineWidth": GL11.glLineWidth(f(a,0));break;
            case "glPointSize": GL11.glPointSize(f(a,0));break;
            case "glShadeModel": GL11.glShadeModel(i(a,0));break;
            case "glCullFace": GL11.glCullFace(i(a,0));break;
            case "glFrontFace": GL11.glFrontFace(i(a,0));break;
            case "glFinish": GL11.glFinish();break;
            case "glFlush": GL11.glFlush();break;
            default: throw unsupported(method);
        }
        return null;
    }

    private static UnsupportedOperationException unsupported(Method method) { return new UnsupportedOperationException("Unimplemented game OpenGL operation: "+method); }
    private static int i(Object[] a,int index) { return ((Number)a[index]).intValue(); }
    private static float f(Object[] a,int index) { return ((Number)a[index]).floatValue(); }
    private static boolean b(Object[] a,int index) { return (Boolean)a[index]; }
    private static FloatBuffer matrix(Object[] a) {
        if(a[0] instanceof FloatBuffer && ((FloatBuffer)a[0]).isDirect()) return (FloatBuffer)a[0];
        FloatBuffer result=BufferUtils.createFloatBuffer(16);
        if(a[0] instanceof float[])result.put((float[])a[0],i(a,1),16);
        else { FloatBuffer original=((FloatBuffer)a[0]).duplicate();original.limit(original.position()+16);result.put(original); }
        return result.flip();
    }
    private long pointer(String name,Object value) {
        if(value instanceof Buffer)pointerBuffers.put(name,(Buffer)value);
        else pointerBuffers.remove(name);
        return address(value);
    }
    private static long address(Object value) {
        if(value==null)return 0;
        if(value instanceof Number)return ((Number)value).longValue();
        Buffer buffer=(Buffer)value;
        if(!buffer.isDirect())throw new IllegalArgumentException("Non-direct GL client buffer: "+buffer.getClass());
        if(buffer instanceof ByteBuffer)return MemoryUtil.memAddress((ByteBuffer)buffer);
        if(buffer instanceof FloatBuffer)return MemoryUtil.memAddress((FloatBuffer)buffer);
        if(buffer instanceof ShortBuffer)return MemoryUtil.memAddress((ShortBuffer)buffer);
        if(buffer instanceof IntBuffer)return MemoryUtil.memAddress((IntBuffer)buffer);
        throw new IllegalArgumentException("Unsupported GL buffer: "+buffer.getClass());
    }
    private static void getIntegers(Object[] a) {
        IntBuffer values=BufferUtils.createIntBuffer(16);
        GL11.glGetIntegerv(i(a,0),values);
        int count=i(a,0)==GL11.GL_VIEWPORT || i(a,0)==GL11.GL_SCISSOR_BOX ? 4 : 1;
        if(a[1] instanceof int[])for(int n=0;n<count;n++)((int[])a[1])[i(a,2)+n]=values.get(n);
        else { IntBuffer out=(IntBuffer)a[1];for(int n=0;n<count;n++)out.put(out.position()+n,values.get(n)); }
    }
    private static void ids(Object[] a,int kind,boolean generate) {
        int count=i(a,0);
        for(int n=0;n<count;n++) {
            int index=a[1] instanceof int[] ? i(a,2)+n : ((IntBuffer)a[1]).position()+n;
            int value;
            if(generate) {
                value=kind==0 ? GL11.glGenTextures() : kind==1 ? GL15.glGenBuffers() : kind==2 ? EXTFramebufferObject.glGenFramebuffersEXT() : EXTFramebufferObject.glGenRenderbuffersEXT();
                if(a[1] instanceof int[])((int[])a[1])[index]=value;else ((IntBuffer)a[1]).put(index,value);
            } else {
                value=a[1] instanceof int[] ? ((int[])a[1])[index] : ((IntBuffer)a[1]).get(index);
                if(kind==0)GL11.glDeleteTextures(value);else if(kind==1)GL15.glDeleteBuffers(value);else if(kind==2)EXTFramebufferObject.glDeleteFramebuffersEXT(value);else EXTFramebufferObject.glDeleteRenderbuffersEXT(value);
            }
        }
    }
}
