package org.cocos2d.opengl;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.khronos.opengles.GL10;
import org.cocos2d.nodes.CCDirector;

/** Original resource reload/cleanup queue, with explicit desktop OpenGL thread ownership. */
public class GLResourceHelper {
    private static final GLResourceHelper SHARED=new GLResourceHelper();
    private final ConcurrentLinkedQueue<GLResorceTask> taskQueue=new ConcurrentLinkedQueue<>();
    private final Map<Resource,GLResourceLoader> reloadMap=Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicBoolean reloadTaskIsInQueue=new AtomicBoolean();
    private volatile boolean inUpdate;
    private volatile Thread renderThread;

    public interface GLResorceTask { void perform(GL10 gl); }
    public interface GLResourceLoader { void load(Resource resource); }
    public interface Resource {}
    public static GLResourceHelper sharedHelper() { return SHARED; }
    public void addLoader(Resource resource,GLResourceLoader loader,boolean addTask) {
        if(addTask)perform(gl->{loader.load(resource);reloadMap.put(resource,loader);});
        else reloadMap.put(resource,loader);
    }
    public void reloadResources() {
        if(!reloadTaskIsInQueue.compareAndSet(false,true))return;
        taskQueue.add(gl->{
            try {
                synchronized(reloadMap) {
                    for(var entry:reloadMap.entrySet()) {
                        Resource resource=entry.getKey();
                        if(resource!=null)entry.getValue().load(resource);
                    }
                }
            } finally { reloadTaskIsInQueue.set(false); }
        });
    }
    public void perform(GLResorceTask task) {
        if(task==null)throw new IllegalArgumentException("Null GL resource task");
        // inUpdate alone is insufficient: JVM finalizers can run while a frame is being drawn.
        if(inUpdate && Thread.currentThread()==renderThread)task.perform(CCDirector.gl);
        else taskQueue.add(task);
    }
    private synchronized void claimRenderThread() {
        if(renderThread==null)renderThread=Thread.currentThread();
        else if(renderThread!=Thread.currentThread())throw new IllegalStateException("GL resource queue drained from a non-render thread");
    }
    public void update(GL10 gl) {
        claimRenderThread();
        GLResorceTask task;
        while((task=taskQueue.poll())!=null)task.perform(gl);
    }
    public void setInUpdate(boolean value) {
        claimRenderThread();
        inUpdate=value;
    }
}
