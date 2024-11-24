package reactor.api

interface Protocol<T, R> {
    fun process(message: T): R?;
    fun shouldTerminate(): Boolean;
}
