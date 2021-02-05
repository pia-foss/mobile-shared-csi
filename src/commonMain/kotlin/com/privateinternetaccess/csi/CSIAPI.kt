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
 * Interface defining the API to be offered by the csi module.
 */
interface CSIAPI {

    /**
     * It sends a request with the known information gathered internally as well as the one offered by the providers.
     *
     * @param shouldSendPersistedData `Boolean`. It tells the module whether to send user persisted data.
     * e.g. SharedPreferences, Last Known Exception, etc. When false, only protocol information will be sent.
     * @param callback `(reportIdentifier: String?, error: CSIRequestError?) -> Unit`
     */
    fun send(
            shouldSendPersistedData: Boolean = false,
            callback: (reportIdentifier: String?, error: CSIRequestError?) -> Unit
    )
}

/**
 * Interface defining the region's data provider.
 */
public interface RegionInformationProvider {

    /**
     * It returns the the string representing all the relevant information for the current region.
     *
     * @return `String`
     */
    fun regionInformation(): String
}

/**
 * Interface defining the vpn's protocol data provider.
 */
public interface ProtocolInformationProvider {

    /**
     * It returns the the string representing all the relevant information for the current protocol.
     *
     * @return `String`
     */
    fun protocolInformation(): String
}

/**
 * Interface defining the client's endpoint provider.
 */
public interface CSIClientStateProvider {

    /**
     * It returns the list of endpoints to try to reach when performing a request. Order is relevant.
     *
     * @return `List<CSIEndpoint>`
     */
    fun csiEndpoints(): List<CSIEndpoint>
}

/**
 * Enum containing the supported platforms.
 */
public enum class Platform {
    IOS,
    ANDROID
}

/**
 * Builder class responsible for creating an instance of an object conforming to
 * the `CSIAPI` interface.
 */
public class CSIBuilder {
    private var regionInformationProvider: RegionInformationProvider? = null
    private var protocolInformationProvider: ProtocolInformationProvider? = null
    private var csiClientStateProvider: CSIClientStateProvider? = null
    private var androidPreferenceFilename: String? = null
    private var platform: Platform? = null
    private var appVersion: String? = null

    /**
     * It sets the instance responsible to provide the selected region information.
     *
     * @param regionInformationProvider `RegionInformationProvider`.
     */
    fun setRegionInformationProvider(regionInformationProvider: RegionInformationProvider): CSIBuilder =
        apply { this.regionInformationProvider = regionInformationProvider }

    /**
     * It sets the instance responsible to provide the selected protocol information.
     *
     * @param protocolInformationProvider `ProtocolInformationProvider`.
     */
    fun setProtocolInformationProvider(protocolInformationProvider: ProtocolInformationProvider): CSIBuilder =
        apply { this.protocolInformationProvider = protocolInformationProvider }

    /**
     * It sets the instance responsible to provide the endpoints information.
     *
     * @param csiClientStateProvider `CSIClientStateProvider`.
     */
    fun setCSIClientStateProvider(csiClientStateProvider: CSIClientStateProvider): CSIBuilder =
        apply { this.csiClientStateProvider = csiClientStateProvider }

    /**
     * It sets android's preference filename containing all persisted data.
     *
     * @param androidPreferenceFilename `String`.
     */
    fun setAndroidPreferenceFilename(androidPreferenceFilename: String): CSIBuilder =
        apply { this.androidPreferenceFilename = androidPreferenceFilename }

    /**
     * It sets the platform for which we are building the API.
     *
     * @param platform `Platform`.
     */
    fun setPlatform(platform: Platform): CSIBuilder =
        apply { this.platform = platform }

    /**
     * It sets the application version for which we are building the API.
     *
     * @param appVersion `String`.
     */
    fun setAppVersion(appVersion: String): CSIBuilder =
        apply { this.appVersion = appVersion }

    /**
     * @return `CSIAPI` instance.
     */
    fun build(): CSIAPI {
        val regionInformationProvider = this.regionInformationProvider
            ?: throw Exception("Region information provider missing.")
        val protocolInformationProvider = protocolInformationProvider
            ?: throw Exception("Protocol information provider missing.")
        val csiClientStateProvider = csiClientStateProvider
            ?: throw Exception("CSI client state provider missing.")
        val platform = this.platform
            ?: throw Exception("Platform definition missing.")
        val appVersion = this.appVersion
            ?: throw Exception("App version missing.")
        val androidPreferenceFilename = when (platform) {
            Platform.IOS ->
                null
            Platform.ANDROID ->
                this.androidPreferenceFilename ?: throw Exception("Android Preference Filename missing.")
        }
        return CSI(
            regionInformationProvider,
            protocolInformationProvider,
            csiClientStateProvider,
            platform,
            appVersion,
            androidPreferenceFilename
        )
    }
}

/**
 * Request error message containing the http code and description.
 */
public data class CSIRequestError(val code: Int, val message: String?)


/**
 * Data class defining the endpoints data needed when performing a request on it.
 */
public data class CSIEndpoint(
    val endpoint: String,
    val isProxy: Boolean,
    val usePinnedCertificate: Boolean = false,
    val certificateCommonName: String? = null
)