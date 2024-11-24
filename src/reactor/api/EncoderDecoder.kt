package reactor.api

interface EncoderDecoder <T, R> {
    fun encode(decoded: R): ByteArray;
    fun decode(encoded: Byte): T?;
}
