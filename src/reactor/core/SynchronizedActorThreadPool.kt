package reactor.core

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Uses synchronization with blocking methods usually slower and more costly than non-blocking atomic operations
 * Better to use NonBlockingActorThreadPool.
 * @see core.NonBlockingActorThreadPool
 * @param threads number of worker threads to initiate and manage.
 */
internal class SynchronizedActorThreadPool<T : Any>(threads: Int) : ActorThreadPool<T> {
    private val _pool: ExecutorService = Executors.newFixedThreadPool(threads);
    private val _acts: MutableMap<T, Queue<Runnable>> = WeakHashMap();
    private val _playingNow: MutableSet<T> = ConcurrentHashMap.newKeySet();
    private val _actsRWLock = ReentrantReadWriteLock();

    override fun submit(act: T, r: Runnable) {
        TODO("Not yet implemented")
    }

    override fun shutdown() {
        _pool.shutdown();
    }

    private fun pendingRunnablesOf(act: T): Queue<Runnable>? {
        _actsRWLock.readLock().lock();
        var pendingRunnables: Queue<Runnable>? = _acts[act];
        _actsRWLock.readLock().unlock();

        if (pendingRunnables == null) {
            _actsRWLock.writeLock().lock();
            _acts[act] = LinkedList<Runnable>().also { pendingRunnables = it };
            _actsRWLock.writeLock().unlock();
        }
        return pendingRunnables
    }

    private fun execute(r: Runnable, act: T) {
        _pool.submit {
            try {
                r.run()
            } finally {
                complete(act)
            }
        }
    }

    private fun complete(act: T) {
        synchronized(act) {
            val pending = pendingRunnablesOf(act)
            if (pending!!.isEmpty()) {
                _playingNow.remove(act)
            } else {
                execute(pending.poll(), act)
            }
        }
    }
}
