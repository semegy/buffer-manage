package buffer.pool;

import static buffer.pool.SizeClasses.LOG2_QUANTUM;

public class PoolSubpage<T> {

    private final int pageShifts /*13*/;
    public final int elemSize /*2048*/;
    /**
     * 当前子页所属chunk区
     */
    PoolChunk<T> chunk;

    /**
     * 定位前一个子页
     */
    PoolSubpage<T> prev;

    /**
     * 定位下一个子页
     */
    PoolSubpage<T> next;

    private int runOffset /*0*/;
    private int runSize /*8k*/;

    // bit位
    private long[] bitmap;

    private int numAvail;
    private int maxNumElems;
    private int nextAvail;
    private int bitmapLength;
    private boolean doNotDestroy;

    PoolSubpage() {
        chunk = null;
        pageShifts = -1;
        runOffset = -1;
        elemSize = -1;
        runSize = -1;
        bitmap = null;
    }

    PoolSubpage(PoolSubpage<T> head, PoolChunk<T> chunk, int pageShifts, int runOffset, int runSize, int elemSize) {
        this.chunk = chunk;
        this.pageShifts = pageShifts;
        this.runOffset = runOffset;
        this.runSize = runSize;
        this.elemSize = elemSize;
        bitmap = new long[runSize >>> 6 + LOG2_QUANTUM]; // 预留bitMap空间

        doNotDestroy = true;
        if (elemSize != 0) {
            // 计算当前存储块最大数量
            // 8192 / 16b = 512
            // 8192 / 32b = 256
            maxNumElems = numAvail = runSize / elemSize;
            nextAvail = 0;
            // 存储块空闲状态需要几个long元素数据标记状态 512 / 64 = 8 8个long元素位标识对应存储块
            bitmapLength = maxNumElems >>> 6;
            if ((maxNumElems & 63) != 0) {
                // 如果 maxNumElems 不是64的倍数，则需要多预留一个long元素
                bitmapLength++;
            }

            for (int i = 0; i < bitmapLength; i++) {
                bitmap[i] = 0;
            }
        }
        addToPool(head);
    }

    /**
     * @return {@code true} if this subpage is in use.
     * {@code false} if this subpage is not used by its chunk and thus it's OK to be released.
     */
    boolean free(PoolSubpage<T> head, int bitmapIdx) {
        if (elemSize == 0) {
            return true;
        }
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) != 0;
        bitmap[q] ^= 1L << r;

        setNextAvail(bitmapIdx);

        if (numAvail++ == 0) {
            addToPool(head);
            /* When maxNumElems == 1, the maximum numAvail is also 1.
             * Each of these PoolSubpages will go in here when they do free operation.
             * If they return true directly from here, then the rest of the code will be unreachable
             * and they will not actually be recycled. So return true only on maxNumElems > 1. */
            if (maxNumElems > 1) {
                return true;
            }
        }

        if (numAvail != maxNumElems) {
            return true;
        } else {
            // Subpage not in use (numAvail == maxNumElems)
            if (prev == next) {
                // Do not remove if this subpage is the only one left in the pool.
                return true;
            }

            // Remove this subpage from the pool if there are other subpages left in the pool.
            doNotDestroy = false;
            removeFromPool();
            return false;
        }
    }

    private void setNextAvail(int bitmapIdx) {
        nextAvail = bitmapIdx;
    }

    /**
     * 头插法
     *
     * @param head 池子中的头部页面，也就是要将当前对象添加到这个头部后面。
     */
    private void addToPool(PoolSubpage<T> head) {
        assert prev == null && next == null;
        prev = head;
        next = head.next;
        next.prev = this;
        head.next = this;
    }

    public long allocate() {
        if (numAvail == 0 || !doNotDestroy) {
            return -1;
        }

        // 可用存储块索引
        final int bitmapIdx = getNextAvail();
        // 高位索引 long
        int q = bitmapIdx >>> 6;
        // 低位索引
        int r = bitmapIdx & 63;
        // bitMap中q对应的long元素的R位是空闲位
        assert (bitmap[q] >>> r & 1) == 0;
        // 设置bitMap中q对应的long元素的R位为1，表示存储块已被占用
        bitmap[q] |= 1L << r;

        // 可用存储块减法
        if (--numAvail == 0) { // 如果存储块已全部分配完毕，移除此page
            removeFromPool();
        }

        return toHandle(bitmapIdx);
    }

    private long toHandle(int bitmapIdx) {
        int pages = runSize >> pageShifts;
        return (long) runOffset << PoolChunk.RUN_OFFSET_SHIFT
                | (long) pages << PoolChunk.SIZE_SHIFT
                | 1L << PoolChunk.IS_USED_SHIFT
                | 1L << PoolChunk.IS_SUBPAGE_SHIFT
                | bitmapIdx;
    }

    private void removeFromPool() {
        assert prev != null && next != null;
        prev.next = next;
        next.prev = prev;
        next = null;
        prev = null;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i++) {
            long bits = bitmap[i];
            // ~bits != 0 表示bits是否有0位，没有跳出当前long元素，否则表示当前long的位上有空闲的存储块
            if (~bits != 0) {
                // bits上查找空闲的存储块索引
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int findNextAvail0(int i, long bits) {
        // 最大的存储块数量
        final int maxNumElems = 512;
        // 第i个long元素的初始位 i*64
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j++) {
            if ((bits & 1) == 0) { // 最低一位是否为0，为1则说明当前bit位没有
                // 记录bit位在bitMap中的bit位找到空闲的存储块 i*64 + 所在的空闲位
                int val = baseVal | j;
                // 存储位超过了最大存储块数量表示存储块已满
                if (val < maxNumElems) {
                    // 返回空闲存储块的索引
                    return val;
                } else {
                    break;
                }
            }
            // 向右移动一个bit位继续执行循环，可利用最后的bit位是否位判断当前的bit是否空闲
            bits >>>= 1;
        }
        // 没有空闲位置
        return -1;
    }
}
