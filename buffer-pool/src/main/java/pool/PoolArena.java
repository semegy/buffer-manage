package pool;

import pool.recycle.Recycler;
import pool.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static pool.PoolChunk.isSubpage;

public class PoolArena<T> extends SizeClasses {

    public AtomicInteger numThreadCaches = new AtomicInteger();

    private int deallocationsNormal;
    private int deallocationsSmall;

    final PooledBufferAllocate parent;

    private final PoolChunkList<T> q050;
    private final PoolChunkList<T> q025;
    private final PoolChunkList<T> q000;
    private final PoolChunkList<T> qInit;
    private final PoolChunkList<T> q075;
    private final PoolChunkList<T> q100;

    public <T> void free(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, int normCapacity, ThreadLocalCache cache) {
        SizeClass sizeClass = sizeClass(handle);
        if (cache != null && cache.add(this, chunk, nioBuffer, handle, normCapacity, sizeClass)) {
            return;
        }
        // 释放chunk空间
        freeChunk(chunk, handle, normCapacity, sizeClass, nioBuffer, false);
    }

    private SizeClass sizeClass(long handle) {
        return isSubpage(handle) ? SizeClass.Small : SizeClass.Normal;
    }

    public enum SizeClass {
        Small,
        Normal
    }

    public Recycler<PooledByteBuf<T>> recycler = new Recycler<PooledByteBuf<T>>() {
        protected PooledByteBuf<T> newObject(Recycler.Handle handle) {
            return new PooledByteBuf<T>(handle);
        }
    };


    final int nPSizes;
    public int numSmallSubpagePools;
    PoolSubpage<T>[] smallSubpagePools;

    public PoolArena(int pageSize, int pageShifts, int chunkSize, PooledBufferAllocate parent, int cacheAlignment) {
        super(pageSize, pageShifts, chunkSize, cacheAlignment);
        this.nPSizes = 40;
        numSmallSubpagePools = nSubpages;
        smallSubpagePools = newSubpagePoolArray(nSubpages);
        this.parent = parent;

        q100 = new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE, chunkSize);
        q075 = new PoolChunkList<T>(this, q100, 75, 100, chunkSize);
        q050 = new PoolChunkList<T>(this, q075, 50, 100, chunkSize);
        q025 = new PoolChunkList<T>(this, q050, 25, 75, chunkSize);
        q000 = new PoolChunkList<T>(this, q025, 1, 50, chunkSize);
        qInit = new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        q000.prevList(null);
        qInit.prevList(qInit);
    }

    private PoolSubpage<T> newSubpagePoolHead() {
        PoolSubpage<T> head = new PoolSubpage<T>();
        head.prev = head;
        head.next = head;
        return head;
    }

    private PoolSubpage<T>[] newSubpagePoolArray(int nSubpages) {
        PoolSubpage[] poolSubpages = new PoolSubpage[nSubpages];
        for (int i = 0; i < nSubpages; i++) {
            poolSubpages[i] = newSubpagePoolHead();
        }
        return poolSubpages;
    }

    public ByteBuf allocate(ThreadLocalCache cache, int reqCapacity, int maxCapacity) {
        // 先从缓存池中获取到合适容量的PooledByteBuf, 没有则创建新的buf
        PooledByteBuf<T> buf = newInstance(maxCapacity);
        // buf分配空间
        allocate(cache, buf, reqCapacity);
        return buf;
    }

    private void allocate(ThreadLocalCache cache, PooledByteBuf<T> buf, int reqCapacity) {
        // 原理 根据请求的空间大小找到合适sizeIdx （寻址索引）
        final int sizeIdx = size2SizeIdx(reqCapacity);
        allocateNormal(cache, buf, reqCapacity, sizeIdx);
    }

    private PooledByteBuf<T> newInstance(int maxCapacity) {
        PooledByteBuf buf = recycler.get();
        buf.reused(maxCapacity);
        return buf;
    }

    private void allocateNormal(ThreadLocalCache cache, PooledByteBuf<T> buf, int reqCapacity, int sizeIdx) {
        // 缓存中取到可用的buf
        if (cache.allocateCacheNormal(this, buf, sizeIdx)) {
            return;
        }

        int runSize = sizeIdx2size(sizeIdx);
        synchronized (this) {
            // 挨个从不同使用率的chunkList中获取normal大小的内存空间，获取不到则重新建立一个新chunk分配内存
            if (q050.allocate(buf, reqCapacity, runSize, cache) ||
                    q025.allocate(buf, reqCapacity, runSize, cache) ||
                    q000.allocate(buf, reqCapacity, runSize, cache) ||
                    qInit.allocate(buf, reqCapacity, runSize, cache) ||
                    q075.allocate(buf, reqCapacity, runSize, cache)) {
                return;
            }
            // Add a new chunk.
            PoolChunk<T> poolChunk = newChunk(pageSize, nPSizes, pageShifts, chunkSize);
            poolChunk.allocate(buf, reqCapacity, runSize, cache);
            qInit.add(poolChunk);
        }
    }

    private PoolChunk<T> newChunk(int pageSize, int nPSizes, int pageShifts, int chunkSize) {
        if (directMemoryCacheAlignment == 0) {
            ByteBuffer memory = ByteBuffer.allocateDirect(chunkSize);
            return new PoolChunk(this, memory, memory, pageSize, pageShifts,
                    chunkSize, nPSizes);
        } else {
            ByteBuffer memory = ByteBuffer.allocateDirect(chunkSize);
            ByteBuffer base = ByteBuffer.allocateDirect(chunkSize);
            return new PoolChunk(this, base, memory, pageSize,
                    pageShifts, chunkSize, nPSizes);
        }
    }

    public void freeChunk(PoolChunk chunk, long handle, int normCapacity, SizeClass sizeClass, ByteBuffer nioBuffer, boolean finalizer) {
        final boolean destroyChunk;
        synchronized (this) {
            // We only call this if freeChunk is not called because of the PoolThreadCache finalizer as otherwise this
            // may fail due lazy class-loading in for example tomcat.
            if (!finalizer) {
                switch (sizeClass) {
                    case Normal:
                        ++deallocationsNormal;
                        break;
                    case Small:
                        ++deallocationsSmall;
                        break;
                    default:
                        throw new Error();
                }
            }
            // 销毁chunk?
            destroyChunk = !chunk.parent.free(chunk, handle, normCapacity, nioBuffer);
        }
//        if (destroyChunk) {
//            // destroyChunk not need to be called while holding the synchronized lock.
//            // 销毁chunk,创建新的PoolChunk
//            // todo
////            destroyChunk(chunk);
//        }

    }


}
