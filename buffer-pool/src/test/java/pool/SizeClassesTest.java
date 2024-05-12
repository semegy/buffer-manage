package pool;

import buffer.pool.SizeClasses;
import org.junit.Test;

public class SizeClassesTest {

    private static class TestableSizeClasses extends SizeClasses {

        private final int LOG2_SIZE_CLASS_GROUP = 2;
        private final int LOG2_QUANTUM = 4;

        protected TestableSizeClasses(int pageSize, int pageShifts, int chunkSize, int directMemoryCacheAlignment) {
            super(pageSize, pageShifts, chunkSize, directMemoryCacheAlignment);
        }

        // 暴露pages2pageIdxCompute方法供测试
        protected int testPages2pageIdxCompute(int pages, boolean floor) {
            return super.pages2pageIdxCompute(pages, floor);
        }

        protected int testSize2SizeIdx(int size) {
            return super.size2SizeIdx(size);
        }
    }

    /**
     * 实例化SizeClasses的测试子类
     */
    private static TestableSizeClasses sizeClasses = new TestableSizeClasses(1024 * 8, 13, 1024 * 1024 * 16, 0);

    @Test
    public void testPages2pageIdxCompute() {
        // 测试用例 #2: 当页面数量大于块大小时
        for (int i = 1; i <= 2048; i++) {
            sizeClasses.testPages2pageIdxCompute(i, false);
        }
        // 更多测试用例...
    }

    @Test
    public void testSize2SizeIdx() {

        // 测试用例 #2: 当页面数量大于块大小时
        sizeClasses.testSize2SizeIdx(2048);

        sizeClasses.testSize2SizeIdx(2048 * 512);

        // 更多测试用例...
    }
}
