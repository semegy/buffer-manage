package pool;

public abstract class SizeClasses implements SizeClassesMetric {

    static final int LOG2_QUANTUM = 4;

    private static final int LOG2_SIZE_CLASS_GROUP = 2;
    private static final int LOG2_MAX_LOOKUP_SIZE = 12;
    private static final int LOG2GROUP_IDX = 1;
    private static final int LOG2DELTA_IDX = 2;
    private static final int NDELTA_IDX = 3;
    private static final int PAGESIZE_IDX = 4;
    private static final int SUBPAGE_IDX = 5;
    private static final int LOG2_DELTA_LOOKUP_IDX = 6;

    private static final byte no = 0, yes = 1;

    protected final int pageSize;
    protected final int pageShifts;
    public final int chunkSize;
    protected final int directMemoryCacheAlignment;

    public final int nSizes;
    final int nSubpages;
    final int nPSizes;
    final int lookupMaxSize;
    final int smallMaxSizeIdx;
    private final int[] pageIdx2sizeTab;

    // lookup table for sizeIdx <= smallMaxSizeIdx
    private final int[] sizeIdx2sizeTab;

    // lookup table used for size <= lookupMaxClass
    // spacing is 1 << LOG2_QUANTUM, so the size of array is lookupMaxClass >> LOG2_QUANTUM
    private final int[] size2idxTab;

    protected SizeClasses(int pageSize, int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
        int group = log2(chunkSize) + 1 - LOG2_QUANTUM;

        //generate size classes
        //[index, log2Group, log2Delta, nDelta, isMultiPageSize, isSubPage, log2DeltaLookup]
        short[][] sizeClasses = new short[group << LOG2_SIZE_CLASS_GROUP][7];

        int normalMaxSize = -1;
        int nSizes = 0;
        int size = 0;

        int log2Group = LOG2_QUANTUM;
        int log2Delta = LOG2_QUANTUM;
        int ndeltaLimit = 1 << LOG2_SIZE_CLASS_GROUP;

        //First small group, nDelta start at 0.
        //first size class is 1 << LOG2_QUANTUM
        // 第一规格组
        for (int nDelta = 0; nDelta < ndeltaLimit; nDelta++, nSizes++) {
            // index	log2Group	log2Delta	nDelta	isMultiPageSize	isSubPage	log2DeltaLookup	size	log2Size	pageIdxAndPages	size2idxTab
            // 0	    4	        4	        0	    0	            1	        4	            16	     4(4)		                0
            // 1	    4	        4	        1	    0	            1	        4	            32	     5(5)		                1
            // 2	    4	        4	        2	    0	            1	        4	            48	     5(6)		                2
            // 3	    4	        4	        3	    0	            1	        4	            64	     6(6)		                3
            short[] sizeClass = newSizeClass(nSizes, log2Group, log2Delta, nDelta, pageShifts);
            sizeClasses[nSizes] = sizeClass;
            // 规格大小
            size = sizeOf(sizeClass, directMemoryCacheAlignment);
        }

        // 6
        log2Group += LOG2_SIZE_CLASS_GROUP;

        //All remaining groups, nDelta start at 1.
        for (; size < chunkSize; log2Group++, log2Delta++) {
            // 设置规格组sizeClass
            for (int nDelta = 1; nDelta <= ndeltaLimit && size < chunkSize; nDelta++, nSizes++) {
                // 规格组类型
                short[] sizeClass = newSizeClass(nSizes, log2Group, log2Delta, nDelta, pageShifts);
                sizeClasses[nSizes] = sizeClass;
                size = normalMaxSize = sizeOf(sizeClass, directMemoryCacheAlignment);
            }
        }

        //chunkSize must be normalMaxSize
        // chunk 16M
        assert chunkSize == normalMaxSize;

        int smallMaxSizeIdx = 0;
        int lookupMaxSize = 0;
        int nPSizes = 0;
        int nSubpages = 0;
        for (int idx = 0; idx < nSizes; idx++) {
            // 规格类
            short[] sz = sizeClasses[idx];
            // 是否整页， 整页数自增
            if (sz[PAGESIZE_IDX] == yes) {
                nPSizes++;
            }
            // 是否为子页，子页数自增
            if (sz[SUBPAGE_IDX] == yes) {
                nSubpages++;
                // 最终子页数索引
                smallMaxSizeIdx = idx;
            }
            if (sz[LOG2_DELTA_LOOKUP_IDX] != no) {
                // 快速查询对应规格大小的辅助值上限，4096。
                lookupMaxSize = sizeOf(sz, directMemoryCacheAlignment);
            }
        }
        // 小规格内存索引最大值
        this.smallMaxSizeIdx = smallMaxSizeIdx;
        // 快速查询对应规格大小的辅助值上限，4096。
        this.lookupMaxSize = lookupMaxSize;
        // 整页倍数40，用于创建pageIndex2sizeTab映射表
        this.nPSizes = nPSizes;
        // 子页数量38
        this.nSubpages = nSubpages;
        // 规格数量
        this.nSizes = nSizes;
        // 页面大小
        this.pageSize = pageSize;
        // 页面位移
        this.pageShifts = pageShifts;
        // 块大小
        this.chunkSize = chunkSize;
        // 堆内存对齐，默认0
        this.directMemoryCacheAlignment = directMemoryCacheAlignment;

        //generate lookup tables
        // 创建规格大小映射表
        sizeIdx2sizeTab = newIdx2SizeTab(sizeClasses, nSizes, directMemoryCacheAlignment);
        // 创建页索引映射表
        pageIdx2sizeTab = newPageIdx2sizeTab(sizeClasses, nSizes, nPSizes, directMemoryCacheAlignment);
        // 小规格内存索引映射表
        size2idxTab = newSize2idxTab(lookupMaxSize, sizeClasses);
    }

