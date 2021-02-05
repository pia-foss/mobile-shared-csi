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

import kotlinx.cinterop.*
import platform.Foundation.*


actual class CSIPlatformData actual constructor(androidPreferenceFilename: String?) {

    companion object {
        private const val LAST_KNOWN_EXCEPTION_KEY = "LAST_KNOWN_EXCEPTION_KEY"
    }

    init {
        memScoped {
            val uncaughtExceptionHandler = staticCFunction { exception: NSException ->
                val cleanCallStack = exception.callStackSymbols.toString().replace(",", ",\n")
                val exceptionString = "$exception,\n$cleanCallStack"
                NSUserDefaults.standardUserDefaults.setValue(exceptionString, forKey = LAST_KNOWN_EXCEPTION_KEY)
                NSUserDefaults.standardUserDefaults.synchronize()
            } as CPointer<NSUncaughtExceptionHandler>
            NSSetUncaughtExceptionHandler(uncaughtExceptionHandler)
        }
    }

    actual fun userSettings(): String {
        var userSettings = ""
        val wantedSettings = NSUserDefaults.standardUserDefaults.dictionaryRepresentation().filterNot {
            it.key ==  LAST_KNOWN_EXCEPTION_KEY
        }
        for ((key, value) in wantedSettings) {
            userSettings = "$userSettings\n$key: $value"
        }
        return userSettings
    }

    actual fun lastKnownException(): String {
        return NSUserDefaults.standardUserDefaults.stringForKey(LAST_KNOWN_EXCEPTION_KEY) ?: ""
    }
}