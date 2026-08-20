package com.inspyresoftworks.ti84evo.protocol

/**
 * Base exception for TI-84 Evo protocol failures.
 *
 * Author: Taylor B. | Inspyre-Softworks.
 */
open class EvoProtocolException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class EvoFrameException(message: String) : EvoProtocolException(message)
class EvoTimeoutException(message: String) : EvoProtocolException(message)
class EvoUnexpectedFrameException(message: String) : EvoProtocolException(message)
class EvoUnsupportedException(message: String) : EvoProtocolException(message)
