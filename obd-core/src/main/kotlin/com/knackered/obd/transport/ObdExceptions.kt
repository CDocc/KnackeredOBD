package com.knackered.obd.transport

class ObdTimeoutException(message: String) : Exception(message)

class ObdProtocolException(
    message: String,
    val raw: String,
) : Exception(message)

/** A command outside the v1 read-only allow-list, including unexpected Mode 04. */
class ObdNotAllowedException(message: String) : Exception(message)