    /**
     * 计算大小类别。
     * 该方法用于根据提供的参数计算出与内存分配相关的大小类别信息。
     *
     * @param index      类别的索引。
     * @param log2Group  以2为底的规格组指数。
     * @param log2Delta  以2为底的规格组增量指数。
     * @param nDelta     规格组的增量数。
     * @param pageShifts 页面大小的位移量。
     * @return 一个short数组，包含计算出的大小类别相关信息。
     */
    private static short[] newSizeClass(int index, int log2Group, int log2Delta, int nDelta, int pageShifts) {
        short isMultiPageSize;
        if (log2Delta >= pageShifts) {
            isMultiPageSize = yes;
        } else {
            // 页容量：8192
            int pageSize = 1 << pageShifts;
            // size = (1<<log2Delta) + nDelta<<log2Delta
            // 内存规格计算 相当于当前规格组初始大小 + 规格组增量数*规格组增量
            // 每个内存规格组规定有4种
            int size = calculateSize(log2Group, nDelta, log2Delta);
            // 是否是整页
            isMultiPageSize = size == size / pageSize * pageSize ? yes : no;
        }

        // 计算增量规格数的对数值 0 ：0 ， 1 ： 0 ， 2 ： 1 ， 3 ： 1 ， 4 ： 2
        int log2Ndelta = nDelta == 0 ? 0 : log2(nDelta);
        // 是否需要移除
        byte remove = 1 << log2Ndelta < nDelta ? yes : no;

        // 计算规格大小对数
        int log2Size = log2Delta + log2Ndelta == log2Group ? log2Group + 1 : log2Group;
        if (log2Size == log2Group) {
            remove = yes;
        }
        // 偏移15位为32k，小于32k，则表示是页规格, 所以页规格最大是28k
        short isSubpage = log2Size < pageShifts + LOG2_SIZE_CLASS_GROUP ? yes : no;
        // 对于方便较小size 快速查询对应规格大小的辅助值
        int log2DeltaLookup = log2Size < LOG2_MAX_LOOKUP_SIZE ||
                log2Size == LOG2_MAX_LOOKUP_SIZE && remove == no
                ? log2Delta : no;

        return new short[]{
                (short) index, (short) log2Group, (short) log2Delta,
                (short) nDelta, isMultiPageSize, isSubpage, (short) log2DeltaLookup
        };
    }

