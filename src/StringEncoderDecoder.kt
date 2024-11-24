import reactor.api.EncoderDecoder

class StringEncoderDecoder : EncoderDecoder<String, String> {

    private val msgBuilder = StringBuffer();

    /**
     * Encode: Convert the response (String) to a ByteArray
     */
    override fun encode(decoded: String): ByteArray {
        return decoded.toByteArray(Charsets.UTF_8);
    }

    // Decode: Convert a Byte to a String (assuming single byte is a character for simplicity)
    override fun decode(encoded: Byte): String? {
        if (encoded.toInt().toChar() == '\n') {
            msgBuilder.append('\n');
            val res = msgBuilder.toString();
            msgBuilder.setLength(0);
            return res;
        }
        msgBuilder.append(encoded.toInt().toChar());
        return null;
    }
}