package com.service;

import buffer.ByteBuf;
import channel.ChannelContext;
import channel.message.MessageToByteEncoder;
import src.main.java.common.Hessian2Serialize;

import java.io.Serializable;

import static channel.ChannelContainer.bufferAllocate;

public class RpcEncoder extends MessageToByteEncoder<Serializable> {


    public RpcEncoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected ByteBuf encode(ChannelContext ctx, Serializable out) {
        byte[] serialize = Hessian2Serialize.serialize(out);
        ByteBuf buf = bufferAllocate.ioBuffer(serialize.length + initialBytesToStrip);
        try {
            buf.writeBytes(serialize, lengthFieldLength, serialize.length);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        buf.setReadIndex(initialBytesToStrip);
        buf.setWriterIndex(serialize.length + initialBytesToStrip);
        buf.setLength(serialize.length);
        super.encode(ctx, buf);
        return buf;
    }
}
