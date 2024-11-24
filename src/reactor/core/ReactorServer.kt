package reactor.core;

import reactor.api.EncoderDecoder;
import reactor.api.Protocol;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*
import java.nio.channels.Selector.open;
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier;
import kotlin.jvm.Throws

internal typealias ConnectionPool<T, R> = ActorThreadPool<NonBlockingConnectionHandler<T, R>>;
internal typealias EncoderSupplier<T, R> = Supplier<EncoderDecoder<T, R>>;
internal typealias ProtocolSupplier<T, R> = Supplier<Protocol<T, R>>;

/**
 * Reactor Implementation of generic TCP server.
 * @param threads Number of worker threads to allocate for this server.
 * @param blockingSync Whether to use blocking synchronization or not. Preferably (for better performing server) and
 *                     by default set to false.
 * @param _port Server's port.
 * @param _protSupp Communication Protocol Supplier.
 * @param _encdecSupp Encoding & Decoding Protocol Supplier.
 */
class ReactorServer<T, R> (
    threads: Int,
    private val _port: Int,
    private val _encdecSupp: EncoderSupplier<T, R>,
    private val _protSupp: ProtocolSupplier<T, R>,
    blockingSync: Boolean = false
): Server<T, R> {

    private val _pool: ConnectionPool<T, R> =
        if (blockingSync) SynchronizedActorThreadPool(threads)
        else NonBlockingActorThreadPool(threads);

    private val _selectorTask = ConcurrentLinkedQueue<Runnable>();

    private var _selectorThread: Thread? = null;
    private var _selector: Selector? = null;

    /**
     * Start the server. Once called the current thread is blocked.
     */
    override fun serve() {
        _selectorThread = Thread.currentThread();

        try {
            open().use  { selector ->
                this._selector = selector;
                ServerSocketChannel.open().use { serverSock ->

                    serverSock.bind(InetSocketAddress(_port));
                    serverSock.configureBlocking(false);
                    serverSock.register(selector, SelectionKey.OP_ACCEPT);

                    while(!_selectorThread?.isInterrupted!!) {
                        selector.select();
                        runSelectionThreadTasks();

                        for (key: SelectionKey in selector.selectedKeys()) {
                            if (!key.isValid) {
                                continue;
                            } else if (key.isAcceptable) {
                                handleAccept(serverSock, selector);
                            } else {
                                handleReadWrite(key);
                            }
                        }

                        selector.selectedKeys().clear();
                    }
                }
            }
        } catch (ex: ClosedSelectorException) {
            // Ignore atm
        } catch (ex: IOException) {
            // Ignore atm
        } finally {
            _pool.shutdown();
        }
    }

    internal fun updateInterestedOps(chan: SocketChannel, ops: Int) {
        val key: SelectionKey = chan.keyFor(_selector);
        if(Thread.currentThread() == _selectorThread) {
            key.interestOps(ops);
        } else {
            // Adds the task so the selector thread will do it later and wakes it up in case it's selecting rn:
            _selectorTask.add {
                if (key.isValid) {
                    key.interestOps(ops);
                }
            };

            _selector?.wakeup();
        }
    }

    private fun handleAccept(serverChan: ServerSocketChannel, selector: Selector) {
        val clientChan: SocketChannel = serverChan.accept();
        clientChan.configureBlocking(false);

        // TODO check insert sync/non-blocking mechanism here
        val handler: ConnectionHandler<T, R> = NonBlockingConnectionHandler<T, R>(
            _encdecSupp.get(), _protSupp.get(), clientChan, this);

        clientChan.register(selector, SelectionKey.OP_READ, handler);
    }

    private fun handleReadWrite(key: SelectionKey) {
        // Retrieve the attachment
        val handler = key.attachment() as NonBlockingConnectionHandler<T, R>;

        try {
            if(key.isReadable) {
                val task: Runnable? = handler.continueRead();
                task?.let {
                    _pool.submit(handler, task);
                }
            }

            if (key.isWritable) {
                handler.continueWrite();
            }
        } catch (ex: CancelledKeyException) {
            // Ignore and one day I will log this
        }

    }

    private fun runSelectionThreadTasks() {
        while(!_selectorTask.isEmpty()) _selectorTask.remove().run();
    }

    /**
     * Closes / Shuts down this server.
     */
    @Throws(IOException::class)
    override fun close() {
        _selector?.close();
    }
}
