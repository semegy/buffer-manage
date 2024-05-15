/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package buffer.pool;

/**
 * Internal primitive map implementation that is specifically optimised for the runs availability map use case in {@link
 * PoolChunk}.
 * 长整形到长整型的hash映射
 */
final class LongLongHashMap {
    private static final int MASK_TEMPLATE = ~1;
    private int mask;
    private long[] array;
    private int maxProbe;
    private long zeroVal;
    private final long emptyVal;

    LongLongHashMap(long emptyVal) {
        this.emptyVal = emptyVal;
        zeroVal = emptyVal;
        int initialSize = 32;
        array = new long[initialSize];
        mask = initialSize - 1;
        computeMaskAndProbe();
    }

    /**
     * 将给定的键值对存储到数据结构中。如果键已存在，则替换旧的值并返回旧的值；如果键不存在，则新增键值对。
     *
     * @param key   要存储的键，必须为非负数。
     * @param value 与键对应的值，可以为任意长整型数值。
     * @return 如果键已存在，返回旧的值；如果键不存在且插入成功，返回特殊的空值标识。
     */
    public long put(long key, long value) {
        if (key == 0) {
            // 处理特殊情况：键为0的情况，直接替换并返回旧的值
            long prev = zeroVal;
            zeroVal = value;
            return prev;
        }

        for (; ; ) {
            // hash槽
            int index = index(key);
            for (int i = 0; i < maxProbe; i++) {
                // 尝试在散列槽中找到空位或匹配的键
                long existing = array[index];
                if (existing == key || existing == 0) {
                    // 在找到的槽位中设置新的键值对
                    long prev = existing == 0 ? emptyVal : array[index + 1];
                    // index槽位设置key
                    array[index] = key;
                    // index下一槽位设置value
                    array[index + 1] = value;
                    // hash链表冲突时的解决， 允许最大冲突链 maxProbe
                    for (; i < maxProbe; i++) { // Nerf any existing misplaced entries.
                        index = index + 2 & mask;
                        if (array[index] == key) {
                            // 冲突的键值对，设置为0
                            array[index] = 0;
                            prev = array[index + 1];
                            break;
                        }
                    }
                    // 完成插入操作返回旧值
                    return prev;
                }
                // 下一个散列槽位, 循环查找最大冲突次数，直到找到空位为止，如果超出后则需要进行扩容操作
                index = index + 2 & mask;
            }
            expand(); // Grow array and re-hash.
        }
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

    private int index(long key) {
        // hash算法
        // Hash with murmur64, and mask.
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdL;
        key ^= key >>> 33;
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= key >>> 33;
        return (int) key & mask;
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

    private void computeMaskAndProbe() {
        int length = array.length;
        mask = length - 1 & MASK_TEMPLATE;
        maxProbe = (int) Math.log(length);
    }
}
