# Blazingly Fast Reactive Generic Server
This project is a high-performance, multithreaded TCP server implemented in Kotlin. By leveraging the NIO Selector, AtomicReferences, and the Reactor Design Pattern, this server ensures efficient handling of multiple clients with minimal overhead. It is designed to be highly versatile, making it suitable for a wide range of applications.

This server is the implementation of material studied at the Ben Gurion University System Programming Lab course.

## Goals
I am not aiming to replace any production-ready standard servers available elsewhere. The primary goal of this project was to expand my knowledge and add Kotlin to my toolbox. You might notice in the code that I still use semicolons (sorry, old habits die hard). Additionally, some of the functions I call are not safely handled despite being aware that they might throw exceptions. The exception-handling mechanism is something I haven’t fully explored yet. I plan to revisit this project in the future to address these issues.

Additionally, it is nice to have a generic server ready to use, which you can understand (the server's implementation is fairly short) and predict its performance. So who knows? maybe I'll end up using it someday.

## Usage & Set Up
To get started, you need to decide on the encoding format your server will use (e.g., UTF-8, UTF-16, etc.). Since network communication transmits data as raw bytes, each byte received must be decoded into a meaningful format. Once a complete message has been successfully decoded, it should be passed to the server for processing. Similarly, before sending a response, the data must be properly encoded into bytes and transmitted to the client. This process is defined by implementing `reactor.api.EncoderDecoder`:
```kotlin
import reactor.api.EncoderDecoder

// Server which receives and returns strings. 
// In general, you could define different types for requests and responses for your server.
class StringEncoderDecoder : EncoderDecoder<String, String> {

    // A way to track received data and built a message from it:
    private val msgBuilder = StringBuffer()

    // Encode: Convert the response (String) to a ByteArray
    override fun encode(decoded: String): ByteArray {
        return decoded.toByteArray(Charsets.UTF_8)
    }

    // Decode: Convert a Byte to a String
    override fun decode(encoded: Byte): String? {
        if (encoded.toInt().toChar() == '\n') {
            msgBuilder.append('\n')
            val res = msgBuilder.toString()
            msgBuilder.setLength(0)
            return res
        }
        msgBuilder.append(encoded.toInt().toChar())
        return null
    }
}
```

Keep in mind that this is a generic server, meaning no specific protocol (such as HTTP, FTP, SMTP, etc.) is predefined. The communication method with individual clients is entirely up to you. To define the desired behavior, you need to implement the `reactor.api.Protocol` interface.
```kotlin
import reactor.api.Protocol

class EchoProtocol : Protocol<String, String> {
    private var terminate = false

    // Define what happens with each and every message
    // or request:
    override fun process(message: String): String? {
        if(terminate) return null
        // Define under what circumstances the connection should be closed:
        if(message == "BYE") terminate = true
        return message
    }
    
    override fun shouldTerminate(): Boolean {
        return terminate
    }
}
```
That's it! the server is practically ready, you can start by creating your server:
```kotlin
val server = ReactorServer<String, String>(
    8 /*Number of worker threads*/, 3000 /*Port*/,
    /*EncoderDecoder & Protocol Suppliers:*/
    { StringEncoderDecoder() }, { EchoProtocol() }, 
    /*Whether to use blocking mechanisms for synchronization:*/
    blockingSync = false // false by default.
)
    
// Start the server, the calling thread is blocked:
server.serve()

// ... In some other thread:
server.close()
```
Let's go over what happens here really quick:
1. Determine the number of worker threads that will operate alongside the IO thread/server thread. In this case, it's set to `8`.
2. Choose a port for the socket to listen on, such as `3000`. (`TODO` - add a hostname setting).
3. The server provides each connection with its own `EncoderDecoder` and `Protocol` instances. This is achieved using the `Supplier` functional interface. By using a lambda expression, we supply two simple `Supplier` implementations that return new instances.
4. `blockingSync` determines whether to use a thread pool (`reactor.core.ActorThreadPool`) that relies on the `synchronized` mechanism for task synchronization. The thread pool employed by the reactor involves intricate synchronization to maintain fairness and availability. The `synchronized` pool (`reactor.core.SynchronizedActorThreadPool`) blocks threads attempting to access synchronized objects simultaneously, leading to two context switch operations by the operating system per blocked thread. Blocking prevents efficient utilization of the hardware and context switches are costly since they require a system call, it is generally preferable to avoid blocking threads whenever possible. This is achieved through the use of AtomicReferences in the `reactor.core.NonBlockingActorThreadPool`. Personally, I don't see any valid reason to set this true, unless your hardware/ os don't support atomic compare and set instructions/ operations.
5. Then server is started by calling `serve()`. Note that if you need the current thread available, you should not call `serve()`, since it will block the caller thread.

