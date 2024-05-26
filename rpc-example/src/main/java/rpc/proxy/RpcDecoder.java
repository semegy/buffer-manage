package rpc.proxy;

import buffer.ByteBuf;
import channel.ChannelContext;
import channel.message.ByteToMessageDecoder;
import com.ServiceBean;
import src.main.java.common.Hessian2Serialize;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ServiceBean.serviceMap;
import static rpc.proxy.DefaultFuture.FUTURES;

public class RpcDecoder extends ByteToMessageDecoder {

    ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(1);

    public RpcDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected void handle(ChannelContext context, ByteBuf byteBuf) {
        byte[] bytes = byteBuf.readBytes();
        Object deserialize = Hessian2Serialize.deserialize(bytes);
        if (deserialize instanceof Request) {
            threadPoolExecutor.execute(() -> {
                Request request = (Request) deserialize;
                ServiceBean serviceBean = serviceMap.get(request.getInterfaceName());
                Object invoke = serviceBean.invoke(request.getMethodIndex(), request.params);
                AppResponse appResponse = new AppResponse();
                appResponse.setValue(invoke);
                appResponse.setRequestId(request.getRequestId());
                context.write(appResponse);
            });
        } else if (deserialize instanceof AppResponse) {
            AppResponse appResponse = Hessian2Serialize.deserialize(bytes);
            DefaultFuture defaultFuture = FUTURES.get(appResponse.getRequestId());
            defaultFuture.obtrudeValue(appResponse);
        }
    }
}