    /**
     * 根据给定的尺寸类别数组，计算并生成一个对应尺寸索引和尺寸值的映射表。
     *
     * @param sizeClasses                尺寸类别数组，每个类别是一个短整型数组，代表不同尺寸的集合。
     * @param nSizes                     尺寸类别数组的大小，即尺寸类别的数量。
     * @param directMemoryCacheAlignment 直接内存缓存对齐的大小。
     * @return 一个整型数组，索引表示尺寸类别的索引，值表示对应尺寸类别的大小。
     */
    private static int[] newIdx2SizeTab(short[][] sizeClasses, int nSizes, int directMemoryCacheAlignment) {
        // 初始化一个整型数组，用于存储尺寸索引和尺寸值的映射关系
        int[] sizeIdx2sizeTab = new int[nSizes];

        // 遍历尺寸类别数组，计算每个尺寸类别的大小，并存储到映射表中
        for (int i = 0; i < nSizes; i++) {
            short[] sizeClass = sizeClasses[i];
            // 计算当前尺寸类别的大小，并存储到映射表中
            sizeIdx2sizeTab[i] = sizeOf(sizeClass, directMemoryCacheAlignment);
        }
        return sizeIdx2sizeTab;
    }


    /**
     * 计算给定参数的尺寸大小。
     *
     * @param log2Group 以2为底的组指数，表示组的大小。
     * @param nDelta    增量的数量。
     * @param log2Delta 以2为底的增量指数。
     *                  同组初始值大小 + 增量数*增量大小
     * @return 返回计算得到的尺寸大小。
     */
    private static int calculateSize(int log2Group, int nDelta, int log2Delta) {
        // 规格组第一个内存块 + 增量的大小
        return (1 << log2Group) + (nDelta << log2Delta);
    }

    private static int sizeOf(short[] sizeClass, int directMemoryCacheAlignment) {
        // 规格组
        int log2Group = sizeClass[LOG2GROUP_IDX];
        // 规格增量位移
        int log2Delta = sizeClass[LOG2DELTA_IDX];
        // 规格增量数
        int nDelta = sizeClass[NDELTA_IDX];
        // 规格大小
        int size = calculateSize(log2Group, nDelta, log2Delta);
        // 计算对齐
        return alignSizeIfNeeded(size, directMemoryCacheAlignment);
    }

    private static int[] newPageIdx2sizeTab(short[][] sizeClasses, int nSizes, int nPSizes,
                                            int directMemoryCacheAlignment) {
        int[] pageIdx2sizeTab = new int[nPSizes];
        int pageIdx = 0;
        for (int i = 0; i < nSizes; i++) {
            short[] sizeClass = sizeClasses[i];
            // 整页数据放入pageIdx2sizeTab
            if (sizeClass[PAGESIZE_IDX] == yes) {
                pageIdx2sizeTab[pageIdx++] = sizeOf(sizeClass, directMemoryCacheAlignment);
            }
        }
        return pageIdx2sizeTab;
    }

    /**
     * 根据给定的查找最大大小和尺寸类别数组，生成一个新的尺寸到索引映射表。
     *
     * @param lookupMaxSize 查找最大大小，决定了映射表的长度。
     * @param sizeClasses   尺寸类别数组，包含不同尺寸的分类信息。
     * @return int[] 返回一个尺寸到索引的映射表，其中索引用于快速查找对应的尺寸类别。
     */
    private static int[] newSize2idxTab(int lookupMaxSize, short[][] sizeClasses) {
        // 初始化尺寸到索引映射表，大小为lookupMaxSize经过位移操作后的值。
        // 尺寸映射是1:16的关系，size2idxTab映射表的容量计算则是lookupMaxSize除以16的整数部分。即右移四位
        int[] size2idxTab = new int[lookupMaxSize >> LOG2_QUANTUM];
        int idx = 0; // 索引，用于填充映射表。
        int size = 0; // 当前计算的尺寸。

        // 遍历尺寸类别数组，直到超过lookupMaxSize。
        for (int i = 0; size <= lookupMaxSize; i++) {
            // 获取当前尺寸类别的增量的对数值。
            int log2Delta = sizeClasses[i][LOG2DELTA_IDX];
            // 计算基于log2Delta的重复次数。
            int times = 1 << log2Delta - LOG2_QUANTUM;

            // 循环填充映射表，直到尺寸超过lookupMaxSize或重复次数用尽。
            while (size <= lookupMaxSize && times-- > 0) {
                size2idxTab[idx++] = i; // 将当前尺寸类别索引填充到映射表。
                size = idx + 1 << LOG2_QUANTUM; // 更新尺寸值。
            }
        }
        return size2idxTab;
    }

    @Override
    public int sizeIdx2size(int sizeIdx) {
        return sizeIdx2sizeTab[sizeIdx];
    }

