package rpc.proxy;

import org.mockito.verification.Timeout;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.*;

public class DefaultFuture<T> extends CompletableFuture<T> {

    private ExecutorService executor;

    public Timeout timeoutCheckTask;
    private int requestStatus;

    private Object response;

    private long requestId;

    public static final Map<Long, DefaultFuture> FUTURES = new ConcurrentHashMap();
    private Thread responseTask;

    static Unsafe UNSAFE;

    static {
        try {
            Class<Unsafe> unsafeClass = Unsafe.class;
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public DefaultFuture(long requestId) {
        FUTURES.put(requestId, this);
    }

    private void setResponse(String s) {
        this.response = s;
    }

    private Object getResponse() {
        return response;
    }

    public Executor getExecutor() {
        return executor;
    }

    public long getId() {
        return requestId;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return super.get();
    }

    public void setResponseTask(Thread responseTask) {
        this.responseTask = responseTask;
    }

    public Thread getResponseTask() {
        return this.responseTask;
    }
}
