package pool;

import pool.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;

public class PooledBufferAllocate implements BufferAllocate {

    private int nextReceiveBufferSize = 1024;

    public static final int DEFAULT_MAX_CACHED_BYTEBUFFERS_PER_CHUNK = 1023;

    public static final int CACHE_NOT_USED = 0;

    static final int DEFAULT_MAX_CACHED_BUFFER_CAPACITY;

    private static final int DEFAULT_CACHE_TRIM_INTERVAL;

    private static final int MIN_PAGE_SIZE = 4096;

    private static final int DEFAULT_PAGE_SIZE;
    private static final int DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT;
    private static final int MAX_CHUNK_SIZE = (int) (((long) Integer.MAX_VALUE + 1) / 2);

    private static final int DEFAULT_MAX_ORDER; // 8192 << 9 = 4 MiB per chunk
    private static final int DEFAULT_SMALL_CACHE_SIZE = 256;
    private final int smallCacheSize;

    private final int normalCacheSize;

    int chunkSize;
    private final PoolArena<ByteBuffer>[] directArenas;

    private final PoolThreadLocalCache threadCache;

    private Handle handle = new Handle() {
        @Override
        public ByteBuf allocate() {
            return ioBuffer(guess());
        }

        @Override
        public int guess() {
            return nextReceiveBufferSize;
        }

        @Override
        public void incMessagesRead(int numMessages) {

        }

        @Override
        public void lastBytesRead(int bytes) {

        }

        @Override
        public int lastBytesRead() {
            return 0;
        }

        @Override
        public void attemptedBytesRead(int bytes) {

        }

        @Override
        public int attemptedBytesRead() {
            return 0;
        }

        @Override
        public boolean continueReading() {
            return false;
        }

        @Override
        public void readComplete() {

        }
    };

    @Override
    public Handle newHandle() {
        return null;
    }

    private final class PoolThreadLocalCache extends ThreadLocal<ThreadLocalCache> {
        private final boolean useCacheForAllThreads;

        PoolThreadLocalCache(boolean useCacheForAllThreads) {
            this.useCacheForAllThreads = useCacheForAllThreads;
        }

        /**
         * 初始化方法，用于创建并返回一个线程缓存对象。该方法是同步的，保证线程安全。
         * 判断当前线程是否应该使用缓存，如果是，则创建并返回一个包含缓存的PoolThreadCache对象；
         * 如果不是，则返回一个不包含缓存的PoolThreadCache对象。
         *
         * @return PoolThreadCache 线程缓存对象，可能包含或不包含缓存。
         */
        @Override
        protected synchronized ThreadLocalCache initialValue() {
            // 获取当前最少使用的directArena
            final PoolArena<ByteBuffer> directArena = leastUsedArena(directArenas);

            // 判断当前线程是否应该使用缓存
            final ThreadLocalCache cache = new ThreadLocalCache(directArena, smallCacheSize, normalCacheSize,
                    DEFAULT_MAX_CACHED_BUFFER_CAPACITY, DEFAULT_CACHE_TRIM_INTERVAL);

            return cache;
        }

        /**
         * 查找并返回使用最少的PoolArena。
         *
         * @param arenas PoolArena数组，代表多个可供选择的PoolArena实例。
         * @return 使用最少的PoolArena实例；如果输入为null或空数组，则返回null。
         */
        private <T> PoolArena<T> leastUsedArena(PoolArena<T>[] arenas) {
            // 检查输入是否为空，如果是则直接返回null
            if (arenas == null || arenas.length == 0) {
                return null;
            }

            // 初始化为第一个arena，将其作为当前最小使用率的arena
            PoolArena<T> minArena = arenas[0];

            // 优化：如果这是第一次执行，并且minArena没有被使用过（即其线程缓存数为未使用标志），则直接返回minArena
            // 这样可以减少下面for循环中的比较次数
            if (minArena.numThreadCaches.get() == CACHE_NOT_USED) {
                return minArena;
            }

            // 遍历剩余的arena，查找并记录使用最少的arena
            for (int i = 1; i < arenas.length; i++) {
                PoolArena<T> arena = arenas[i];
                // 如果当前arena的线程缓存数少于minArena的线程缓存数，则更新minArena为当前arena
                if (arena.numThreadCaches.get() < minArena.numThreadCaches.get()) {
                    minArena = arena;
                }
            }

            // 返回使用最少的arena
            return minArena;
        }
    }

