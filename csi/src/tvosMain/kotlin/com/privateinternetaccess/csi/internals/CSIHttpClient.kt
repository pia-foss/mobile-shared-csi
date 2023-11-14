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

import com.privateinternetaccess.csi.CSIRequestError
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*


actual object CSIHttpClient {
    actual fun client(
        certificate: String?,
        pinnedEndpoint: Pair<String, String>?
    ): Pair<HttpClient?, CSIRequestError.CSIException?> {
        return Pair(HttpClient(Darwin) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = CSI.REQUEST_TIMEOUT_MS
            }

            if (certificate != null && pinnedEndpoint != null) {
                engine {
                    handleChallenge(
                        CertificatePinner(
                            certificate,
                            pinnedEndpoint.first,
                            pinnedEndpoint.second
                        )
                    )
                }
            }
        }, null)
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private class CertificatePinner(
    certificate: String,
    private val hostname: String,
    private val commonName: String
) : ChallengeHandler {

    private val certificateData = NSData.create(
        base64EncodedString =
        certificate
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\n", ""),
        options = NSDataBase64Encoding64CharacterLineLength
    )

    override fun invoke(
        session: NSURLSession,
        task: NSURLSessionTask,
        challenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        if (challenge.protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
            challenge.sender?.cancelAuthenticationChallenge(challenge)
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
            return
        }

        val serverTrust = challenge.protectionSpace.serverTrust
        val serverCertificateRef = SecTrustGetCertificateAtIndex(serverTrust, 0)
        @Suppress("UNCHECKED_CAST")
        val certificateDataRef = CFBridgingRetain(certificateData) as CFDataRef
        val certificateRef = SecCertificateCreateWithData(null, certificateDataRef)
        val policyRef = SecPolicyCreateSSL(true, null)

        memScoped {
            var preparationSucceeded = true
            val serverCommonNameRef = alloc<CFStringRefVar>()
            SecCertificateCopyCommonName(serverCertificateRef, serverCommonNameRef.ptr)
            val commonNameEvaluationSucceeded = (commonName == CFBridgingRelease(serverCommonNameRef.value))
            val hostNameEvaluationSucceeded = (hostname == challenge.protectionSpace.host)

            val trust = alloc<SecTrustRefVar>()
            val trustCreation = SecTrustCreateWithCertificates(serverCertificateRef, policyRef, trust.ptr)
            if (trustCreation != errSecSuccess) {
                preparationSucceeded = false
            }

            val mutableArrayRef = CFArrayCreateMutable(kCFAllocatorDefault, 1, null)
            CFArrayAppendValue(mutableArrayRef, certificateRef)

            val trustAnchor = SecTrustSetAnchorCertificates(trust.value, mutableArrayRef)
            if (trustAnchor != errSecSuccess) {
                preparationSucceeded = false
            }

            val error = alloc<CFErrorRefVar>()
            val certificateEvaluationSucceeded = SecTrustEvaluateWithError(trust.value, error.ptr)
            challenge.sender?.useCredential(NSURLCredential.create(serverTrust), challenge)
            if (preparationSucceeded && hostNameEvaluationSucceeded && commonNameEvaluationSucceeded && certificateEvaluationSucceeded) {
                completionHandler(NSURLSessionAuthChallengeUseCredential, NSURLCredential.create(serverTrust))
            } else {
                completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
            }

            CFRelease(serverCertificateRef)
            CFRelease(certificateDataRef)
            CFRelease(certificateRef)
            CFRelease(policyRef)
            CFRelease(mutableArrayRef)
        }
    }
}