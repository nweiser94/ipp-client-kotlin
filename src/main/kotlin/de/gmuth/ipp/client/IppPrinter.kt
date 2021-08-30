package de.gmuth.ipp.client

/**
 * Copyright (c) 2020-2021 Gerhard Muth
 */

import de.gmuth.http.Http
import de.gmuth.http.HttpURLConnectionClient
import de.gmuth.ipp.client.IppPrinterState.*
import de.gmuth.ipp.core.*
import de.gmuth.ipp.core.IppOperation.*
import de.gmuth.ipp.core.IppTag.*
import de.gmuth.ipp.iana.IppRegistrationsSection2
import de.gmuth.log.Logging
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset

open class IppPrinter(
        val printerUri: URI,
        var attributes: IppAttributesGroup = IppAttributesGroup(Printer),
        val config: IppConfig = IppConfig(),
        httpClient: Http.Client = HttpURLConnectionClient(config),
        val ippClient: IppClient = IppClient(config, httpClient)
) {

    init {
        if (!config.getPrinterAttributesOnInit) {
            log.warn { "getPrinterAttributesOnInit disabled => no printer attributes available" }
        } else if (attributes.size == 0) {
            updateAllAttributes()
        }
    }

    constructor(printerAttributes: IppAttributesGroup, ippClient: IppClient = IppClient()) : this(
            printerAttributes.getValues<List<URI>>("printer-uri-supported").first(),
            printerAttributes,
            ippClient = ippClient
    )

    // constructors for java usage
    constructor(printerUri: String) : this(URI.create(printerUri))
    constructor(printerUri: String, config: IppConfig) : this(URI.create(printerUri), config = config)

    companion object {
        val log = Logging.getLogger {}
    }

    var getJobsRequestedAttributes = listOf(
            "job-id", "job-uri", "job-printer-uri", "job-state", "job-name",
            "job-state-message", "job-state-reasons", "job-originating-user-name"
    )

    //---------------
    // ipp attributes
    //---------------

    val name: IppString
        get() = attributes.getValue("printer-name")

    val makeAndModel: IppString
        get() = attributes.getValue("printer-make-and-model")

    val isAcceptingJobs: Boolean
        get() = attributes.getValue("printer-is-accepting-jobs")

    val state: IppPrinterState
        get() = IppPrinterState.fromInt(attributes.getValue("printer-state"))

    val stateReasons: List<String>
        get() = attributes.getValues("printer-state-reasons")

    val documentFormatSupported: List<String>
        get() = attributes.getValues("document-format-supported")

    val operationsSupported: List<IppOperation>
        get() = attributes.getValues<List<Int>>("operations-supported").map {
            IppOperation.fromShort(it.toShort())
        }

    val colorSupported: Boolean
        get() = attributes.getValue("color-supported")

    val sidesSupported: List<String>
        get() = attributes.getValues("sides-supported")

    val mediaSupported: List<String>
        get() = attributes.getValues("media-supported")

    val mediaReady: List<String>
        get() = attributes.getValues("media-ready")

    val mediaDefault: String
        get() = attributes.getValue("media-default")

    val versionsSupported: List<String>
        get() = attributes.getValues("ipp-versions-supported")

    val communicationChannelsSupported: List<IppCommunicationChannel>
        get() = mutableListOf<IppCommunicationChannel>().apply {
            with(attributes) {
                val printerUriSupportedList = getValues<List<URI>>("printer-uri-supported")
                val uriSecuritySupportedList = getValues<List<String>>("uri-security-supported")
                val uriAuthenticationSupportedList = getValues<List<String>>("uri-authentication-supported")
                for ((index, printerUriSupported) in printerUriSupportedList.withIndex())
                    add(IppCommunicationChannel(
                            printerUriSupported,
                            uriSecuritySupportedList[index],
                            uriAuthenticationSupportedList[index]
                    ))
            }
        }

    // ----------------------------------------------
    // extensions supported by cups and some printers
    // https://www.cups.org/doc/spec-ipp.html
    // ----------------------------------------------

    val deviceUri: URI
        get() = attributes.getValue("device-uri")

    val printerType: CupsPrinterType
        get() = CupsPrinterType(attributes.getValue("printer-type"))

    fun hasCapability(capability: CupsPrinterType.Capability) =
            printerType.contains(capability)

    val markers: List<CupsMarker>
        get() = mutableListOf<CupsMarker>().apply {
            with(attributes) {
                val levels = getValues<List<Int>>("marker-levels")
                val lowLevels = getValues<List<Int>>("marker-low-levels")
                val highLevels = getValues<List<Int>>("marker-high-levels")
                val types = getValues<List<String>>("marker-types")
                val names = getValues<List<IppString>>("marker-names")
                val colors = getValues<List<IppString>>("marker-colors")
                for ((index, type) in types.withIndex())
                    add(CupsMarker(
                            type,
                            names[index].text,
                            levels[index],
                            lowLevels[index],
                            highLevels[index],
                            colors[index].text
                    ))
            }
        }

    fun marker(color: CupsMarker.Color) = markers.single { it.color == color }

    //-----------------

    fun isIdle() = state == Idle
    fun isStopped() = state == Stopped
    fun isProcessing() = state == Processing
    fun isMediaNeeded() = stateReasons.contains("media-needed")
    fun isDuplexSupported() = sidesSupported.any { it.startsWith("two-sided") }
    fun supportsOperations(vararg operations: IppOperation) = operationsSupported.containsAll(operations.toList())
    fun supportsVersion(version: String) = versionsSupported.contains(version)
    fun isCups() = attributes.containsKey("cups-version")

    //-----------------
    // Identify-Printer
    //-----------------

    fun identify(vararg actions: String) = identify(actions.toList())

    fun identify(actions: List<String>): IppResponse {
        checkIfValueIsSupported("identify-actions-supported", actions)
        val request = ippRequest(IdentifyPrinter).apply {
            operationGroup.attribute("identify-actions", Keyword, actions)
        }
        return exchange(request)
    }

    fun flash() = identify("flash")
    fun sound() = identify("sound")

    //-----------------------
    // Printer administration
    //-----------------------

    fun pause() = exchange(ippRequest(PausePrinter))
    fun resume() = exchange(ippRequest(ResumePrinter))
    fun purgeJobs() = exchange(ippRequest(PurgeJobs))

    //-----------------------
    // Get-Printer-Attributes
    //-----------------------

    @JvmOverloads
    fun getPrinterAttributes(requestedAttributes: List<String>? = null) =
            exchange(ippRequest(GetPrinterAttributes, requestedAttributes = requestedAttributes))

    fun updateAllAttributes() {
        attributes = getPrinterAttributes().printerGroup
    }

    //-------------
    // Validate-Job
    //-------------

    @Throws(IppExchangeException::class)
    fun validateJob(vararg attributeBuilders: IppAttributeBuilder): IppResponse {
        val request = attributeBuildersRequest(ValidateJob, attributeBuilders)
        return exchange(request)
    }

    //----------
    // Print-Job
    //----------

    fun printJob(inputStream: InputStream, vararg attributeBuilder: IppAttributeBuilder) =
            printInputStream(inputStream, attributeBuilder)

    fun printJob(byteArray: ByteArray, vararg attributeBuilder: IppAttributeBuilder) =
            printInputStream(ByteArrayInputStream(byteArray), attributeBuilder)

    fun printJob(file: File, vararg attributeBuilder: IppAttributeBuilder) =
            printInputStream(FileInputStream(file), attributeBuilder)

    protected fun printInputStream(inputStream: InputStream, attributeBuilders: Array<out IppAttributeBuilder>): IppJob {
        val request = attributeBuildersRequest(PrintJob, attributeBuilders).apply {
            documentInputStream = inputStream
        }
        return exchangeForIppJob(request)
    }

    //----------
    // Print-URI
    //----------

    fun printUri(documentUri: URI, vararg attributeBuilders: IppAttributeBuilder): IppJob {
        val request = attributeBuildersRequest(PrintURI, attributeBuilders).apply {
            operationGroup.attribute("document-uri", Uri, documentUri)
        }
        return exchangeForIppJob(request)
    }

    //-----------
    // Create-Job
    //-----------

    fun createJob(vararg attributeBuilders: IppAttributeBuilder): IppJob {
        val request = attributeBuildersRequest(CreateJob, attributeBuilders)
        return exchangeForIppJob(request)
    }

    // ---- factory method for operations Validate-Job, Print-Job, Print-Uri, Create-Job

    protected fun attributeBuildersRequest(operation: IppOperation, attributeBuilders: Array<out IppAttributeBuilder>) =
            ippRequest(operation).apply {
                for (attributeBuilder in attributeBuilders) {
                    val attribute = attributeBuilder.buildIppAttribute(attributes)
                    checkIfValueIsSupported("${attribute.name}-supported", attribute.values)
                    // put attribute in operation or job group?
                    val groupTag = IppRegistrationsSection2.selectGroupForAttribute(attribute.name)
                    if (getAttributesGroups(groupTag).isEmpty()) createAttributesGroup(groupTag)
                    log.trace { "$groupTag put $attribute" }
                    getSingleAttributesGroup(groupTag).put(attribute)
                }
            }

    //-------------------------------
    // Get-Job-Attributes (as IppJob)
    //-------------------------------

    fun getJob(jobId: Int): IppJob {
        val request = ippRequest(GetJobAttributes, jobId)
        return exchangeForIppJob(request)
    }

    //---------------------------
    // Get-Jobs (as List<IppJob>)
    //---------------------------

    @JvmOverloads
    fun getJobs(
            whichJobs: IppWhichJobs? = null,
            myJobs: Boolean? = null,
            limit: Int? = null,
            requestedAttributes: List<String> = getJobsRequestedAttributes
    ): List<IppJob> {
        val request = ippRequest(GetJobs, requestedAttributes = requestedAttributes).apply {
            operationGroup.run {
                whichJobs?.keyword?.let {
                    checkIfValueIsSupported("which-jobs-supported", it)
                    attribute("which-jobs", Keyword, it)
                }
                myJobs?.let { attribute("my-jobs", IppTag.Boolean, it) }
                limit?.let { attribute("limit", Integer, it) }
            }
        }
        return exchange(request)
                .getAttributesGroups(Job)
                .map { IppJob(this, it) }
    }

    //----------------------
    // delegate to IppClient
    //----------------------

    fun ippRequest(operation: IppOperation, jobId: Int? = null, requestedAttributes: List<String>? = null) =
            ippClient.ippRequest(operation, printerUri, jobId, requestedAttributes)

    fun exchange(request: IppRequest): IppResponse {
        return ippClient.exchange(request.apply {
            checkIfValueIsSupported("ipp-versions-supported", version!!)
            checkIfValueIsSupported("operations-supported", code!!.toInt())
            checkIfValueIsSupported("charset-supported", attributesCharset)
        })
    }

    fun exchangeForIppJob(request: IppRequest) =
            IppJob(this, exchange(request).jobGroup)

    // -------
    // Logging
    // -------

    override fun toString() =
            "IppPrinter: name=$name, makeAndModel=$makeAndModel, state=$state, stateReasons=$stateReasons"

    fun logDetails() =
            attributes.logDetails(title = "PRINTER-$name ($makeAndModel), $state $stateReasons")

    // ------------------------------------------------------
    // attribute value checking based on printer capabilities
    // ------------------------------------------------------

    protected fun checkIfValueIsSupported(supportedAttributeName: String, value: Any) {
        if (attributes.size == 0) return

        if (!supportedAttributeName.endsWith("-supported"))
            throw IppException("attribute name not ending with '-supported'")

        if (value is Collection<*>) { // instead of providing another signature just check collections iteratively
            for (collectionValue in value) {
                checkIfValueIsSupported(supportedAttributeName, collectionValue!!)
            }
        } else {
            isAttributeValueSupported(supportedAttributeName, value)
        }
    }

    protected fun isAttributeValueSupported(supportedAttributeName: String, value: Any): Boolean? {
        val supportedAttribute = attributes[supportedAttributeName] ?: return null
        val attributeValueIsSupported = when (supportedAttribute.tag) {
            IppTag.Boolean -> { // e.g. 'page-ranges-supported'
                supportedAttribute.value as Boolean
            }
            IppTag.Enum, Charset, NaturalLanguage, MimeMediaType, Keyword, Resolution -> when (supportedAttributeName) {
                "media-col-supported" -> with(value as IppCollection) {
                    members.filter { !supportedAttribute.values.contains(it.name) }
                            .forEach { log.warn { "member unsupported: $it" } }
                    // all member names must be supported
                    supportedAttribute.values.containsAll(members.map { it.name })
                }
                else -> supportedAttribute.values.contains(value)
            }
            Integer -> {
                if (supportedAttribute.is1setOf()) supportedAttribute.values.contains(value)
                else value is Int && value <= supportedAttribute.value as Int // e.g. 'job-priority-supported'
            }
            RangeOfInteger -> {
                value is Int && value in supportedAttribute.value as IntRange
            }
            else -> null
        }
        when (attributeValueIsSupported) {
            null -> log.warn { "unable to check if value '$value' is supported by $supportedAttribute" }
            true -> log.debug { "$supportedAttributeName: $value" }
            false -> {
                log.warn { "according to printer attributes value '${supportedAttribute.enumNameOrValue(value)}' is not supported." }
                log.warn { "$supportedAttribute" }
            }
        }
        return attributeValueIsSupported
    }

    // -----------------------
    // Save printer attributes
    // -----------------------

    fun savePrinterAttributes() {
        val printerModel: String = makeAndModel.text.replace("\\s+".toRegex(), "_")
        getPrinterAttributes().run {
            saveRawBytes(File("$printerModel.bin"))
            printerGroup.saveText(File("$printerModel.txt"))
        }
    }

}