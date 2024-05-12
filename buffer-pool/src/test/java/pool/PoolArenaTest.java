package pool;

import buffer.pool.PoolArena;
import buffer.pool.PooleDirectByteBuf;
import buffer.pool.PooledBufferAllocate;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertNotNull;

public class PoolArenaTest {

    private PoolArena<ByteBuffer> poolArena;
    private Method newInstanceMethod;

    @Before
    public void setUp() throws Exception {
        PooledBufferAllocate bufferAllocate = new PooledBufferAllocate(32, 8192, 11, 256, 64, true);
        poolArena = new PoolArena<>(1024 * 8, 13, 1024 * 1024 * 16, bufferAllocate, 0);
        // 使用反射获取私有方法
        newInstanceMethod = PoolArena.class.getDeclaredMethod("newInstance", int.class);
        newInstanceMethod.setAccessible(true);
    }

    @Test
    public void testNewInstance() throws Exception {
        for (int i = 0; i < 10; i++) {
            PooleDirectByteBuf instance = (PooleDirectByteBuf) newInstanceMethod.invoke(poolArena, 10);
            // 验证结果是否正确
            assertNotNull(instance);
        }
    }
}
