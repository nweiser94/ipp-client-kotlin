package de.gmuth.ipp.client

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import de.gmuth.http.Http
import de.gmuth.http.HttpClientByHttpURLConnection
import de.gmuth.ipp.core.*
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.URI

class IppClient(
        val printerUri: URI,
        private val httpClient: Http.Client = HttpClientByHttpURLConnection()
        //private val httpClient: Http.Client = HttpClientByJava11HttpClient()
) {
    var verbose: Boolean = true

    fun exchangeIpp(ippRequest: IppRequest, documentInputStream: InputStream? = null): IppResponse {
        val ippResponseStream = with(ippRequest) {
            println("send ${operation} request to $printerUri")
            println(this)
            if (verbose) logDetails(">> ")
            exchangeIpp(toInputStream(), documentInputStream)
        }
        println("read ipp response")
        with(IppResponse.fromInputStream(ippResponseStream)) {
            if (verbose) logDetails("<< ")
            println(this)
            if (statusMessage != null) println("status-message: $statusMessage")
            return this
        }
    }

    private fun exchangeIpp(ippRequestStream: InputStream, documentInputStream: InputStream? = null): InputStream {
        val ippContentType = "application/ipp"
        val ippRequestContent = Http.Content(
                ippContentType,
                if (documentInputStream == null) ippRequestStream
                else SequenceInputStream(ippRequestStream, documentInputStream)
        )
        val httpUri = with(printerUri) { URI.create("${scheme.replace("ipp", "http")}:${schemeSpecificPart}") }
        with(httpClient.post(httpUri, ippRequestContent)) {
            if (status == 200 && content.type == ippContentType) {
                return content.stream

            } else {
                val text = if (content.type.startsWith("text")) ", content = " + String(content.stream.readAllBytes()) else ""
                throw IOException("response from $printerUri is invalid: http-status = $status, content-type = ${content.type}$text")
            }
        }
    }

    fun printDocument(
            inputStream: InputStream,
            documentFormat: String? = "application/octet-stream",
            userName: String? = "ipp-client-kotlin"

    ): IppJob {

        val ippRequest = IppRequest(IppOperation.PrintJob).apply {
            addOperationAttribute("printer-uri", IppTag.Uri, "$printerUri")
            addOperationAttribute("document-format", IppTag.MimeMediaType, documentFormat)
            addOperationAttribute("requesting-user-name", IppTag.NameWithoutLanguage, userName)
        }

        val ippResponse = exchangeIpp(ippRequest, inputStream)
        if (ippResponse.status.isSuccessful()) {
            val ippJobGroup = ippResponse.getSingleJobGroup()
            val ippJob = IppJob.fromIppAttributesGroup(ippJobGroup)
            println(ippJob)
            return ippJob

        } else {
            throw RuntimeException("printing to $printerUri failed")
        }
    }
}