    @Override
    public int sizeIdx2sizeCompute(int sizeIdx) {
        int group = sizeIdx >> LOG2_SIZE_CLASS_GROUP;
        int mod = sizeIdx & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        int groupSize = group == 0 ? 0 :
                1 << LOG2_QUANTUM + LOG2_SIZE_CLASS_GROUP - 1 << group;

        int shift = group == 0 ? 1 : group;
        int lgDelta = shift + LOG2_QUANTUM - 1;
        int modSize = mod + 1 << lgDelta;

        return groupSize + modSize;
    }

    @Override
    public long pageIdx2size(int pageIdx) {
        return pageIdx2sizeTab[pageIdx];
    }

    @Override
    public long pageIdx2sizeCompute(int pageIdx) {
        int group = pageIdx >> LOG2_SIZE_CLASS_GROUP;
        int mod = pageIdx & (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        long groupSize = group == 0 ? 0 :
                1L << pageShifts + LOG2_SIZE_CLASS_GROUP - 1 << group;

        int shift = group == 0 ? 1 : group;
        int log2Delta = shift + pageShifts - 1;
        int modSize = mod + 1 << log2Delta;

        return groupSize + modSize;
    }

    @Override
    public int size2SizeIdx(int size) {
        // 计算分区寻址 chunk normal huge
        if (size == 0) {
            return 0;
        }

        // huge size
        if (size > chunkSize) {
            return nSizes;
        }
        // 调整size大小，将大小向上调整到最接近对齐的倍数。
        size = alignSizeIfNeeded(size, directMemoryCacheAlignment);
        // 4096b 以内直接从映射表中取，减少计算量
        if (size <= lookupMaxSize) {
            //size-1 / MIN_TINY
            // 16 -> 0 : value -> 16b
            // 32 -> 1 : value -> 32b
            // 反向查找sizeIdx
            return size2idxTab[size - 1 >> LOG2_QUANTUM];
        }

        // 出第一组外的sizeIdx
        // lastGroupSize = 1>>log2Group + nDelta<<log2Delta
        // log2Group = log2Delta + LOG2_SIZE_CLASS_GROUP
        // nDelta = 2^LOG2_SIZE_CLASS_GROU
        // log2Size = log2(size<<1-1)
        // log2Size = log2(2^(log2Group + 1))
        // log2Size = log2Group + 1
        // log2Delta = log2Group - LOG2_SIZE_CLASS_GROUP = log2Size - 1 - LOG2_SIZE_CLASS_GROUP
        int log2Size = log2((size << 1) - 1);
        // 第一组默认 0
        int shift = log2Size < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? 0 : log2Size - (LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM);
        // 组内所在第一个内存块索引
        int group = shift << LOG2_SIZE_CLASS_GROUP;

        // 第一组默认4
        int log2Delta = log2Size < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? LOG2_QUANTUM : log2Size - LOG2_SIZE_CLASS_GROUP - 1;
        //  计算掩码
        // index = group + mod
        // group 是该组得第一个内存块索引
        // mod 是偏移位置    也即是 nDelta - 1
        // size=(2^LOG2_SIZE_CLASS_GROUP + nDelta -1 + 1)* (1<<log2Delta)
        // 再进一步转换 将1提出来
        //size=(2^LOG2_SIZE_CLASS_GROUP + nDelta -1)*(1<<log2Delta)+(1<<log2Delta)
        //2^LOG2_SIZE_CLASS_GROUP + nDelta-1=(size-(1<<log2Delta))/(1<<log2Delta)
        // size - 1 是为了申请内存等于内存块size时避免分配到下一个内存块size中
        // & deltaInverseMask 将申请内存大小最后log2Delta个bit位设置为0,可以理解为 减去 1<<log2Delta
        // >> log2Delta   可以理解成  除以 1<<log2Delta
        // & (1 << LOG2_SIZE_CLASS_GROUP) - 1 取低LOG2_SIZE_CLASS_GROUP位数 等价与-2^LOG2_SIZE_CLASS_GROUP
        // mod = (size-(1<<log2Delta))/(1<<log2Delta) - 2^LOG2_SIZE_CLASS_GROUP
        //最终 mod = nDelta - 1
        int deltaInverseMask = -1 << log2Delta;
        int mod = (size - 1 & deltaInverseMask) >> log2Delta &
                (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        return group + mod;
    }

    @Override
    public int pages2pageIdx(int pages) {
        return pages2pageIdxCompute(pages, false);
    }

    @Override
    public int pages2pageIdxFloor(int pages) {
        return pages2pageIdxCompute(pages, true);
    }

    /**
     * 根据页面数量计算页面索引。
     * 此方法用于根据提供的页面数量和是否使用下界标志，计算出对应的页面索引。
     * 页面索引的计算涉及到页面大小的调整和基于位移的计算。
     * 算法与size2SizeIdx方法类似，但用途不同。
     *
     * @param pages 页面数量。
     * @param floor 是否使用下界标志。如果为true，则在计算页面索引后会检查是否需要向下调整索引。
     * @return 计算得到的页面索引。
     */
    protected int pages2pageIdxCompute(int pages, boolean floor) {
        // 计算页面大小
        int pageSize = pages << pageShifts;
        // 如果页面大小超过块大小，则返回最大索引值
        if (pageSize > chunkSize) {
            return nPSizes;
        }

        //对pageSize进行log2的向上取整
        int x = log2((pageSize << 1) - 1);

        //x >= LOG2_SIZE_CLASS_GROUP + pageShifts + 1后则每个size都是1<<pageShifts
        int shift = x < LOG2_SIZE_CLASS_GROUP + pageShifts
                ? 0 : x - (LOG2_SIZE_CLASS_GROUP + pageShifts);

        // 计算组索引
        int group = shift << LOG2_SIZE_CLASS_GROUP;

        // log2Delta 计算
        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + pageShifts + 1 ?
                pageShifts : x - LOG2_SIZE_CLASS_GROUP - 1;

        // 计算delta的反掩码，用于计算模数
        int deltaInverseMask = -1 << log2Delta;
        // 计算模数和索引的调整值
        int mod = (pageSize - 1 & deltaInverseMask) >> log2Delta &
                (1 << LOG2_SIZE_CLASS_GROUP) - 1;

        // 计算最终的页面索引
        int pageIdx = group + mod;

        // 如果使用下界且计算得到的页面大小大于原始页面数量，则调整页面索引
        if (floor && pageIdx2sizeTab[pageIdx] > pages << pageShifts) {
            pageIdx--;
        }
        return pageIdx;
    }


    /**
     * 将大小向上调整到最接近对齐的倍数。
     *
     * @param size                       原始大小，需要进行对齐调整的。
     * @param directMemoryCacheAlignment 对齐的倍数，如果小于等于0，则不进行对齐调整。
     * @return 调整后的大小。如果原始大小已经是对齐倍数的整数倍，则返回原始大小；否则返回调整后的大小。
     */
    private static int alignSizeIfNeeded(int size, int directMemoryCacheAlignment) {
        // 如果对齐倍数小于等于0，不进行对齐调整，直接返回原始大小
        if (directMemoryCacheAlignment <= 0) {
            return size;
        }
        // 计算原始大小与对齐倍数的模，即大小与对齐倍数之间的差值
        int delta = size & directMemoryCacheAlignment - 1;
        // 如果差值为0，表示原始大小已经是对齐倍数的整数倍，直接返回原始大小；否则调整大小使其成为对齐倍数的整数倍
        return delta == 0 ? size : size + directMemoryCacheAlignment - delta;
    }

    @Override
    public int normalizeSize(int size) {
        if (size == 0) {
            return sizeIdx2sizeTab[0];
        }
        // 调整大小，将大小向上调整到最接近对齐的倍数。
        size = alignSizeIfNeeded(size, directMemoryCacheAlignment);
        if (size <= lookupMaxSize) {
            int ret = sizeIdx2sizeTab[size2idxTab[size - 1 >> LOG2_QUANTUM]];
            assert ret == normalizeSizeCompute(size);
            return ret;
        }
        return normalizeSizeCompute(size);
    }

    private static int normalizeSizeCompute(int size) {
        int x = log2((size << 1) - 1);
        int log2Delta = x < LOG2_SIZE_CLASS_GROUP + LOG2_QUANTUM + 1
                ? LOG2_QUANTUM : x - LOG2_SIZE_CLASS_GROUP - 1;
        int delta = 1 << log2Delta;
        int delta_mask = delta - 1;
        return size + delta_mask & ~delta_mask;
    }

    static int log2(int val) {
        return 31 - Integer.numberOfLeadingZeros(val);
    }
}
