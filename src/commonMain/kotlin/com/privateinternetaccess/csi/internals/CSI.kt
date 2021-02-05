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
    fun client(pinnedEndpoint: Pair<String, String>? = null): HttpClient
}

expect class CSIPlatformData(androidPreferenceFilename: String?) {
    fun userSettings(): String
    fun lastKnownException(): String
}

internal class CSI(
    private val regionInformationProvider: RegionInformationProvider,
    private val protocolInformationProvider: ProtocolInformationProvider,
    private val csiClientStateProvider: CSIClientStateProvider,
    private val platform: Platform,
    private val appVersion: String,
    androidPreferenceFilename: String?
) : CoroutineScope, CSIAPI {

    companion object {
        internal const val REQUEST_TIMEOUT_MS = 3000L
        internal const val certificate = "-----BEGIN CERTIFICATE-----\n" +
                "MIIHqzCCBZOgAwIBAgIJAJ0u+vODZJntMA0GCSqGSIb3DQEBDQUAMIHoMQswCQYD\n" +
                "VQQGEwJVUzELMAkGA1UECBMCQ0ExEzARBgNVBAcTCkxvc0FuZ2VsZXMxIDAeBgNV\n" +
                "BAoTF1ByaXZhdGUgSW50ZXJuZXQgQWNjZXNzMSAwHgYDVQQLExdQcml2YXRlIElu\n" +
                "dGVybmV0IEFjY2VzczEgMB4GA1UEAxMXUHJpdmF0ZSBJbnRlcm5ldCBBY2Nlc3Mx\n" +
                "IDAeBgNVBCkTF1ByaXZhdGUgSW50ZXJuZXQgQWNjZXNzMS8wLQYJKoZIhvcNAQkB\n" +
                "FiBzZWN1cmVAcHJpdmF0ZWludGVybmV0YWNjZXNzLmNvbTAeFw0xNDA0MTcxNzQw\n" +
                "MzNaFw0zNDA0MTIxNzQwMzNaMIHoMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQ0Ex\n" +
                "EzARBgNVBAcTCkxvc0FuZ2VsZXMxIDAeBgNVBAoTF1ByaXZhdGUgSW50ZXJuZXQg\n" +
                "QWNjZXNzMSAwHgYDVQQLExdQcml2YXRlIEludGVybmV0IEFjY2VzczEgMB4GA1UE\n" +
                "AxMXUHJpdmF0ZSBJbnRlcm5ldCBBY2Nlc3MxIDAeBgNVBCkTF1ByaXZhdGUgSW50\n" +
                "ZXJuZXQgQWNjZXNzMS8wLQYJKoZIhvcNAQkBFiBzZWN1cmVAcHJpdmF0ZWludGVy\n" +
                "bmV0YWNjZXNzLmNvbTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBALVk\n" +
                "hjumaqBbL8aSgj6xbX1QPTfTd1qHsAZd2B97m8Vw31c/2yQgZNf5qZY0+jOIHULN\n" +
                "De4R9TIvyBEbvnAg/OkPw8n/+ScgYOeH876VUXzjLDBnDb8DLr/+w9oVsuDeFJ9K\n" +
                "V2UFM1OYX0SnkHnrYAN2QLF98ESK4NCSU01h5zkcgmQ+qKSfA9Ny0/UpsKPBFqsQ\n" +
                "25NvjDWFhCpeqCHKUJ4Be27CDbSl7lAkBuHMPHJs8f8xPgAbHRXZOxVCpayZ2SND\n" +
                "fCwsnGWpWFoMGvdMbygngCn6jA/W1VSFOlRlfLuuGe7QFfDwA0jaLCxuWt/BgZyl\n" +
                "p7tAzYKR8lnWmtUCPm4+BtjyVDYtDCiGBD9Z4P13RFWvJHw5aapx/5W/CuvVyI7p\n" +
                "Kwvc2IT+KPxCUhH1XI8ca5RN3C9NoPJJf6qpg4g0rJH3aaWkoMRrYvQ+5PXXYUzj\n" +
                "tRHImghRGd/ydERYoAZXuGSbPkm9Y/p2X8unLcW+F0xpJD98+ZI+tzSsI99Zs5wi\n" +
                "jSUGYr9/j18KHFTMQ8n+1jauc5bCCegN27dPeKXNSZ5riXFL2XX6BkY68y58UaNz\n" +
                "meGMiUL9BOV1iV+PMb7B7PYs7oFLjAhh0EdyvfHkrh/ZV9BEhtFa7yXp8XR0J6vz\n" +
                "1YV9R6DYJmLjOEbhU8N0gc3tZm4Qz39lIIG6w3FDAgMBAAGjggFUMIIBUDAdBgNV\n" +
                "HQ4EFgQUrsRtyWJftjpdRM0+925Y6Cl08SUwggEfBgNVHSMEggEWMIIBEoAUrsRt\n" +
                "yWJftjpdRM0+925Y6Cl08SWhge6kgeswgegxCzAJBgNVBAYTAlVTMQswCQYDVQQI\n" +
                "EwJDQTETMBEGA1UEBxMKTG9zQW5nZWxlczEgMB4GA1UEChMXUHJpdmF0ZSBJbnRl\n" +
                "cm5ldCBBY2Nlc3MxIDAeBgNVBAsTF1ByaXZhdGUgSW50ZXJuZXQgQWNjZXNzMSAw\n" +
                "HgYDVQQDExdQcml2YXRlIEludGVybmV0IEFjY2VzczEgMB4GA1UEKRMXUHJpdmF0\n" +
                "ZSBJbnRlcm5ldCBBY2Nlc3MxLzAtBgkqhkiG9w0BCQEWIHNlY3VyZUBwcml2YXRl\n" +
                "aW50ZXJuZXRhY2Nlc3MuY29tggkAnS7684Nkme0wDAYDVR0TBAUwAwEB/zANBgkq\n" +
                "hkiG9w0BAQ0FAAOCAgEAJsfhsPk3r8kLXLxY+v+vHzbr4ufNtqnL9/1Uuf8NrsCt\n" +
                "pXAoyZ0YqfbkWx3NHTZ7OE9ZRhdMP/RqHQE1p4N4Sa1nZKhTKasV6KhHDqSCt/dv\n" +
                "Em89xWm2MVA7nyzQxVlHa9AkcBaemcXEiyT19XdpiXOP4Vhs+J1R5m8zQOxZlV1G\n" +
                "tF9vsXmJqWZpOVPmZ8f35BCsYPvv4yMewnrtAC8PFEK/bOPeYcKN50bol22QYaZu\n" +
                "LfpkHfNiFTnfMh8sl/ablPyNY7DUNiP5DRcMdIwmfGQxR5WEQoHL3yPJ42LkB5zs\n" +
                "6jIm26DGNXfwura/mi105+ENH1CaROtRYwkiHb08U6qLXXJz80mWJkT90nr8Asj3\n" +
                "5xN2cUppg74nG3YVav/38P48T56hG1NHbYF5uOCske19F6wi9maUoto/3vEr0rnX\n" +
                "JUp2KODmKdvBI7co245lHBABWikk8VfejQSlCtDBXn644ZMtAdoxKNfR2WTFVEwJ\n" +
                "iyd1Fzx0yujuiXDROLhISLQDRjVVAvawrAtLZWYK31bY7KlezPlQnl/D9Asxe85l\n" +
                "8jO5+0LdJ6VyOs/Hd4w52alDW/MFySDZSfQHMTIc30hLBJ8OnCEIvluVQQ2UQvoW\n" +
                "+no177N9L2Y+M9TcTA62ZyMXShHQGeh20rb4kK8f+iFX8NxtdHVSkxMEFSfDDyQ=\n" +
                "-----END CERTIFICATE-----\n"
    }

    private enum class ReportType(val value: String) {
        DIAGNOSTIC("diagnostic"),
        CRASH("crash"),
        LOG("log")
    }

    private val csiPlatformData: CSIPlatformData = CSIPlatformData(androidPreferenceFilename)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // region CoroutineScope
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main
    // endregion

    // region CSIAPI
    override fun send(
            shouldSendPersistedData: Boolean,
            callback: (reportIdentifier: String?, error: CSIRequestError?) -> Unit
    ) {
        sendAsync(shouldSendPersistedData, csiClientStateProvider.csiEndpoints(), callback)
    }
    // endregion

    // region private
    private fun sendAsync(
            shouldSendPersistedData: Boolean,
            endpoints: List<CSIEndpoint>,
            callback: (reportIdentifier: String?, error: CSIRequestError?) -> Unit
    ) = async {
        val reportPlatform = when (platform) {
            Platform.IOS -> "pia_ios"
            Platform.ANDROID -> "pia_android"
        }

        createReport(reportPlatform, endpoints) { reportIdentifier, createReportError ->
            launch {
                val userSettings = csiPlatformData.userSettings()
                val lastKnownException = csiPlatformData.lastKnownException()
                val protocolInformation = protocolInformationProvider.protocolInformation()
                val regionInformation = regionInformationProvider.regionInformation()
                reportIdentifier?.let { identifier ->
                    if (shouldSendPersistedData) {
                        submitLogsToReport(userSettings, "user_settings", ReportType.DIAGNOSTIC, identifier, endpoints)?.let {
                            println("Error uploading User Settings: $it")
                        }
                        submitLogsToReport(regionInformation, "region_information", ReportType.DIAGNOSTIC, identifier, endpoints)?.let {
                            println("Error uploading Region Information: $it")
                        }
                        submitLogsToReport(lastKnownException, "last_known_exception", ReportType.CRASH, identifier, endpoints)?.let {
                            println("Error uploading Last Known Exception: $it")
                        }
                    }
                    submitLogsToReport(protocolInformation, "protocol_information", ReportType.DIAGNOSTIC, identifier, endpoints)?.let {
                        println("Error uploading Protocol Information: $it")
                    }
                    closeReport(reportIdentifier, endpoints)?.let {
                        println("Error closing report: $it")
                    }
                }

                withContext(Dispatchers.Main) {
                    callback(reportIdentifier, createReportError)
                }
            }
        }
    }

    private suspend fun createReport(
            platform: String,
            endpoints: List<CSIEndpoint>,
            callback: (reportIdentifier: String?, error: CSIRequestError?) -> Unit
    ) {
        var reportIdentifier: String? = null
        var error: CSIRequestError? = null
        if (endpoints.isNullOrEmpty()) {
            error = CSIRequestError(600, "No available endpoints to perform the request")
        }

        for (endpoint in endpoints) {
            error = null
            val client = if (endpoint.usePinnedCertificate) {
                CSIHttpClient.client(Pair(endpoint.endpoint, endpoint.certificateCommonName!!))
            } else {
                CSIHttpClient.client()
            }

            val response = client.postCatching<Pair<HttpResponse?, Exception?>> {
                url("https://${endpoint.endpoint}/api/v2/report/create")
                parameter("meta", "{\"version\": \"$appVersion\"}")
                parameter("team", platform)
            }

            response.first?.let { httpResponse ->
                try {
                    val content = httpResponse.receive<String>()
                    if (CSIUtils.isErrorStatusCode(httpResponse.status.value)) {
                        error = CSIRequestError(httpResponse.status.value, httpResponse.status.description)
                    } else {
                        try {
                            reportIdentifier = json.decodeFromString(CreateReportResponse.serializer(), content).code
                        } catch (exception: SerializationException) {
                            error = CSIRequestError(600, "Decode error $exception")
                        }
                    }
                } catch (exception: NoTransformationFoundException) {
                    error = CSIRequestError(600, "Unexpected response transformation: $exception")
                } catch (exception: DoubleReceiveException) {
                    error = CSIRequestError(600, "Request receive already invoked: $exception")
                }
            }
            response.second?.let {
                error = CSIRequestError(600, it.message)
            }

            // If there were no errors in the request for the current endpoint. No need to try the next endpoint.
            if (error == null) {
                break
            }
        }

        callback(reportIdentifier, error)
    }

    private suspend fun submitLogsToReport(
            logs: String,
            filename: String,
            reportType: ReportType,
            reportIdentifier: String,
            endpoints: List<CSIEndpoint>
    ): CSIRequestError? {
        var error: CSIRequestError? = null
        if (endpoints.isNullOrEmpty()) {
            error = CSIRequestError(600, "No available endpoints to perform the request")
        }

        for (endpoint in endpoints) {
            error = null
            val client = if (endpoint.usePinnedCertificate) {
                CSIHttpClient.client(Pair(endpoint.endpoint, endpoint.certificateCommonName!!))
            } else {
                CSIHttpClient.client()
            }

            val categorizeLogs = "/PIA_PART/$filename.${reportType.value}\n$logs"
            val response = client.postCatching<Pair<HttpResponse?, Exception?>> {
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

            response.first?.let { httpResponse ->
                try {
                    if (CSIUtils.isErrorStatusCode(httpResponse.status.value)) {
                        error = CSIRequestError(httpResponse.status.value, httpResponse.status.description)
                    }
                } catch (exception: NoTransformationFoundException) {
                    error = CSIRequestError(600, "Unexpected response transformation: $exception")
                } catch (exception: DoubleReceiveException) {
                    error = CSIRequestError(600, "Request receive already invoked: $exception")
                }
            }
            response.second?.let {
                error = CSIRequestError(600, it.message)
            }

            // If there were no errors in the request for the current endpoint. No need to try the next endpoint.
            if (error == null) {
                break
            }
        }

        return error
    }

    private suspend fun closeReport(
            reportIdentifier: String,
            endpoints: List<CSIEndpoint>
    ): CSIRequestError? {
        var error: CSIRequestError? = null
        if (endpoints.isNullOrEmpty()) {
            error = CSIRequestError(600, "No available endpoints to perform the request")
        }

        for (endpoint in endpoints) {
            error = null
            val client = if (endpoint.usePinnedCertificate) {
                CSIHttpClient.client(Pair(endpoint.endpoint, endpoint.certificateCommonName!!))
            } else {
                CSIHttpClient.client()
            }

            val response = client.postCatching<Pair<HttpResponse?, Exception?>> {
                url("https://${endpoint.endpoint}/api/v2/report/$reportIdentifier/finish")
            }

            response.first?.let { httpResponse ->
                try {
                    if (CSIUtils.isErrorStatusCode(httpResponse.status.value)) {
                        error = CSIRequestError(httpResponse.status.value, httpResponse.status.description)
                    }
                } catch (exception: NoTransformationFoundException) {
                    error = CSIRequestError(600, "Unexpected response transformation: $exception")
                } catch (exception: DoubleReceiveException) {
                    error = CSIRequestError(600, "Request receive already invoked: $exception")
                }
            }
            response.second?.let {
                error = CSIRequestError(600, it.message)
            }

            // If there were no errors in the request for the current endpoint. No need to try the next endpoint.
            if (error == null) {
                break
            }
        }

        return error
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
}