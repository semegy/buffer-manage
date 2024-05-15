package nio;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

public class EventHandlerContext {

    EventHandler eventHandler;

    ReactorEndpoint endpoint;

    ReactorEndpoint nextEndpoint;

    HandlerInvoker invoker;

    public static ConcurrentHashMap<String, SocketChannel> clientMap = new ConcurrentHashMap<>();

    public EventHandlerContext(EventHandler eventHandler, ReactorEndpoint endpoint, ReactorEndpoint nextEndpoint, HandlerInvoker invoker) {
        this.eventHandler = eventHandler;
        this.endpoint = endpoint;
        this.nextEndpoint = nextEndpoint;
        this.invoker = invoker;
    }

    public EventHandler handle() {
        return eventHandler;
    }

    public void invokerRead(ChannelWrapper channel) {
        try {
            this.invoker.invokerRead(channel, this.eventHandler);
        } catch (IOException e) {
            this.invoker.invokerException(channel, e, this.eventHandler);
        }
    }

    public void invokerAccept(ChannelWrapper channel) throws IOException {
        this.invoker.invokerAccept(channel, this);
    }

    public void invokerWrite(ChannelWrapper channelWrapper) throws IOException {
        this.invoker.invokerWrite(channelWrapper, this.eventHandler);
    }
}
