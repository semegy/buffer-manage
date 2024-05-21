package buffer.pool;

import buffer.recycle.ThreadLocalCache;

import java.nio.ByteBuffer;

import static java.lang.Math.max;

public class PoolChunkList<T> {

    private final PoolArena<T> arena;
    private final PoolChunkList<T> nextList;
    private final int freeMinThreshold;
    private PoolChunk<T> head;

    private final int minUsage;

    private final int maxUsage;

    private final int freeMaxThreshold;

    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private PoolChunkList<T> prevList;
    private final int maxCapacity;

    PoolChunkList(PoolArena<T> arena, PoolChunkList<T> nextList, int minUsage, int maxUsage, int chunkSize) {
        assert minUsage <= maxUsage;
        this.arena = arena;
        this.nextList = nextList;
        this.minUsage = minUsage;
        this.maxUsage = maxUsage;
        this.maxCapacity = calculateMaxCapacity(minUsage, chunkSize);

        // 阈值与 PoolChunk.usage（） 逻辑一致：
        // 1）基本逻辑：usage（） = 100 - freeBytes * 100L / chunkSize
        // 因此，例如：（usage（） >= maxUsage） 条件可以通过以下方式进行转换：
        // 100 - freeBytes * 100L / chunkSize >= maxUsage
        // freeBytes <= chunkSize * （100 - 最大使用量） / 100
        // let freeMinThreshold = chunkSize * （100 - maxUsage） / 100，则 freeBytes <= freeMinThreshold
        //
        // 2） usage（） 返回一个 int 值，并在计算过程中进行下限舍入，
        // 要对齐，应为“舍入步骤”移动绝对阈值：
        // freeBytes * 100 / chunkSize < 1
        // 条件可以转换为：freeBytes < 1 * chunkSize / 100
        // 这就是为什么我们有 + 0.99999999 班次。不能仅使用 +1 班次的示例：
        // freeBytes = 16777216 == freeMaxThreshold： 16777216， usage = 0 < minUsage： 1， chunkSize： 16777216
        // 同时，我们希望在 （maxUsage == 100） 和 （minUsage == 100） 的情况下阈值为零。

        // 空闲使用率小于最小使用率时，分配内存
        freeMinThreshold = (maxUsage == 100) ? 0 : (int) (chunkSize * (100.0 - maxUsage + 0.99999999) / 100L);
        // 空闲使用率大于最大使用率时，释放内存
        freeMaxThreshold = (minUsage == 100) ? 0 : (int) (chunkSize * (100.0 - minUsage + 0.99999999) / 100L);
    }

    void prevList(PoolChunkList<T> prevList) {
        assert this.prevList == null;
        this.prevList = prevList;
    }

    private static int calculateMaxCapacity(int minUsage, int chunkSize) {
        minUsage = minUsage0(minUsage);

        if (minUsage == 100) {
            // If the minUsage is 100 we can not allocate anything out of this list.
            return 0;
        }

        // Calculate the maximum amount of bytes that can be allocated from a PoolChunk in this PoolChunkList.
        //
        // As an example:
        // - If a PoolChunkList has minUsage == 25 we are allowed to allocate at most 75% of the chunkSize because
        //   this is the maximum amount available in any PoolChunk in this PoolChunkList.
        return (int) (chunkSize * (100L - minUsage) / 100L);
    }

    private static int minUsage0(int value) {
        return max(1, value);
    }

    void add(PoolChunk<T> chunk) {
        if (chunk.freeBytes <= freeMinThreshold) {
            nextList.add(chunk);
            return;
        }
        add0(chunk);
    }

    boolean free(PoolChunk<T> chunk, long handle, int normCapacity, ByteBuffer nioBuffer) {
        chunk.free(handle, normCapacity, nioBuffer);
        // chunk可以释放的字节 > 最大释放的阈值上线
        if (chunk.freeBytes > freeMaxThreshold) {
            remove(chunk);
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);
        }
        return true;
    }

    private boolean move0(PoolChunk<T> chunk) {
        if (prevList == null) {
            // There is no previous PoolChunkList so return false which result in having the PoolChunk destroyed and
            // all memory associated with the PoolChunk will be released.
            assert chunk.usage() == 0;
            return false;
        }
        return prevList.move(chunk);
    }

    private boolean move(PoolChunk<T> chunk) {
        assert chunk.usage() < maxUsage;

        if (chunk.freeBytes > freeMaxThreshold) {
            // Move the PoolChunk down the PoolChunkList linked-list.
            return move0(chunk);
        }

        // PoolChunk fits into this PoolChunkList, adding it here.
        add0(chunk);
        return true;
    }

    /**
     * Adds the {@link PoolChunk} to this {@link PoolChunkList}.
     */
    void add0(PoolChunk<T> chunk) {
        chunk.parent = this;
        if (head == null) {
            head = chunk;
            chunk.prev = null;
            chunk.next = null;
        } else {
            chunk.prev = null;
            chunk.next = head;
            head.prev = chunk;
            head = chunk;
        }
    }


    private void remove(PoolChunk<T> cur) {
        if (cur == head) {
            head = cur.next;
            if (head != null) {
                head.prev = null;
            }
        } else {
            PoolChunk<T> next = cur.next;
            cur.prev.next = next;
            if (next != null) {
                next.prev = cur.prev;
            }
        }
    }

    public boolean allocate(PooledByteBuf<T> buf, int reqCapacity, ThreadLocalCache cache, int sizeIdx) {
        // normal内存容量
        int normCapacity = arena.sizeIdx2size(sizeIdx);
        // normal内存容量
        if (normCapacity > maxCapacity) {
            // Either this PoolChunkList is empty or the requested capacity is larger then the capacity which can
            // be handled by the PoolChunks that are contained in this PoolChunkList.
            return false;
        }

        for (PoolChunk<T> cur = head; cur != null; cur = cur.next) {
            if (cur.allocate(buf, reqCapacity, cache, sizeIdx)) {
                if (cur.freeBytes <= freeMinThreshold) {
                    // 利用率增加，转移的更容易寻址的ChunkList中
                    remove(cur);
                    nextList.add(cur);
                }
                return true;
            }
        }
        return false;
    }
}
