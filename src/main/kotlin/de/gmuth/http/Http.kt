package de.gmuth.http

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import java.io.InputStream
import java.net.URI

interface Http {

    class Content(val type: String, val stream: InputStream)
    class Response(val status: Int, val content: Content)

    fun post(uri: URI, content: Content): Response

}