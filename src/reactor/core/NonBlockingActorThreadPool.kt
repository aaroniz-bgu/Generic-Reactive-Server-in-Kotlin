package reactor.core

import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Superfast implementation of task based thread pool. Does not use synchronized directly, uses atomic references
 * to better manage task providers and their tasks.
 * The only blocking part of it is with ReentrantReadWriteLock with small probability.
 *
 * @param threads number of worker threads to initiate and manage.
 */
internal class NonBlockingActorThreadPool<T>(threads: Int) : ActorThreadPool<T> {
    private val _pool: ExecutorService = Executors.newFixedThreadPool(threads);
    private val _actsRWLock = ReentrantReadWriteLock();
    private val _acts: MutableMap<T, Actor> = WeakHashMap();

    private fun getActor(act: T): Actor {
        _actsRWLock.readLock().lock();
        var actor: Actor? = _acts[act];
        _actsRWLock.readLock().unlock();

        if(actor != null) {
            return actor;
        } else {
            actor = Actor();
            _actsRWLock.readLock().lock();
            _acts[act] = actor;
            _actsRWLock.readLock().unlock();
            return actor;
        }
    }

    override fun submit(act: T, r: Runnable) {
        getActor(act).add(r);
    }

    override fun shutdown() {
        _pool.shutdown();
    }

    private class ExecutionState(var tasksLeft: Int, var playingNow: Boolean);

    private inner class Actor : Runnable {
        private var _state = AtomicReference(ExecutionState(0, false));
        private val _tasks = ConcurrentLinkedQueue<Runnable>();

        override fun run() {
            val newState = ExecutionState(0, false);
            var oldState: ExecutionState;
            var tasksDone = 0;

            do {
                oldState = _state.get();
                while(tasksDone < oldState.tasksLeft) {
                    _tasks.remove().run();
                    tasksDone++;
                }
            } while (!_state.compareAndSet(oldState, newState));
        }

        fun add(r: Runnable) {
            _tasks.add(r);
            var oldState = _state.get();
            val newState = ExecutionState(oldState.tasksLeft + 1, true);

            while(!_state.compareAndSet(oldState, newState)) {
                oldState = _state.get();
                newState.tasksLeft = oldState.tasksLeft + 1;
            }

            if(!oldState.playingNow) {
                _pool.submit(this);
            }
        }
    }
}
