package de.gmuth.ipp.core

/**
 * Copyright (c) 2020-2023 Gerhard Muth
 */

import de.gmuth.ipp.core.IppOperation.CreateJobSubscriptions
import java.net.URI
import java.time.Duration
import java.util.logging.Logger.getLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class IppRequestTests {

    val log = getLogger(javaClass.name)

    @Test
    fun requestConstructor1() {
        IppRequest().run {
            code = 5
            log.info { toString() }
            log(log)
            assertEquals(null, version)
            assertEquals(IppOperation.CreateJob, operation)
            createAttributesGroup(IppTag.Operation)
            assertFailsWith<IppException> { printerOrJobUri }
        }
    }

    @Test
    fun requestConstructor2() {
        val request = IppRequest(IppOperation.StartupPrinter, URI.create("ipp://foo"))
        assertEquals(1, request.requestId)
        assertEquals("2.0", request.version)
        assertEquals(IppOperation.StartupPrinter, request.operation)
        assertEquals(Charsets.UTF_8, request.attributesCharset)
        assertEquals("en", request.operationGroup.getValue("attributes-natural-language"))
        assertEquals("ipp://foo", request.printerOrJobUri.toString())
        assertEquals("Startup-Printer", request.codeDescription)
        val requestEncoded = request.encode()
        assertEquals(97, requestEncoded.size)
    }

    @Test
    fun printJobRequest() {
        val request = IppRequest(
            IppOperation.PrintJob, URI.create("ipp://printer"),
            listOf("one", "two"), "user"
        )
        request.documentInputStream = "pdl-content".byteInputStream()
        log.info { request.toString() }
        request.log(log)
        val requestEncoded = request.encode()
        log.info { "encoded ${requestEncoded.size} bytes" }
        val requestDecoded = IppRequest()
        requestDecoded.decode(requestEncoded)
        assertEquals("2.0", requestDecoded.version)
        assertEquals(IppOperation.PrintJob, requestDecoded.operation)
        assertEquals(1, requestDecoded.requestId)
        assertNotNull(requestDecoded.operationGroup)
        with(requestDecoded.operationGroup) {
            assertEquals(Charsets.UTF_8, getValue("attributes-charset"))
            assertEquals("en", getValue("attributes-natural-language"))
            assertEquals(URI.create("ipp://printer"), getValue("printer-uri"))
            //assertEquals(0, getValue("job-id"))
            assertEquals(listOf("one", "two"), getValues("requested-attributes"))
            //assertEquals("user".toIppString(), getValue("requesting-user-name"))
        }
        assertEquals("user", requestDecoded.requestingUserName)
        assertEquals("pdl-content", String(requestDecoded.documentInputStream!!.readBytes()))
    }

    @Test
    fun createSubscriptionAttributesGroup() {
        IppRequest(CreateJobSubscriptions, URI.create("ipp://foo"))
            .createSubscriptionAttributesGroup(
                listOf("all"),
                Duration.ofHours(1),
                Duration.ofMinutes(1),
                999
            )
    }

    @Test
    fun createSubscriptionAttributesGroupWithNullDefaults() {
        IppRequest(CreateJobSubscriptions, URI.create("ipp://null"))
            .createSubscriptionAttributesGroup()
    }

}