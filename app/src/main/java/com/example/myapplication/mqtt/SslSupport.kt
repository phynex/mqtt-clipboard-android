package com.example.myapplication.mqtt

import android.annotation.SuppressLint
import android.util.Log
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.example.myapplication.data.BrokerConfig

/**
 * MQTT over TLS 支持。
 *
 * - useSsl = false  -> 返回 null，Paho 使用普通 TCP。
 * - trustAllCerts   -> 信任所有证书（自签名 Broker 场景）。
 * - 正常模式        -> 走系统信任链，并显式做主机名校验（Paho 默认不校验主机名）。
 */
object SslSupport {

    private const val TAG = "SslSupport"

    fun socketFactoryFor(cfg: BrokerConfig): SSLSocketFactory? {
        if (!cfg.useSsl) return null
        return try {
            val ctx = SSLContext.getInstance("TLS")
            if (cfg.trustAllCerts) {
                ctx.init(null, arrayOf<TrustManager>(TrustAllManager()), SecureRandom())
            } else {
                ctx.init(null, null, SecureRandom())
            }
            VerifyingSocketFactory(ctx.socketFactory, cfg.normalizeHost(), cfg.trustAllCerts)
        } catch (t: Throwable) {
            Log.w(TAG, "创建 SSL SocketFactory 失败，回退默认", t)
            SSLSocketFactory.getDefault() as SSLSocketFactory
        }
    }

    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private class TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * 在委托的系统工厂之上补充两件事：
     * 1) 显式设置 SNI（部分 Broker 依赖 SNI 选择虚拟主机）；
     * 2) 握手后校验主机名（[trustAll] 时跳过）。
     */
    private class VerifyingSocketFactory(
        private val delegate: SSLSocketFactory,
        private val targetHost: String,
        private val trustAll: Boolean
    ) : SSLSocketFactory() {

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(): Socket =
            delegate.createSocket().also { configure(it, targetHost) }

        override fun createSocket(host: String?, port: Int): Socket =
            delegate.createSocket(host, port).also { configure(it, host ?: targetHost) }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            delegate.createSocket(host, port, localHost, localPort).also { configure(it, host ?: targetHost) }

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            delegate.createSocket(host, port).also { configure(it, targetHost) }

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            delegate.createSocket(address, port, localAddress, localPort).also { configure(it, targetHost) }

        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
            delegate.createSocket(s, host, port, autoClose).also { configure(it, host ?: targetHost) }

        private fun configure(socket: Socket, host: String?) {
            val ssl = socket as? SSLSocket ?: return
            val h = host ?: targetHost
            if (h.isBlank()) return
            try {
                val params = ssl.sslParameters
                params.serverNames = listOf(SNIHostName(h))
                ssl.sslParameters = params
            } catch (t: Throwable) {
                Log.w(TAG, "设置 SNI 失败: ${t.message}")
            }
            if (trustAll || !ssl.isConnected) return
            // 此时连接已建立，立刻握手以便在 connect() 阶段就能暴露证书问题
            runCatching { ssl.startHandshake() }
            val verified = runCatching {
                HttpsURLConnection.getDefaultHostnameVerifier().verify(h, ssl.session)
            }.getOrDefault(false)
            if (!verified) {
                runCatching { ssl.close() }
                throw SSLPeerUnverifiedException("证书主机名校验失败: $h")
            }
        }
    }
}
