import reactor.api.Protocol

class EchoProtocol : Protocol<String, String> {
    private var terminate = false;

    override fun process(message: String): String? {
        if(terminate) return null;
        if(message == "BYE") terminate = true;
        return message;
    }

    override fun shouldTerminate(): Boolean {
        return terminate;
    }
}