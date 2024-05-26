package rpc.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class ThreadServiceExecutor extends AbstractExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(ThreadServiceExecutor.class.getName());
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue();
    private ExecutorService sharedExecutor;
    private CompletableFuture<?> waitingFuture;
    private boolean finished = false;
    private volatile boolean waiting = true;
    private final Object lock = new Object();

    public ThreadServiceExecutor(ExecutorService sharedExecutor) {
        this.sharedExecutor = sharedExecutor;
    }

    public CompletableFuture<?> getWaitingFuture() {
        return this.waitingFuture;
    }

    public void setWaitingFuture(CompletableFuture<?> waitingFuture) {
        this.waitingFuture = waitingFuture;
    }

    public boolean isWaiting() {
        return this.waiting;
    }

    public void waitAndDrain() throws InterruptedException {
        if (!this.finished) {
            Runnable runnable = (Runnable) this.queue.take();
            synchronized (this.lock) {
                this.waiting = false;
                runnable.run();
            }

            for (runnable = (Runnable) this.queue.poll(); runnable != null; runnable = (Runnable) this.queue.poll()) {
                try {
                    runnable.run();
                } catch (Throwable throwable) {
                    logger.info(throwable.getStackTrace().toString());
                }
            }

            this.finished = true;
        }
    }

    public long waitAndDrain(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    public void execute(Runnable runnable) {
        synchronized (this.lock) {
            if (!this.waiting) {
                this.sharedExecutor.execute(runnable);
            } else {
                this.queue.add(runnable);
            }

        }
    }

    public void notifyReturn(Throwable t) {
        this.execute(() -> {
            this.waitingFuture.completeExceptionally(t);
        });
    }

    public void shutdown() {
        this.shutdownNow();
    }

    public List<Runnable> shutdownNow() {
        this.notifyReturn(new IllegalStateException("Consumer is shutting down and this call is going to be stopped without receiving any result, usually this is called by a slow provider instance or bad service implementation."));
        return Collections.emptyList();
    }

    public boolean isShutdown() {
        return false;
    }

    public boolean isTerminated() {
        return false;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }
}
