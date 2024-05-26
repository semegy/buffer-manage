package rpc.proxy;

import buffer.ByteBuf;
import channel.ChannelContext;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import src.main.java.common.Hessian2Serialize;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.when;
import static rpc.proxy.DefaultFuture.FUTURES;

public class RpcHandlerTest {
    private RpcDecoder rpcHandler;

    @Mock
    private ChannelContext context;
    @Mock
    private ByteBuf byteBuf;
    @Mock
    private AppResponse appResponse;
    @Mock
    private DefaultFuture<AppResponse> defaultFuture;
    @Mock
    private Thread responseTask;

    public void setUp() {
        MockitoAnnotations.initMocks(this);
        rpcHandler = new RpcDecoder(Integer.MAX_VALUE, 0, 2, 0, 2);
        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(1);
        defaultFuture = new DefaultFuture(0);

    }

    @Test
    public void testHandle() throws ExecutionException, InterruptedException {
        setUp();
        // Prepare the test data

        long requestId = 123;
        AppResponse response = new AppResponse(requestId);
        response.setValue("1234");
        byte[] serialize = Hessian2Serialize.serialize(response);
        when(byteBuf.readBytes()).thenReturn(serialize);
        when(appResponse.getRequestId()).thenReturn(requestId);
        FUTURES.put(requestId, defaultFuture);
        defaultFuture.runAsync(defaultFuture.getResponseTask());
        // Call the method to test
        rpcHandler.handle(context, byteBuf);

        // Verify the interactions
        try {
            Object o = defaultFuture.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
