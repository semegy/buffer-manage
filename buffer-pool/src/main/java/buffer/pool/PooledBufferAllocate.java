package buffer.pool;

import buffer.BufferAllocate;
import buffer.ByteBuf;
import buffer.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class PooledBufferAllocate implements BufferAllocate {


    private int nextReceiveBufferSize;
    static final int CALCULATE_THRESHOLD = 1048576 * 4; // 4 MiB page

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

    private static final int[] SIZE_TABLE;

    private int index;

    static {
        List<Integer> sizeTable = new ArrayList<Integer>();
        // 设置空闲大小
        // 0 ~ 32 为 16 -> 32 -> ..-> 16n -> 512
        for (int i = 16; i < 512; i += 16) {
            sizeTable.add(i);
        }

        // Suppress a warning since i becomes negative when an integer overflow happens
        // 0 ~ 21
        // 512bit -> 1k -> 2k -> ..-> 512k -> 1M -> 2M -> .. -> 1024M
        for (int i = 512; i > 0; i <<= 1) { // lgtm[java/constant-comparison]
            sizeTable.add(i);
        }

        // 设置内存大小分区 31 + 22 = 53个分区
        SIZE_TABLE = new int[sizeTable.size()];
        for (int i = 0; i < SIZE_TABLE.length; i ++) {
            SIZE_TABLE[i] = sizeTable.get(i);
        }
    }

    Handle handle = new HandleImpl(DEFAULT_MINIMUM, DEFAULT_INITIAL, DEFAULT_MAXIMUM);

    /**
     * 根据内存大小获取分区
     * @param size
     * @return
     */
    private static int getSizeTableIndex(final int size) {
        // high = 52 二分法
        for (int low = 0, high = SIZE_TABLE.length - 1;;) {
            if (high < low) {
                // 高索引位 < 低索引位
                return low;
            }
            if (high == low) {
                // 高索引位 == 低索引位
                return high;
            }

            // 第一次 mid = high / 2 + 0 = 26
            // 第二次 mid = 1 + 13 = 14
            // 第四次 mid = 2 + 7 == 9
            // 第五次 mid = 3 + 4 = 7
            // 第六次 mid = 3 + 4 = 7
            int mid = low + high >>> 1;
            int a = SIZE_TABLE[mid];
            int b = SIZE_TABLE[mid + 1];
            if (size > b) {
                // 最小索引位
                low = mid + 1;
            } else if (size < a) {
                // 最大索引位
                high = mid - 1;
            } else if (size == a) {
                // 找到合适的大小 返回mid
                return mid;
            } else {
                // 返回high
                return mid + 1;
            }
        }
    }

    public class HandleImpl implements Handle {

        // 最小索引
        private final int minIndex;
        // 最大索引
        private final int maxIndex;
        private int totalBytesRead;

        private int attemptBytesRead;

        private int lastBytesRead;

        private boolean decreaseNow = false;

        HandleImpl(int minimum, int maximum, int initial) {
            int minIndex = getSizeTableIndex(minimum);
            // 分配不足 索引+1
            if (SIZE_TABLE[minIndex] < minimum) {
                this.minIndex = minIndex + 1;
            } else {
                this.minIndex = minIndex;
            }
            // 最大索引
            int maxIndex = getSizeTableIndex(maximum);
            // 分配过大 索引 - 1
            if (SIZE_TABLE[maxIndex] > maximum) {
                this.maxIndex = maxIndex - 1;
            } else {
                this.maxIndex = maxIndex;
            }
            int initialIndex = getSizeTableIndex(initial);

            nextReceiveBufferSize = SIZE_TABLE[initialIndex];
        }

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
            // If we read as much as we asked for we should check if we need to ramp up the size of our next guess.
            // This helps adjust more quickly when large amounts of data is pending and can avoid going back to
            // the selector to check for more data. Going back to the selector can add significant latency for large
            // data transfers.
            // 如果上次读取字节 == 尝试读取的字节
            if (bytes == attemptedBytesRead()) {
                record(bytes);
            }
            // 上次次读取字节数累加
            // 上次读取字节
            lastBytesRead = bytes;
            if (bytes > 0) {
                // 本次需要读取字节
                totalBytesRead += bytes;
            }
        }

        private void record(int actualReadBytes) {
            // 实际读取的字节 <= nextReceiveBufferSize 的前一个字节大小
            if (actualReadBytes <= SIZE_TABLE[max(0, index - 1)]) {
                // 使用立马递减？
                if (decreaseNow) {
                    // 重置index nextReceiveBufferSize，降低空间使用率
                    index = max(index - 1, minIndex);
                    nextReceiveBufferSize = SIZE_TABLE[index];
                    // 设置 decreaseNow = false
                    decreaseNow = false;
                } else {
                    // 下次再小 会触发if（true）逻辑
                    decreaseNow = true;
                }
            } else if (actualReadBytes >= nextReceiveBufferSize) {
                // 增长空间
                index = min(index + 1, maxIndex);
                nextReceiveBufferSize = SIZE_TABLE[index];
                decreaseNow = false;
            }
        }

        @Override
        public int lastBytesRead() {
            return lastBytesRead;
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            attemptBytesRead = bytes;
        }

        @Override
        public int attemptedBytesRead() {
            return attemptBytesRead;
        }

        @Override
        public boolean continueReading() {
            return false;
        }

        @Override
        public void readComplete() {
            // 完成空间大小预估设置
            record(totalBytesRead());
        }
        protected final int totalBytesRead() {
            return totalBytesRead < 0 ? Integer.MAX_VALUE : totalBytesRead;
        }

    };

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
        if (minNewCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                    minNewCapacity, maxCapacity));
        }
        final int threshold = CALCULATE_THRESHOLD; // 4 MiB page

        if (minNewCapacity == threshold) {
            return threshold;
        }

        // If over threshold, do not double but just increase by threshold.
        if (minNewCapacity > threshold) {
            int newCapacity = minNewCapacity / threshold * threshold;
            if (newCapacity > maxCapacity - threshold) {
                newCapacity = maxCapacity;
            } else {
                newCapacity += threshold;
            }
            return newCapacity;
        }

        // 64 <= newCapacity is a power of 2 <= threshold
        int value = Math.max(minNewCapacity, 64);
        int newCapacity = 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
        return Math.min(newCapacity, maxCapacity);
    }

    public ThreadLocalCache threadCache() {
        ThreadLocalCache cache = threadCache.get();
        assert cache != null;
        return cache;
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
