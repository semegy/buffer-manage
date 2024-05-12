import buffer.ByteBuf;
import buffer.pool.PoolArena;
import buffer.pool.PooleDirectByteBuf;
import buffer.pool.PooledBufferAllocate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class PoolArenaTest {


    private PoolArena<Object> arena;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        PooledBufferAllocate bufferAllocate = new PooledBufferAllocate(32, 8192, 11, 256, 64, true);
        // 假设 PoolArena 有一个公共构造函数，用于实例化对象
        arena = new PoolArena<>(1024 * 8, 13, 1024 * 1024 * 16, bufferAllocate, 0);
    }

    @Test
    public void testAllocate() {
        int reqCapacity = 1024 * 30;
        int maxCapacity = Integer.MAX_VALUE;
        PooledBufferAllocate bufferAllocate = new PooledBufferAllocate(32, 8192, 11, 256, 64, true);
        for (int i = 0; i < 10; i++) {
            // 测试 allocate 方法的行为
            ByteBuf allocatedBuffer = bufferAllocate.newDirectBuffer(reqCapacity, maxCapacity);
            PooleDirectByteBuf p = (PooleDirectByteBuf) allocatedBuffer;
            p.deallocate();
        }
    }

}
