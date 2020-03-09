package com.github.rwsbillyang.dsldata.http

import android.annotation.TargetApi

import android.content.Context
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.webkit.*
import androidx.appcompat.app.AlertDialog

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection


/**
 * 支持SSL双向认证的 WebViewClient since API 8
 *
 * TODO: 低于Build.VERSION_CODES.LOLLIPOP的版本可能客户端身份认证工作不正常
 *
 * FIXME： OkHttpClient暂不能正常工作，忽略即可。
 *
 * */
class SslWebViewClient(
    private val context: Context,
    private val provider: ClientConfiguration,
    private val okHttpClient: OkHttpClient? = null
) : WebViewClient() {

    private val TAG = "SslWebViewClient"

    /**
     * Notify the host application to handle a SSL client certificate request.
     * */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        //log("onReceivedClientCertRequest")
        val clientCert = CustomCertificateHelper.prepareClientKeyCertificates(provider)
        if (clientCert == null) {
            //log("clientCert is null, request.cancle")
            request.cancel()
            return
        }

        if (clientCert.key != null && !clientCert.certificates.isNullOrEmpty()) {
            request.proceed(clientCert.key, clientCert.certificates)
        } else {
            //log("clientCert is partial, request.cancle")
            request.cancel()
        }
    }

    /**
     * Notify the host application of a resource request and allow the application to return the data.
     * If the return value is null, the WebView will continue to load the resource as usual. Otherwise,
     * the return response and data will be used.
     * */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        //return handleRequestViaOkHttp(request.url.toString())
        //return processRequest(request.url.toString())
        return null
    }

    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
        //return handleRequestViaOkHttp(url)
        return processRequest(url)
    }


    /**
     * Notify the host application that an SSL error occurred while loading a resource.
     * The host application must call either SslErrorHandler#cancel or SslErrorHandler#proceed.
     * */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        if (CustomCertificateHelper.isServerTrusted(provider, error)) {
            // log("isServerTrusted = true")
            handler.proceed()
        } else {

            var message = when (error.primaryError) {
                SslError.SSL_UNTRUSTED -> "The certificate authority is not trusted."
                SslError.SSL_EXPIRED -> "The certificate has expired."
                SslError.SSL_IDMISMATCH -> "The certificate Hostname mismatch."
                SslError.SSL_NOTYETVALID -> "The certificate is not yet valid."
                else -> "SSL Certificate error."
            }
            message += " Do you want to continue anyway?"

            //log("isServerTrusted = false, to show dlg: $message")

            AlertDialog.Builder(context).run {
                setTitle("SSL Certificate Error")
                setMessage(message)
                setPositiveButton("continue") { dialog, which -> handler.proceed() }
                setNegativeButton("cancel") { dialog, which -> handler.cancel() }
                create().show()
            }
        }
    }


    private fun processRequest(webUrl: String): WebResourceResponse {
        //log("to using HttpsURLConnection processRequest: $webUrl ")
        try {
            val url = URL(webUrl)
            val urlConnection = url.openConnection() as HttpsURLConnection
            urlConnection.setSSLSocketFactory(CustomCertificateHelper.getSSLSocketFactory(provider))
            urlConnection.setHostnameVerifier { hostname, session ->
                val match = provider.host().matches(Regex("http(s)?://$hostname/"))
                //log("hostname=$hostname, match=$match")
                hostname.isIp() || match
            }

            val `is` = urlConnection.getInputStream()
            val contentType = urlConnection.getContentType()
            val encoding = urlConnection.getContentEncoding()

            if (contentType != null) {
                var mimeType: String = contentType

                if (contentType.contains(";")) {
                    mimeType =
                        contentType.split(";".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[0].trim({ it <= ' ' })
                }

                return WebResourceResponse(mimeType, encoding, `is`)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return WebResourceResponse(null, null, null)
    }


    /**
     * TODO：封装有错误，work unnormally
     * */
    private fun handleRequestViaOkHttp(url: String): WebResourceResponse? {
        if (okHttpClient == null) return null
        try {
            val response = okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .build()
            ).execute()

            return WebResourceResponse(
                response.header("content-type", "text/plain"),
                response.header("content-encoding", "utf-8"),
                response.body?.byteStream()
            )
        } catch (e: Exception) {
            Log.w(TAG,"exception: ${e.message}")
            return WebResourceResponse(
                "content-type:text/plain",
                "content-encoding: utf-8",  // Again, you can set another encoding as default
                e.message?.byteInputStream(Charset.forName("UTF-8"))
            )// return response for bad request
        }
    }

}