    static {
        int defaultAlignment = 0;
        int defaultPageSize = 8192;
        try {
            validateAndCalculatePageShifts(defaultPageSize, defaultAlignment);
        } catch (Throwable t) {
            defaultPageSize = 8192;
            defaultAlignment = 0;
        }
        DEFAULT_PAGE_SIZE = defaultPageSize;
        DEFAULT_DIRECT_MEMORY_CACHE_ALIGNMENT = defaultAlignment;

        int defaultMaxOrder = 11;
        try {
            validateAndCalculateChunkSize(DEFAULT_PAGE_SIZE, defaultMaxOrder);
        } catch (Throwable t) {
            defaultMaxOrder = 11;
        }
        DEFAULT_MAX_ORDER = defaultMaxOrder;

        // 32 kb is the default maximum capacity of the cached buffer. Similar to what is explained in
        // 'Scalable memory allocation using jemalloc'
        DEFAULT_MAX_CACHED_BUFFER_CAPACITY = 32 * 1024;

        // the number of threshold of allocations when cached entries will be freed up if not frequently used
        DEFAULT_CACHE_TRIM_INTERVAL = 8192;

    }

    private static int validateAndCalculateChunkSize(int pageSize, int maxOrder) {
        if (maxOrder > 14) {
            throw new IllegalArgumentException("maxOrder: " + maxOrder + " (expected: 0-14)");
        }

        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = pageSize;
        for (int i = maxOrder; i > 0; i--) {
            if (chunkSize > MAX_CHUNK_SIZE / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", pageSize, maxOrder, MAX_CHUNK_SIZE));
            }
            chunkSize <<= 1;
        }
        return chunkSize;
    }

    private static int validateAndCalculatePageShifts(int pageSize, int alignment) {
        if (pageSize < MIN_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: " + MIN_PAGE_SIZE + ')');
        }

        if ((pageSize & pageSize - 1) != 0) {
            throw new IllegalArgumentException("pageSize: " + pageSize + " (expected: power of 2)");
        }

        if (pageSize < alignment) {
            throw new IllegalArgumentException("Alignment cannot be greater than page size. " +
                    "Alignment: " + alignment + ", page size: " + pageSize + '.');
        }

        // Logarithm base 2. At this point we know that pageSize is a power of two.
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(pageSize);
    }

    public PooledBufferAllocate(int nDirectArena, int pageSize, int maxOrder,
                                int smallCacheSize, int normalCacheSize,
                                boolean useCacheForAllThreads) {
        threadCache = new PoolThreadLocalCache(useCacheForAllThreads);

        this.smallCacheSize = smallCacheSize;
        this.normalCacheSize = normalCacheSize;

        chunkSize = validateAndCalculateChunkSize(pageSize, maxOrder);

        int pageShifts = validateAndCalculatePageShifts(pageSize, 0);

        directArenas = newArenaArray(nDirectArena);
        for (int i = 0; i < directArenas.length; i++) {
            PoolArena arena = new PoolArena(pageSize, pageShifts, chunkSize, this, 0);
            directArenas[i] = arena;
        }
    }

    private PoolArena<ByteBuffer>[] newArenaArray(int nDirectArena) {
        return new PoolArena[nDirectArena];
    }

    public ByteBuf allocate() {
        return this.handle.allocate();
    }

    public ByteBuf ioBuffer(int guess) {
        return directBuffer(guess, DEFAULT_MAX_CAPACITY);
    }

    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        return newDirectBuffer(initialCapacity, maxCapacity);
    }

    public ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        // 本地线程缓存
        ThreadLocalCache cache = threadCache.get();
        // 缓冲池
        PoolArena<ByteBuffer> directArena = cache.directArena;
        final ByteBuf buf = directArena.allocate(cache, initialCapacity, maxCapacity);
        return buf;
    }

}
