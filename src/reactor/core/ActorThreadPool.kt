package reactor.core

/**
 * A thread pool that ensures fairness and maintains chronological order in handling incoming requests.
 * Every client (Actor) is handled by a single thread at most in any given moment.
 * @param T Connection handler which represents a single client. =Actor.
 */
internal interface ActorThreadPool<T> {
    /**
     * Submits a task from the given actor.
     * @param act the actor submitting a task which it wants to complete.
     * @param r the task.
     */
    fun submit(act: T, r: Runnable);

    /**
     * Closes this thread pool and frees its resources.
     */
    fun shutdown();
}
