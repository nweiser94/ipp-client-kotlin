package de.gmuth.ipp.core

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import de.gmuth.ipp.client.IppExchangeException
import java.util.logging.Logger.getLogger
import kotlin.test.Test
import kotlin.test.assertEquals

class IppExchangeExceptionTests {

    val log = getLogger(javaClass.name)

    @Test
    fun constructor() {
        with(IppExchangeException(
                IppRequest(IppOperation.GetPrinterAttributes).apply { encode() },
                null, 400
        )) {
            log(log)
            assertEquals(11, request.code)
            assertEquals(400, httpStatus)
            assertEquals(message, "Get-Printer-Attributes failed")
        }
    }

}