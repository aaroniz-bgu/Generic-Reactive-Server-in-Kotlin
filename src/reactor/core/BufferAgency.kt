package reactor.core

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * "Static"=Top Level buffer pool.
 * Not limited.
 *
 * Don't ask me why I called it an agency.
 */

private const val BUFFER_ALLOC_SIZE: Int = 1 shl 13;
private val BUFFER_POOL = ConcurrentLinkedQueue<ByteBuffer>();

fun leaseBuffer(): ByteBuffer {
    val buff: ByteBuffer? = BUFFER_POOL.poll();
    return buff?.apply {
        buff.clear()
    } ?: ByteBuffer.allocateDirect(BUFFER_ALLOC_SIZE);
}

fun releaseBuffer(buff: ByteBuffer) {
    BUFFER_POOL.add(buff);
}
