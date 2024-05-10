package pool;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;

public class PoolArenaTest {

    private PoolArena<Object> poolArena;
    private Method newInstanceMethod;

    @Before
    public void setUp() throws Exception {
        poolArena = new PoolArena<>(1024 * 8, 13, 1024 * 1024 * 16, this, 0);
        // 使用反射获取私有方法
        newInstanceMethod = PoolArena.class.getDeclaredMethod("newInstance", int.class);
        newInstanceMethod.setAccessible(true);
    }

    @Test
    public void testNewInstance() throws Exception {
        for (int i = 0; i < 10; i++) {
            PooledByteBuf<Object> instance = (PooledByteBuf<Object>) newInstanceMethod.invoke(poolArena, 10);
            // 验证结果是否正确
            assertNotNull(instance);
        }
    }
}
