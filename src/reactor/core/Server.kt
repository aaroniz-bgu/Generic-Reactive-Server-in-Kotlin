package reactor.core

/**
 * TCP Server.
 */
internal interface Server<T, R> {
    /**
     * Starts the current server.
     * Blocks the calling thread.
     */
    fun serve();

    /**
     * Closes this server and frees its resources.
     */
    fun close();
}
