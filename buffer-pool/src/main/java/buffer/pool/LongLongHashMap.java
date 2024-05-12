package buffer.pool;

public class LongLongHashMap {
    // 反码
    private static final int MASK_TEMPLATE = ~1;
    private int mask;
    private long[] array;
    private int maxProbe;
    private long zeroVal;
    private final long emptyVal;
    private static final int INITIAL_SIZE = 32;

    LongLongHashMap(long emptyVal) {
        this.emptyVal = emptyVal;
        zeroVal = emptyVal;
        array = new long[INITIAL_SIZE];
        mask = INITIAL_SIZE - 1;
        computeMaskAndProbe();
    }

    private void computeMaskAndProbe() {
        int length = array.length;
        mask = length - 1 & MASK_TEMPLATE;
        maxProbe = (int) Math.log(length);
    }

    public long put(long key, long value) {
        if (key == 0) {
            long prev = zeroVal;
            zeroVal = value;
            return prev;
        }

        for (; ; ) {
            int index = index(key);
            for (int i = 0; i < maxProbe; i++) {
                long existing = array[index];
                if (existing == key || existing == 0) {
                    long prev = existing == 0 ? emptyVal : array[index + 1];
                    array[index] = key;
                    array[index + 1] = value;
                    for (; i < maxProbe; i++) { // Nerf any existing misplaced entries.
                        index = index + 2 & mask;
                        if (array[index] == key) {
                            array[index] = 0;
                            prev = array[index + 1];
                            break;
                        }
                    }
                    return prev;
                }
                index = index + 2 & mask;
            }
            expand(); // Grow array and re-hash.
        }

    }

    private int index(long key) {
        // Hash with murmur64, and mask.
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= key >>> 33;
        return (int) key & mask;
    }

    public void remove(long key) {
        if (key == 0) {
            zeroVal = emptyVal;
            return;
        }
        int index = index(key);
        for (int i = 0; i < maxProbe; i++) {
            long existing = array[index];
            if (existing == key) {
                array[index] = 0;
                break;
            }
            index = index + 2 & mask;
        }
    }

    private void expand() {
        long[] prev = array;
        array = new long[prev.length * 2];
        computeMaskAndProbe();
        for (int i = 0; i < prev.length; i += 2) {
            long key = prev[i];
            if (key != 0) {
                long val = prev[i + 1];
                put(key, val);
            }
        }
    }

    public long get(long key) {
        if (key == 0) {
            return zeroVal;
        }
        int index = index(key);
        // 遍历冲突链表
        for (int i = 0; i < maxProbe; i++) {
            long existing = array[index];
            if (existing == key) {
                return array[index + 1];
            }
            index = index + 2 & mask;
        }
        return emptyVal;
    }
}
