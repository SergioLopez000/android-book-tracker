package com.librosleidos.probe

import android.annotation.SuppressLint
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Throwaway CodeQL probe for issue #4 (spike S3). Never merge.
 * Trusts every certificate on purpose so CodeQL raises a High alert.
 */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
class TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

object InsecureTlsProbe {
    fun insecureContext(): SSLContext {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(TrustAllManager()), null)
        return context
    }
}
