package reactor.core

import reactor.api.EncoderDecoder
import reactor.api.Protocol
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents the communication with a single client.
 */
internal class NonBlockingConnectionHandler<T, R> (
    private val _encdec: EncoderDecoder<T, R>,
    private val _prot: Protocol<T, R>,
    private val _chan: SocketChannel,
    private val _serv: ReactorServer<T, R>
) : ConnectionHandler<T, R> {

    private val _writeQueue: Queue<ByteBuffer> = ConcurrentLinkedQueue();

    override fun continueWrite() {
        while(!_writeQueue.isEmpty()) {
            try {
                val top: ByteBuffer = _writeQueue.peek();
                _chan.write(top);
                if(top.hasRemaining()) {
                    return;
                } else {
                    _writeQueue.remove();
                }
            } catch (ex: IOException) {
                // Log someday
                close();
            }
        }

        if(_writeQueue.isEmpty()) {
            if (_prot.shouldTerminate()) close();
            else _serv.updateInterestedOps(_chan, SelectionKey.OP_READ);
            // Since the thread pool allows only one thread per actor it's impossible for
            // more results to be added to the _writeQueue until the else call.
        }
    }

    override fun continueRead(): Runnable? {
        val buff: ByteBuffer = leaseBuffer();

        var success = false;
        try {
            success = _chan.read(buff) != -1;
        } catch (ex: ClosedByInterruptException) {
            Thread.currentThread().interrupt();
        } catch (ex: IOException) {
            // Ignore atm
        }

        if(success) {
            buff.flip();
            return Runnable {
                try {
                    while (buff.hasRemaining()) {
                        val request: T? = _encdec.decode(buff.get());
                        request?.let {
                            val response: R? = _prot.process(request);
                            response?.let {
                                _writeQueue.add(ByteBuffer.wrap(_encdec.encode(response)));
                                _serv.updateInterestedOps(_chan, SelectionKey.OP_READ or SelectionKey.OP_WRITE);
                            }
                        }
                    }
                } finally {
                    releaseBuffer(buff);
                }
            }
        } else {
            releaseBuffer(buff);
            close();
            return null;
        }
    }

    override fun close() {
        try {
            _chan.close();
        } catch (ex: IOException) {
            // Ignore, later log
        }
    }
}
