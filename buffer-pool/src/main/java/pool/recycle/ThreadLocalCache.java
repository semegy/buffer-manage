package pool.recycle;

import pool.PoolArena;
import pool.PoolArena.SizeClass;
import pool.PoolChunk;
import pool.PooledByteBuf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ThreadLocalCache {

    public final PoolArena<ByteBuffer> directArena;
    private MemoryRegionCache<ByteBuffer>[] normalDirectCaches;

    private MemoryRegionCache<ByteBuffer>[] smallSubPageDirectCaches;
    private int allocations;
    private final int freeSweepAllocationThreshold;

    public ThreadLocalCache(PoolArena<ByteBuffer> directArena,
                            int smallCacheSize, int normalCacheSize, int maxCachedBufferCapacity,
                            int freeSweepAllocationThreshold) {
        this.freeSweepAllocationThreshold = freeSweepAllocationThreshold;
        this.directArena = directArena;
        smallSubPageDirectCaches = createSubPageCaches(
                smallCacheSize, directArena.numSmallSubpagePools);
        normalDirectCaches = createNormalCaches(
                normalCacheSize, maxCachedBufferCapacity, directArena);
        directArena.numThreadCaches.getAndIncrement();
    }

    private static <T> MemoryRegionCache<T>[] createSubPageCaches(
            int cacheSize, int numCaches) {
        if (cacheSize > 0 && numCaches > 0) {
            @SuppressWarnings("unchecked")
            MemoryRegionCache<T>[] cache = new MemoryRegionCache[numCaches];
            for (int i = 0; i < cache.length; i++) {
                // TODO: maybe use cacheSize / cache.length
                cache[i] = new SubPageMemoryRegionCache<T>(cacheSize);
            }
            return cache;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> MemoryRegionCache<T>[] createNormalCaches(
            int cacheSize, int maxCachedBufferCapacity, PoolArena<T> area) {
        if (cacheSize > 0 && maxCachedBufferCapacity > 0) {
            int max = Math.min(area.chunkSize, maxCachedBufferCapacity);
            // Create as many normal caches as we support based on how many sizeIdx we have and what the upper
            // bound is that we want to cache in general.
            List<MemoryRegionCache<T>> cache = new ArrayList<MemoryRegionCache<T>>();
            for (int idx = area.numSmallSubpagePools; idx < area.nSizes && area.sizeIdx2size(idx) <= max; idx++) {
                cache.add(new NormalMemoryRegionCache<T>(cacheSize));
            }
            return cache.toArray(new MemoryRegionCache[0]);
        } else {
            return null;
        }
    }


    public boolean allocateNormal(PoolArena area, PooledByteBuf buf, int sizeIdx) {
        // 调整大小索引，以区分小页池之外的索引，主要用于计算在特定内存类型中的实际索引位置。
        int idx = sizeIdx - area.numSmallSubpagePools;
        // 如果区域设置为直接内存，则从直接内存缓存数组中获取缓存。
        MemoryRegionCache<ByteBuffer> cache = cache(normalDirectCaches, idx);
        return allocate(cache, buf, idx);
    }

    public <T> boolean add(PoolArena arena, PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, int normCapacity, SizeClass sizeClass) {
        int sizeIdx = arena.size2SizeIdx(normCapacity);
        MemoryRegionCache<?> cache = cache(arena, sizeIdx, sizeClass);
        if (cache == null) {
            return false;
        }
        return cache.add(chunk, nioBuffer, handle, normCapacity);
    }

    /**
     * Cache used for buffers which are backed by NORMAL size.
     */
    public static final class NormalMemoryRegionCache<T> extends MemoryRegionCache<T> {
        NormalMemoryRegionCache(int size) {
            super(size, SizeClass.Normal);
        }

        @Override
        protected void initBuf(
                PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, PooledByteBuf<T> buf, int reqCapacity,
                ThreadLocalCache threadCache) {
            chunk.initBuf(buf, nioBuffer, handle, reqCapacity, threadCache);
        }

    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean allocate(MemoryRegionCache<?> cache, PooledByteBuf buf, int reqCapacity) {
        if (cache == null) {
            // no cache found so just return false here
            return false;
        }
        // 在缓存中分配，有这个大小的缓存size
        boolean allocated = cache.allocate(buf, reqCapacity, this);
        if (++allocations >= freeSweepAllocationThreshold) {
            allocations = 0;
            trim();
        }
        return allocated;
    }

    void trim() {
        trim(normalDirectCaches);
    }

    private static void trim(MemoryRegionCache<?>[] caches) {
        if (caches == null) {
            return;
        }
        for (MemoryRegionCache<?> c : caches) {
            trim(c);
        }
    }

    private static void trim(MemoryRegionCache<?> cache) {
        if (cache == null) {
            return;
        }
        cache.trim();
    }

    private <T> MemoryRegionCache<T> cache(MemoryRegionCache<T>[] cache, int idx) {
        if (cache == null || idx > cache.length - 1) {
            return null;
        }
        return cache[idx];
    }

    private MemoryRegionCache<?> cache(PoolArena<?> area, int sizeIdx, SizeClass sizeClass) {
        switch (sizeClass) {
            case Normal:
                return cacheForNormal(area, sizeIdx);
            case Small:
//                return cacheForSmall(area, sizeIdx);
            default:
                throw new Error();
        }
    }

    private MemoryRegionCache<?> cacheForNormal(PoolArena<?> area, int sizeIdx) {
        // 调整大小索引，以区分小页池之外的索引，主要用于计算在特定内存类型中的实际索引位置。
        int idx = sizeIdx - area.numSmallSubpagePools;
        // 如果区域设置为直接内存，则从直接内存缓存数组中获取缓存。
        return cache(normalDirectCaches, idx);
    }

    static final class Entry<T> {
        final Recycler.Handle<Entry<?>> recyclerHandle;
        PoolChunk<T> chunk;
        ByteBuffer nioBuffer;
        long handle = -1;
        int normCapacity;

        Entry(Recycler.Handle<Entry<?>> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
        }

        void recycle() {
            chunk = null;
            nioBuffer = null;
            handle = -1;
            recyclerHandle.recycle(this);
        }
    }

    public abstract static class MemoryRegionCache<T> {

        private final int size;
        private final Queue<Entry<T>> queue;
        private final PoolArena.SizeClass sizeClass;
        private int allocations;

        MemoryRegionCache(int size, SizeClass sizeClass) {
            this.size = safeFindNextPositivePowerOfTwo(size);
            queue = new LinkedList<>();
            this.sizeClass = sizeClass;
        }

        public static int safeFindNextPositivePowerOfTwo(final int value) {
            return value <= 0 ? 1 : value >= 0x40000000 ? 0x40000000 : findNextPositivePowerOfTwo(value);
        }

        public static int findNextPositivePowerOfTwo(final int value) {
            assert value > Integer.MIN_VALUE && value < 0x40000000;
            return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
        }

        public boolean allocate(PooledByteBuf buf, int reqCapacity, ThreadLocalCache cache) {
            // 缓存队列中获取
            Entry<T> entry = queue.poll();
            if (entry == null) {
                // 没有则放回内存池中不存在
                return false;
            }
            // 有则直接使用entry的内存
            initBuf(entry.chunk, entry.nioBuffer, entry.handle, buf, reqCapacity, cache);
            entry.recycle();
            // allocations is not thread-safe which is fine as this is only called from the same thread all time.
            ++allocations;
            return true;
        }

        /**
         * Init the {@link PooledByteBuf} using the provided chunk and handle with the capacity restrictions.
         */
        protected abstract void initBuf(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle,
                                        PooledByteBuf<T> buf, int reqCapacity, ThreadLocalCache threadCache);

        public void trim() {
            int free = size - allocations;
            allocations = 0;

            // We not even allocated all the number that are
            if (free > 0) {
                free(free, false);
            }
        }

        private int free(int max, boolean finalizer) {
            int numFreed = 0;
            for (; numFreed < max; numFreed++) {
                Entry<T> entry = queue.poll();
                if (entry != null) {
                    freeEntry(entry, finalizer);
                } else {
                    // all cleared
                    return numFreed;
                }
            }
            return numFreed;
        }

        private void freeEntry(Entry entry, boolean finalizer) {
            // Capture entry state before we recycle the entry object.
            PoolChunk chunk = entry.chunk;
            long handle = entry.handle;
            ByteBuffer nioBuffer = entry.nioBuffer;
            int normCapacity = entry.normCapacity;

            if (!finalizer) {
                // recycle now so PoolChunk can be GC'ed. This will only be done if this is not freed because of
                // a finalizer.
                entry.recycle();
            }

            chunk.arena.freeChunk(chunk, handle, normCapacity, sizeClass, nioBuffer, finalizer);
        }
        public final boolean add(PoolChunk<?> chunk, ByteBuffer nioBuffer, long handle, int normCapacity) {
            Entry<T> entry = newEntry(chunk, nioBuffer, handle, normCapacity);
            boolean queued = queue.offer(entry);
            if (!queued) {
                // 添加已满 entry
                // If it was not possible to cache the chunk, immediately recycle the entry
                entry.recycle();
            }
            return queued;
        }

        @SuppressWarnings("rawtypes")
        private static Entry newEntry(PoolChunk<?> chunk, ByteBuffer nioBuffer, long handle, int normCapacity) {
            Entry entry = RECYCLER.get();
            entry.chunk = chunk;
            entry.nioBuffer = nioBuffer;
            entry.handle = handle;
            entry.normCapacity = normCapacity;
            return entry;
        }

        @SuppressWarnings("rawtypes")
        private static final Recycler<Entry> RECYCLER = new Recycler<Entry>() {
            @Override
            protected Entry newObject(Handle handle) {
                return new Entry(handle);
            }
        };
    }

    public static final class SubPageMemoryRegionCache<T> extends MemoryRegionCache<T> {


        SubPageMemoryRegionCache(int size) {
            super(size, SizeClass.Small);
        }

        @Override
        protected void initBuf(PoolChunk<T> chunk, ByteBuffer nioBuffer, long handle, PooledByteBuf<T> buf, int reqCapacity, ThreadLocalCache threadCache) {

        }
    }
}
