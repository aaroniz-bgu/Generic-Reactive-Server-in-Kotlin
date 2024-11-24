package reactor.core

/**
 * Controls/represents a single connection between the server and client.
 */
internal interface ConnectionHandler<T, R> {
    fun continueRead(): Runnable?;
    fun continueWrite();
    fun close();
}
