package com.privateinternetaccess.csi.internals

/*
 *  Copyright (c) 2020 Private Internet Access, Inc.
 *
 *  This file is part of the Private Internet Access Mobile Client.
 *
 *  The Private Internet Access Mobile Client is free software: you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  The Private Internet Access Mobile Client is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 *  details.
 *
 *  You should have received a copy of the GNU General Public License along with the Private
 *  Internet Access Mobile Client.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.privateinternetaccess.csi.*
import com.privateinternetaccess.csi.CSIInternalErrorCode.*
import com.privateinternetaccess.csi.internals.model.CreateReportResponse
import com.privateinternetaccess.csi.internals.utils.CSIUtils
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext


expect object CSIHttpClient {
    fun client(
        certificate: String? = null,
        pinnedEndpoint: Pair<String, String>? = null
    ): Pair<HttpClient?, CSIRequestError.CSIException?>
}

internal class CSI(
    private val teamIdentifier: String,
    private val endpointsProvider: IEndPointProvider,
    private val listProviders: List<ICSIProvider>,
    private val appVersion: String,
    private val certificate: String?
) : CoroutineScope, CSIAPI {

    companion object {
        internal const val REQUEST_TIMEOUT_MS = 3000L
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // region CoroutineScope
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main
    // endregion

    // region CSIAPI
    @Suppress("DeferredResultUnused")
    override fun send(
        shouldSendPersistedData: Boolean,
        callback: (reportIdentifier: String?, listErrors: List<CSIRequestError>) -> Unit
    ) {
        sendAsync(teamIdentifier, shouldSendPersistedData, endpointsProvider.endpoints, callback)
    }
    // endregion

    // region private
    private fun sendAsync(
        teamIdentifier: String,
        shouldSendPersistedData: Boolean,
        endpoints: List<CSIEndpoint>,
        callback: (reportIdentifier: String?, listErrors: List<CSIRequestError>) -> Unit
    ) = launch {
        val listErrors: MutableList<CSIRequestError> = mutableListOf()

        // start report
        val result: Result = createReport(teamIdentifier, endpoints)
        val reportIdentifier: String? = result.reportIdentifier
        listErrors.addAll(result.errorList)
        if (reportIdentifier.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                callback(null, listErrors)
            }
            return@launch
        }

        // submit logs in parallel
        val listAwait: List<Deferred<List<CSIRequestError>>> = listProviders.map { provider: ICSIProvider ->
            return@map async {
                return@async try {
                    submitLogsToReport(
                        provider = provider,
                        reportIdentifier = reportIdentifier,
                        shouldSendPersistedData = shouldSendPersistedData,
                        endpoints = endpoints
                    )
                } catch (t: Throwable) {
                    listOf(CSIRequestError(
                        code = ERROR_UNSUCCESSFUL_REQUEST,
                        message = t.message,
                        exceptionDetails = CSIRequestError.CSIException(
                            clazz = t::class.simpleName ?: "Unknown Exception Name",
                            message = t.message,
                            stacktrace = t.stackTraceToString()
                        )
                    ))
                }
            }
        }

        // wait for all submit logs requests
        listAwait.forEach { def ->
            try {
                listErrors.addAll(def.await())
            } catch (t: Throwable) {
            }
        }

        // finish report
        try {
            val errorList: List<CSIRequestError> = closeReport(
                reportIdentifier,
                endpoints
            )
            listErrors.addAll(errorList)
        } catch (t: Throwable) {
            listErrors.add(CSIRequestError(
                code = ERROR_UNSUCCESSFUL_REQUEST,
                message = t.message,
                exceptionDetails = CSIRequestError.CSIException(
                    clazz = t::class.simpleName ?: "Unknown Exception Name",
                    message = t.message,
                    stacktrace = t.stackTraceToString()
                )
            ))
        }

        // return the result via callback on the main thread
        withContext(Dispatchers.Main) {
            callback(reportIdentifier, listErrors)
        }
    }

    private suspend fun createReport(
        teamIdentifier: String,
        endpoints: List<CSIEndpoint>
    ): Result {
        var reportIdentifier: String? = null
        if (endpoints.isNullOrEmpty()) {
            return Result(
                reportIdentifier = null,
                errorList = listOf(CSIRequestError(code = ERROR_INVALID_CLIENT_STATE, message = "No available endpoints to perform the request"))
            )
        }

        val listErrors: MutableList<CSIRequestError> = mutableListOf()
        for (endpoint in endpoints) {
            if (endpoint.usePinnedCertificate && certificate.isNullOrEmpty()) {
                listErrors.add(CSIRequestError(code = ERROR_INVALID_CLIENT_STATE, message = "No available certificate for pinning purposes"))
                continue
            }

            val httpClientConfigResult = if (endpoint.usePinnedCertificate) {
                CSIHttpClient.client(certificate, Pair(endpoint.endpoint, endpoint.certificateCommonName!!))
            } else {
                CSIHttpClient.client()
            }

            val httpClient = httpClientConfigResult.first
            val httpClientError = httpClientConfigResult.second
            if (httpClientError != null) {
                listErrors.add(CSIRequestError(code = ERROR_HTTP_ENGINE, message = httpClientError.message, exceptionDetails = httpClientError))
                continue
            }

            if (httpClient == null) {
                listErrors.add(CSIRequestError(code = ERROR_HTTP_ENGINE, message = "Invalid http client"))
                continue
            }

            var hasError: Boolean = false
            val response = httpClient.postCatching<Pair<HttpResponse?, Exception?>> {
                url("https://${endpoint.endpoint}/api/v2/report/create")
                parameter("meta", "{\"version\": \"$appVersion\"}")
                parameter("team", teamIdentifier)
            }

            response.first?.let { httpResponse ->
                if (CSIUtils.isErrorStatusCode(httpResponse.status.value)) {
                    hasError = true
                    listErrors.add(CSIRequestError(
                        code = ERROR_UNSUCCESSFUL_REQUEST,
                        message = "(${httpResponse.status.value}) ${httpResponse.status.description}"
                    ))
                } else {
                    try {
                        reportIdentifier = json.decodeFromString(
                            CreateReportResponse.serializer(),
                            httpResponse.receive()
                        ).code
                    } catch (exception: NoTransformationFoundException) {
                        hasError = true
                        listErrors.add(CSIRequestError(code = ERROR_READING_RESPONSE, message = "Unexpected response transformation: $exception"))
                    } catch (exception: DoubleReceiveException) {
                        hasError = true
                        listErrors.add(CSIRequestError(code = ERROR_READING_RESPONSE, message = "Request receive already invoked: $exception"))
                    } catch (exception: SerializationException) {
                        hasError = true
                        listErrors.add(CSIRequestError(code = ERROR_SERIALIZING_RESPONSE, message = "Decode error $exception"))
                    }
                }
                return@let
            }
            response.second?.let { t ->
                hasError = true
                listErrors.add(CSIRequestError(code = ERROR_HTTP_ENGINE, message = t.message))
                return@let
            }

            // If there were no errors in the request for the current endpoint. No need to try the next endpoint.
            if (hasError.not()) {
                break
            }
        }

        listErrors.forEach { error ->
            println("Error closing report: $error")
        }
        return Result(
            reportIdentifier = reportIdentifier,
            errorList = listErrors
        )
    }

    private suspend fun submitLogsToReport(
        provider: ICSIProvider,
        reportIdentifier: String,
        shouldSendPersistedData: Boolean,
        endpoints: List<CSIEndpoint>
    ): List<CSIRequestError> {
        val providerType: ProviderType = provider.providerType
        val reportType: ReportType = provider.reportType
        if (shouldSendPersistedData.not() && provider.isPersistedData) {
            println("Skip uploading ${providerType.printName}: persisted data")
            return listOf()
        }
        val filename: String? = provider.filename
        if (filename.isNullOrBlank()) {
            println("Skip uploading ${providerType.printName}: filename is null or blank")
            return listOf()
        }
        val value: String = provider.value ?: kotlin.run {
            println("Skip uploading ${providerType.printName}: value is null")
            return listOf()
        }

        val listErrors: MutableList<CSIRequestError> = mutableListOf()
        if (endpoints.isNullOrEmpty()) {
            return listOf(CSIRequestError(
                isFatal = true,
                code = ERROR_INVALID_CLIENT_STATE,
                message = "No available endpoints to perform the request"
            ))
        }

        for (endpoint in endpoints) {
            if (endpoint.usePinnedCertificate && certificate.isNullOrEmpty()) {
                listErrors.add(CSIRequestError(
                    code = ERROR_INVALID_CLIENT_STATE,
                    message = "No available certificate for pinning purposes",
                    csiProvider = "provider for '${provider.providerType.printName}' (filename: ${provider.filename}, report: ${provider.reportType.value})",
                    csiEndpoint = endpoint
                ))
                continue
            }

            val httpClientConfigResult = if (endpoint.usePinnedCertificate) {
                CSIHttpClient.client(certificate, Pair(endpoint.endpoint, endpoint.certificateCommonName!!))
            } else {
                CSIHttpClient.client()
            }

            val httpClient = httpClientConfigResult.first
            val httpClientError = httpClientConfigResult.second
            if (httpClientError != null) {
                listErrors.add(CSIRequestError(
                    code = ERROR_HTTP_ENGINE,
                    message = httpClientError.message,
                    exceptionDetails = httpClientError,
                    csiProvider = "provider for '${provider.providerType.printName}' (filename: ${provider.filename}, report: ${provider.reportType.value})",
                    csiEndpoint = endpoint
                ))
                continue
            }

            if (httpClient == null) {
                listErrors.add(CSIRequestError(
                    code = ERROR_HTTP_ENGINE,
                    message = "Invalid http client",
                    csiProvider = "provider for '${provider.providerType.printName}' (filename: ${provider.filename}, report: ${provider.reportType.value})",
                    csiEndpoint = endpoint
                ))
                continue
            }

            val categorizeLogs = "/PIA_PART/$filename.${reportType.value}\n$value"
            val response = httpClient.postCatching<Pair<HttpResponse?, Exception?>> {
                url("https://${endpoint.endpoint}/api/v2/report/$reportIdentifier/add")
                body = MultiPartFormDataContent(
                    formData {
                        append(
                            filename,
                            categorizeLogs,
                            Headers.build {
                                append(HttpHeaders.ContentType, "text/plain")
                                append(HttpHeaders.ContentDisposition, "filename=$filename.txt")
                            }
                        )
                    }
                )
            }

            var hasError = false
            response.first?.let { httpResponse ->
                if (CSIUtils.isErrorStatusCode(httpResponse.status.value)) {
                    hasError = true
                    listErrors.add(CSIRequestError(
                        code = ERROR_UNSUCCESSFUL_REQUEST,
                        message = "(${httpResponse.status.value}) ${httpResponse.status.description}",
                        csiProvider = "provider for '${provider.providerType.printName}' (filename: ${provider.filename}, report: ${provider.reportType.value})",
                        csiEndpoint = endpoint
                    ))
                }
            }
            response.second?.let { t ->
                hasError = true
                listErrors.add(CSIRequestError(
                    code = ERROR_HTTP_ENGINE,
                    message = t.message,
                    exceptionDetails = CSIRequestError.CSIException(
                        clazz = t::class.simpleName ?: "Unknown Exception Name",
                        message = t.message,
                        stacktrace = t.stackTraceToString()
                    ),
                    csiProvider = "provider for '${provider.providerType.printName}' (filename: ${provider.filename}, report: ${provider.reportType.value})",
                    csiEndpoint = endpoint
                ))
                return@let
            }

            // If there were no errors in the request for the current endpoint. No need to try the next endpoint.
            if (hasError.not()) {
                break
            }
        }
        listErrors.forEach { error ->
            println("Error uploading '${provider.providerType.printName}': $error")
        }
        return listErrors
    }

    private suspend fun closeReport(
        reportIdentifier: String,
        endpoints: List<CSIEndpoint>
    ): List<CSIRequestError> {
        val listErrors: MutableList<CSIRequestError> = mutableListOf()
        if (endpoints.isNullOrEmpty()) {
            return listOf(CSIRequestError(code = ERROR_INVALID_CLIENT_STATE, message = "No available endpoints to perform the request"))
        }

        for (endpoint in endpoints) {
            if (endpoint.usePinnedCertificate && certificate.isNullOrEmpty()) {
                listErrors.add(CSIRequestError(code = ERROR_INVALID_CLIENT_STATE, message = "No available certificate for pinning purposes"))
                continue
            }

            val httpClientConfigResult = if (endpoint.usePinnedCertificate) {
                CSIHttpClient.client(certificate, Pair(endpoint.endpoint, endpoint.certificateCommonName!!))
            } else {
                CSIHttpClient.client()
            }

            val httpClient = httpClientConfigResult.first
            val httpClientError = httpClientConfigResult.second
            if (httpClientError != null) {
                listErrors.add(CSIRequestError(code = ERROR_HTTP_ENGINE, message = httpClientError.message, exceptionDetails = httpClientError))
                continue
            }

            if (httpClient == null) {
                listErrors.add(CSIRequestError(code = ERROR_HTTP_ENGINE, message = "Invalid http client"))
                continue
            }

            var hasError: Boolean = false
            val response = httpClient.postCatching<Pair<HttpResponse?, Exception?>> {
                url("https://${endpoint.endpoint}/api/v2/report/$reportIdentifier/finish")
            }

            response.first?.let { httpResponse ->
                if (CSIUtils.isErrorStatusCode(httpResponse.status.value)) {
                    hasError = true
                    listErrors.add(CSIRequestError(
                        code = ERROR_UNSUCCESSFUL_REQUEST,
                        message = "(${httpResponse.status.value}) ${httpResponse.status.description}"
                    ))
                }
            }
            response.second?.let { t ->
                hasError = true
                listErrors.add(CSIRequestError(
                    code = ERROR_HTTP_ENGINE,
                    message = t.message,
                    exceptionDetails = CSIRequestError.CSIException(
                        clazz = t::class.simpleName ?: "Unknown Exception Name",
                        message = t.message,
                        stacktrace = t.stackTraceToString()
                    )
                ))
                return@let
            }

            // If there were no errors in the request for the current endpoint. No need to try the next endpoint.
            if (hasError.not()) {
                break
            }
        }
        listErrors.forEach { error ->
            println("Error closing report: $error")
        }
        return listErrors
    }
    // endregion

    // region HttpClient extensions
    private suspend inline fun <reified T> HttpClient.postCatching(
        block: HttpRequestBuilder.() -> Unit = {}
    ): Pair<HttpResponse?, Exception?> = request {
        var exception: Exception? = null
        var response: HttpResponse? = null
        try {
            response = request {
                method = HttpMethod.Post
                apply(block)
            }
        } catch (e: Exception) {
            exception = e
        }
        return Pair(response, exception)
    }
    // endregion

    private data class Result(
        val reportIdentifier: String? = null,
        val errorList: List<CSIRequestError>
    )
}