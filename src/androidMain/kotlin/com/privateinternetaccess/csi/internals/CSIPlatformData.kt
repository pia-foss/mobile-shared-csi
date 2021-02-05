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
 *
 *  You should have received a copy of the GNU General Public License along with the Private
 *  Internet Access Mobile Client.  If not, see <https://www.gnu.org/licenses/>.
 */

import android.content.Context


actual class CSIPlatformData actual constructor(private val androidPreferenceFilename: String?) {

    companion object {
        private const val LAST_KNOWN_EXCEPTION_KEY = "LAST_KNOWN_EXCEPTION_KEY"
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val context = CSIContextProvider.applicationContext ?: return@setDefaultUncaughtExceptionHandler
            val sharedPreferences = context.getSharedPreferences(androidPreferenceFilename, Context.MODE_PRIVATE)
            sharedPreferences.edit().putString(LAST_KNOWN_EXCEPTION_KEY, throwable.stackTraceToString()).apply()
        }
    }

    actual fun userSettings(): String {
        val context = CSIContextProvider.applicationContext ?: return "Invalid Context"
        val sharedPreferences = context.getSharedPreferences(androidPreferenceFilename, Context.MODE_PRIVATE)
        var userSettings = ""
        val wantedSettings = sharedPreferences.all.filterNot {
            it.key ==  LAST_KNOWN_EXCEPTION_KEY
        }
        for ((key, value) in wantedSettings) {
            userSettings = "$userSettings\n$key: $value"
        }
        return userSettings
    }

    actual fun lastKnownException(): String {
        val context = CSIContextProvider.applicationContext ?: return "Invalid Context"
        val sharedPreferences = context.getSharedPreferences(androidPreferenceFilename, Context.MODE_PRIVATE)
        return sharedPreferences.getString(LAST_KNOWN_EXCEPTION_KEY, "") ?: ""
    }
}