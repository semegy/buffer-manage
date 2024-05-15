package buffer.pool;

import java.util.Arrays;

public class LongPriorityQueue {

    public static final long NO_VALUE = -1;
    private long[] array = new long[9];

    private int size;

    public static LongPriorityQueue[] newRunsAvailQueueArray(int size) {
        LongPriorityQueue[] queueArray = new LongPriorityQueue[size];
        for (int i = 0; i < queueArray.length; i++) {
            queueArray[i] = new LongPriorityQueue();
        }
        return queueArray;
    }

    public void offer(long handle) {
        if (handle == NO_VALUE) {
            throw new IllegalArgumentException("The NO_VALUE (" + NO_VALUE + ") cannot be added to the queue.");
        }
        size++;
        if (size == array.length) {
            // Grow queue capacity.
            array = Arrays.copyOf(array, array.length * 2);
        }
        // 保存可管理的handle
        array[size] = handle;
        lift(size);
    }

    public void remove(long value) {
        for (int i = 1; i <= size; i++) {
            if (array[i] == value) {
                array[i] = array[size--];
                // 向上一级替换排序
                lift(i);
                // 向下一级替换排序
                sink(i);
                return;
            }
        }
    }

    private void lift(int index) {
        int parentIndex;
        // 向前移位遍历，直到 遇到 父节点小于等于点子节点跳出循环
        // 维护了多个可用的run空间有小到大排序
        while (index > 1 && subord(parentIndex = index >> 1, index)) {
            swap(index, parentIndex);
            index = parentIndex;
        }
    }


    public long poll() {
        if (size == 0) {
            return NO_VALUE;
        }
        long val = array[1];
        array[1] = array[size];
        array[size] = 0;
        size--;
        sink(1);
        return val;
    }

    private void sink(int index) {
        int child;
        while ((child = index << 1) <= size) {
            if (child < size && subord(child, child + 1)) {
                child++;
            }
            if (!subord(index, child)) {
                break;
            }
            swap(index, child);
            index = child;
        }
    }

    private boolean subord(int a, int b) {
        return array[a] > array[b];
    }

    private void swap(int a, int b) {
        long value = array[a];
        array[a] = array[b];
        array[b] = value;
    }

    public boolean isEmpty() {
        return size == 0;
    }
}
