import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.cocos2d.opengl.GLResourceHelper;

/** Regression for the original inUpdate race that let a JVM finalizer invoke native GL. */
public final class GLResourceThreadProbe {
    public static void main(String[] args) throws Exception {
        GLResourceHelper helper=new GLResourceHelper();
        Thread owner=Thread.currentThread();
        AtomicInteger calls=new AtomicInteger();
        AtomicReference<Thread> executedOn=new AtomicReference<>();
        helper.setInUpdate(true);
        Thread finalizer=new Thread(()->helper.perform(gl->{executedOn.set(Thread.currentThread());calls.incrementAndGet();}),"simulated-finalizer");
        finalizer.start();finalizer.join();
        if(calls.get()!=0)throw new AssertionError("Cleanup ran immediately on non-context thread");
        helper.update(null);
        if(calls.get()!=1||executedOn.get()!=owner)throw new AssertionError("Cleanup was not drained on the render owner");
        AtomicReference<Throwable> rejected=new AtomicReference<>();
        Thread wrongDrain=new Thread(()->{try {helper.update(null);}catch(Throwable failure){rejected.set(failure);}});
        wrongDrain.start();wrongDrain.join();
        if(!(rejected.get() instanceof IllegalStateException))throw new AssertionError("Foreign drain accepted");
        helper.setInUpdate(false);
        helper.perform(gl->calls.incrementAndGet());
        if(calls.get()!=1)throw new AssertionError("Out-of-frame cleanup ran immediately");
        helper.update(null);
        if(calls.get()!=2)throw new AssertionError("Out-of-frame cleanup lost");
        System.out.println("PASS: resource cleanup queues across threads, drains only on context owner");
    }
}
