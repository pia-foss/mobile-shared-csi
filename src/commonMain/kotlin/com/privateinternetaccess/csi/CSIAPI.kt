package com.privateinternetaccess.csi

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

import com.privateinternetaccess.csi.internals.CSI


/**
 * Enum categorizing all possible internal errors
 */
enum class CSIInternalErrorCode {

    /**
     * Indicates an issue with the data provided by the client.
     */
    ERROR_INVALID_CLIENT_STATE,

    /**
     * Indicates the request returned a status code other than 200.
     */
    ERROR_UNSUCCESSFUL_REQUEST,

    /**
     * Indicates a problem when reading the requests response.
     */
    ERROR_READING_RESPONSE,

    /**
     * Indicates a problem serializing the response
     */
    ERROR_SERIALIZING_RESPONSE,

    /**
     * Indicates a problem within the http engine. It includes certificate related problems.
     */
    ERROR_HTTP_ENGINE
}

/**
 * Interface defining the API to be offered by the csi module.
 */
interface CSIAPI {

    /**
     * It sends a request with the known information gathered internally as well as the one offered by the providers.
     *
     * @param teamIdentifier `String` Identifier for which a specific report will be submitted.
     * @param shouldSendPersistedData `Boolean` It tells the module whether to send user persisted data.
     * e.g. SharedPreferences, Last Known Exception, etc. When false, only protocol information will be sent.
     * @param callback `(reportIdentifier: String?, error: List<CSIRequestError>) -> Unit`
     */
    fun send(
        shouldSendPersistedData: Boolean = false,
        callback: (reportIdentifier: String?, listErrors: List<CSIRequestError>) -> Unit
    )
}

interface ICSIProvider {
    val providerType: ProviderType
    val isPersistedData: Boolean
    val filename: String?
    val reportType: ReportType
    val value: String?
}

interface IEndPointProvider {
    val endpoints: List<CSIEndpoint>
}

enum class ReportType(val value: String) {
    DIAGNOSTIC(value = "diagnostic"),
    CRASH(value = "crash"),
    LOG(value = "log")
}

enum class ProviderType(
    val printName: String
) {
    USER_SETTINGS(printName = "User Settings"),
    LAST_KNOWN_EXCEPTION(printName = "Last Known Exception"),
    REGION_INFORMATION(printName = "Region Information"),
    LOGGING_INFORMATION(printName = "Logging Information"),
    APPLICATION_INFORMATION(printName = "Application Information"),
    DEVICE_INFORMATION(printName = "Device Information"),
    PROTOCOL_INFORMATION(printName = "Protocol Information")
}

/**
 * Builder class responsible for creating an instance of an object conforming to
 * the `CSIAPI` interface.
 */
public class CSIBuilder {
    private var endpointsProvider: IEndPointProvider? = null
    private val listLogProviders: MutableList<ICSIProvider> = mutableListOf()

    private var teamIdentifier: String? = null
    private var certificate: String? = null
    private var appVersion: String? = null

    /**
     * It sets the team identifier, that is used to distinguish the different client platforms. Required.
     *
     * @param teamIdentifier `String`.
     */
    fun setTeamIdentifier(teamIdentifier: String): CSIBuilder = apply {
        this.teamIdentifier = teamIdentifier
    }

    /**
     * It sets the endpoints provider, that is queried for the current endpoint list. Required.
     *
     * @param endpointsProvider `IEndPointProvider`.
     */
    fun setEndPointProvider(endpointsProvider: IEndPointProvider): CSIBuilder = apply {
        this.endpointsProvider = endpointsProvider
    }

    /**
     * Adds logging providers, that are responsible for adding logging information. Optional
     * You may only add at most one provider of each provider type
     *
     * @param providers `List<ICSIProvider<String>>`.
     */
    fun addLogProviders(providers: List<ICSIProvider>): CSIBuilder = apply {
        if(providers.isNotEmpty()) {
            this.listLogProviders.addAll(providers)
        }
    }

    /**
     * It sets the endpoints provider, that is queried for the current endpoint list. Required.
     *
     * @param providers `List<ICSIProvider<String>>`.
     */
    fun addLogProviders(vararg providers: ICSIProvider): CSIBuilder = apply {
        if(providers.isNotEmpty()) {
            this.listLogProviders.addAll(providers)
        }
    }

    /**
     * It sets the certificate to use when using an endpoint with pinning enabled. Optional.
     *
     * @param certificate `String`.
     */
    fun setCertificate(certificate: String?): CSIBuilder = apply { this.certificate = certificate }

    /**
     * It sets the application version for which we are building the API.
     *
     * @param appVersion `String`.
     */
    fun setAppVersion(appVersion: String): CSIBuilder = apply { this.appVersion = appVersion }

    /**
     * @return `CSIAPI` instance.
     */
    fun build(): CSIAPI {
        val clientEndpointProvider = endpointsProvider ?: throw Exception("Endpoint provider missing.")
        val appVersion = this.appVersion
        val teamIdentifier = this.teamIdentifier
        if(appVersion.isNullOrBlank()) {
            throw Exception("App version missing.")
        }
        if(teamIdentifier.isNullOrBlank()) {
            throw Exception("Team identifier missing.")
        }
        val listLogProviders = this.listLogProviders
        val providerType: ProviderType? = listLogProviders.fold(mutableMapOf<ProviderType, Int>()) { map, provider: ICSIProvider ->
            val providerType: ProviderType = provider.providerType
            map[providerType] = (map[providerType] ?: 0) + 1
            return@fold map
        }.filter { entry -> entry.value > 1 }.entries.firstOrNull()?.key
        if(providerType != null) {
            throw Exception("At most 1 provider of type '${providerType.printName}' may be specified.")
        }
        return CSI(
            teamIdentifier = teamIdentifier,
            endpointsProvider = clientEndpointProvider,
            listProviders = listLogProviders,
            appVersion = appVersion,
            certificate = certificate
        )
    }
}

/**
 * Request error message containing the http code and description.
 */
public data class CSIRequestError(
    val isFatal: Boolean = false,
    val code: CSIInternalErrorCode,
    val message: String?,
    val exceptionDetails: CSIException? = null,
    val csiProvider: String? = null,
    val csiEndpoint: CSIEndpoint? = null
) {

    /**
     * Error data structure containing all the relevant details about a thrown exception.
     */
    data class CSIException(val clazz: String, val message: String?, val stacktrace: String)
}


/**
 * Data class defining the endpoints data needed when performing a request on it.
 */
public data class CSIEndpoint(
    val endpoint: String,
    val isProxy: Boolean,
    val usePinnedCertificate: Boolean = false,
    val certificateCommonName: String? = null
)
