package pool.recycle;

import pool.PooledByteBuf;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public abstract class Recycler<T> {
    public interface Handle<T> {

        /**
         * Recycle the {@link Object} if possible and so make it ready to be reused.
         */
        void recycle(T self);
    }

    private static final Handle<?> NOOP_HANDLE = new Handle() {
        @Override
        public void recycle(Object self) {
            // 不做回收
        }
    };

    private static final class DefaultHandle<T> implements Handle<PooledByteBuf<T>> {
        private static final int STATE_CLAIMED = 0;
        private static final int STATE_AVAILABLE = 1;

        private volatile T value;

        private final LocalPool<T> localPool;

        private static final AtomicIntegerFieldUpdater<DefaultHandle> STATE_UPDATER;

        static {
            // 基于反射将state封装成AtomicIntegerFieldUpdater 实现 state的原子操作
            AtomicIntegerFieldUpdater<?> updater = AtomicIntegerFieldUpdater.newUpdater(DefaultHandle.class, "state");
            //noinspection unchecked
            STATE_UPDATER = (AtomicIntegerFieldUpdater<DefaultHandle>) updater;
        }

        public DefaultHandle(LocalPool<T> localPool) {
            this.localPool = localPool;
        }

        public void recycle(PooledByteBuf<T> self) {
            // 如果handler对象不是当前释放的对直接抛错
            if (self != value) {
                throw new IllegalArgumentException("object does not belong to handle");
            }
            // 插入队列，以便回收利用
            localPool.release(this);
        }

        public T get() {
            return this.value;
        }


        @SuppressWarnings({"FieldMayBeFinal", "unused"}) // Updated by STATE_UPDATER.
        private volatile int state; // State is initialised to STATE_CLAIMED (aka. 0) so they can be released.


        public boolean availableToClaim() {

            // 如果本次等于 STATE_AVAILABLE 1 则已经有人获取到了，直接进入下次循环，否则尝试获取
            if (state != STATE_AVAILABLE) {
                return false;
            }
            // 设置STATE_AVAILABLE 不可用状态、用于取到handle时跳出循环
            // state == STATE_AVAILABLE 返回true
            // 原子操作，意味着如果没有更新成功说明已被其它线程获取到，则进入循环再次尝试认领可用的handle
            // 继承了AtomicInteger，本质上包装了state。使得state具有原子更新的能力
            return STATE_UPDATER.compareAndSet(this, STATE_AVAILABLE, STATE_CLAIMED);
        }


        /**
         * 设置空闲可用
         */
        void toAvailable() {
            // 将状态设置为AVAILABLE:可用
            int prev = STATE_UPDATER.getAndSet(this, STATE_AVAILABLE);
            if (prev == STATE_AVAILABLE) {
                // 如果之前可用，说明对象池中已有对象，无需再次释放
                throw new IllegalStateException("Object has been recycled already.");
            }
        }

        public void set(T obj) {
            this.value = obj;
        }
    }


    private int maxCapacityPerThread = 8;

    private final ThreadLocal<LocalPool<T>> threadLocal = new ThreadLocal<>();

    public T get() {
        if (maxCapacityPerThread == 0) {
            // 直接新建一个对象，维护一个无回收HANDLE对象
            return newObject(NOOP_HANDLE);
        }
        LocalPool<T> localPool = threadLocal.get();
        if (localPool == null) {
            localPool = new LocalPool<>();
            threadLocal.set(localPool);
        }
        // 认领可用的handle
        DefaultHandle<T> handle = localPool.claim();
        if (handle == null) {
            // 视情况生成handle，默认每8次生成可回收对象的handle
            handle = localPool.newHandle();
            if (handle != null) {
                // 不为空时 new 对象
                T obj = newObject(handle);
                // handle 维护对象
                handle.set(obj);
                // handle 维护对象
                return obj;
            } else {
                // 否则 直接new对象，维护一个无回收HANDLE对象
                return newObject(NOOP_HANDLE);
            }
        } else {
            return handle.get();
        }
    }

    private static final class LocalPool<T> {

        // 存放对象handler地方
        private volatile LinkedList<DefaultHandle<T>> pooledHandles = new LinkedList<>();
        private int ratioCounter;
        private final int ratioInterval = 8;

        public DefaultHandle<T> claim() {
            LinkedList<DefaultHandle<T>> handles = pooledHandles;
            if (handles == null) {
                return null;
            }
            DefaultHandle<T> handle;
            do {
                // 调用底层UnSafe类队列操作，取出localPool中的一个handle
                handle = handles.poll();
            } while (handle != null && !handle.availableToClaim());
            // 循环的条件是 handle 不为null 且 (handler被占用) 直至取出可用的handler 或者 localPool没有对象了
            // 循环结束后从对象池中获取可用的对象
            return handle;
        }

        public DefaultHandle<T> newHandle() {
            // 超过ratioInterval则生成DefaultHandle，防止回收频率过快
            if (++ratioCounter >= ratioInterval) {
                ratioCounter = 0;
                // 把对象池赋予对象处理器
                return new DefaultHandle(this);
            }
            return null;
        }

        public void release(DefaultHandle<T> handle) {
            // 设置handler可用，并校验对象是否已经存在
            handle.toAvailable();
            pooledHandles.offer(handle);
        }
    }

    protected abstract T newObject(Handle noopHandle);

}
