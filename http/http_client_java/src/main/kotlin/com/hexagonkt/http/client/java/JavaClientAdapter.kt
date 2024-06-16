package com.hexagonkt.http.client.java

import com.hexagonkt.core.security.createKeyManagerFactory
import com.hexagonkt.core.security.createTrustManagerFactory
import com.hexagonkt.http.CHECKED_HEADERS
import com.hexagonkt.http.SslSettings
import com.hexagonkt.http.client.HttpClient
import com.hexagonkt.http.client.HttpClientPort
import com.hexagonkt.http.client.HttpClientSettings
import com.hexagonkt.http.formatQueryString
import com.hexagonkt.http.handlers.bodyToBytes
import com.hexagonkt.http.model.*
import com.hexagonkt.http.model.HttpResponse
import com.hexagonkt.http.model.ws.WsSession
import com.hexagonkt.http.parseContentType
import java.net.CookieManager
import java.net.HttpCookie
import java.net.URI
import java.net.http.HttpClient.Redirect.ALWAYS
import java.net.http.HttpClient.Redirect.NEVER
import java.net.http.HttpClient.Version.HTTP_2
import java.net.http.HttpHeaders
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Flow.Publisher
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.net.http.HttpClient as JavaHttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse as JavaHttpResponse

/**
 * Client to use other REST services.
 */
class JavaClientAdapter : HttpClientPort {

    private companion object {
        object TrustAll : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> =
                emptyArray()

            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}

            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        }
    }

    private lateinit var javaClient: JavaHttpClient
    private lateinit var httpClient: HttpClient
    private lateinit var httpSettings: HttpClientSettings
    private var started: Boolean = false
    private val cookieManager: CookieManager by lazy { CookieManager() }

    override fun startUp(client: HttpClient) {
        val settings = client.settings

        httpClient = client
        httpSettings = settings
        val javaClientBuilder = JavaHttpClient
            .newBuilder()
            .version(HTTP_2)
            .followRedirects(if (settings.followRedirects) ALWAYS else NEVER)

        if (settings.useCookies)
            javaClientBuilder.cookieHandler(cookieManager)

        settings.sslSettings?.let { javaClientBuilder.sslContext(sslContext(it)) }

        javaClient = javaClientBuilder.build()

        started = true
    }

    override fun shutDown() {
        started = false
    }

    override fun started() =
        started

    override fun send(request: HttpRequestPort): HttpResponsePort {
        val hexagonRequest = createRequest(request)
        val javaResponse = javaClient.send(hexagonRequest, BodyHandlers.ofByteArray())
        return convertResponse(javaResponse)
    }

    override fun ws(
        path: String,
        onConnect: WsSession.() -> Unit,
        onBinary: WsSession.(data: ByteArray) -> Unit,
        onText: WsSession.(text: String) -> Unit,
        onPing: WsSession.(data: ByteArray) -> Unit,
        onPong: WsSession.(data: ByteArray) -> Unit,
        onClose: WsSession.(status: Int, reason: String) -> Unit,
    ): WsSession {
        throw UnsupportedOperationException("WebSockets not supported")
    }

    override fun sse(request: HttpRequestPort): Publisher<ServerEvent> {
        throw UnsupportedOperationException("SSE not supported")
    }

    private fun sslContext(sslSettings: SslSettings): SSLContext {
        val sslContext = SSLContext.getInstance("TLSv1.3")

        if (httpSettings.insecure)
            return sslContext.apply {
                init(emptyArray(), arrayOf(TrustAll), SecureRandom.getInstanceStrong())
            }

        val keyManager = keyManagerFactory(sslSettings)
        val trustManager = trustManagerFactory(sslSettings)
        return sslContext.apply {
            init(
                keyManager?.keyManagers ?: emptyArray(),
                trustManager?.trustManagers ?: emptyArray(),
                SecureRandom.getInstanceStrong()
            )
        }
    }

    private fun trustManagerFactory(sslSettings: SslSettings): TrustManagerFactory? {
        val trustStoreUrl = sslSettings.trustStore ?: return null
        val trustStorePassword = sslSettings.trustStorePassword
        return createTrustManagerFactory(trustStoreUrl, trustStorePassword)
    }

    private fun keyManagerFactory(sslSettings: SslSettings): KeyManagerFactory? {
        val keyStoreUrl = sslSettings.keyStore ?: return null
        val keyStorePassword = sslSettings.keyStorePassword
        return createKeyManagerFactory(keyStoreUrl, keyStorePassword)
    }

    private fun createRequest(request: HttpRequestPort): JavaHttpRequest {
        val baseUrl = httpSettings.baseUrl

        if (httpSettings.useCookies)
            addCookies((baseUrl ?: request.url()).toURI(), request.cookies)

        val bodyBytes = bodyToBytes(request.body)
        val queryParameters = request.queryParameters
        val base = (baseUrl?.toString() ?: "") + request.path
        val uri =
            if (queryParameters.isEmpty()) base
            else base + '?' + formatQueryString(queryParameters)

        val javaRequest = JavaHttpRequest
            .newBuilder(URI(uri))
            .method(request.method.toString(), BodyPublishers.ofByteArray(bodyBytes))

        request.headers.forEach { e ->
            val name = e.value.name
            if (name != "accept-encoding") // TODO Maybe accept-encoding interferes with H2C
                e.value.values.forEach { h -> javaRequest.setHeader(name, h.toString()) }
        }

        request.contentType?.let { javaRequest.setHeader("content-type", it.text) }
        request.authorization?.let { javaRequest.setHeader("authorization", it.text) }
        request.accept.forEach { javaRequest.setHeader("accept", it.text) }

        return javaRequest.build()
    }

    private fun addCookies(uri: URI, cookies: List<Cookie>) {
        cookies.forEach {
            val httpCookie = HttpCookie(it.name, it.value)
            httpCookie.secure = it.secure
            httpCookie.maxAge = it.maxAge
            httpCookie.path = it.path
            httpCookie.isHttpOnly = it.httpOnly
            httpCookie.path = it.path
            it.domain?.let(httpCookie::setDomain)

            cookieManager.cookieStore.add(uri, httpCookie)
        }
    }

    private fun convertResponse(response: JavaHttpResponse<ByteArray>): HttpResponse {

        val bodyString = String(response.body())
        val headers = response.headers()
        val cookies =
            if (httpSettings.useCookies)
                cookieManager.cookieStore.cookies.map {
                    Cookie(
                        it.name,
                        it.value,
                        it.maxAge,
                        it.secure,
                        it.path,
                        it.isHttpOnly,
                        it.domain,
                    )
                }
            else
                emptyList()

        httpClient.cookies = cookies

        val contentType = headers.firstValue("content-type").orElse(null)
        return HttpResponse(
            body = bodyString,
            headers = convertHeaders(headers),
            contentType = contentType?.let { parseContentType(it) },
            cookies = cookies,
            status = HttpStatus(response.statusCode()),
            contentLength = bodyString.length.toLong(),
        )
    }

    private fun convertHeaders(headers: HttpHeaders): Headers =
        Headers(
            headers
                .map()
                .filter { it.key !in CHECKED_HEADERS }
                .map { Header(it.key, it.value) }
        )
}
