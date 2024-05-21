package buffer.pool;

import buffer.recycle.Recycler;

import java.nio.ByteBuffer;

public class PooleDirectByteBuf extends PooledByteBuf<ByteBuffer> {


    public PooleDirectByteBuf(Recycler.Handle recyclerHandle) {
        super(recyclerHandle);
    }

    @Override
    public int capacity() {
        return this.length;
    }

    @Override
    public void setLength(int length) {
        this.length = length;
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(ByteBuffer memory) {
        return memory.duplicate();
    }
}
