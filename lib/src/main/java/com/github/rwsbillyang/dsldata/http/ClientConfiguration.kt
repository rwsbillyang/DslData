package com.github.rwsbillyang.dsldata.http

import android.app.Application
import okhttp3.CookieJar
import okhttp3.Interceptor
import retrofit2.Converter
import java.io.InputStream

/**
 * client配置接口
 * */
interface ClientConfiguration
{
    /**
     * 主机地址，如 "http://localhost/"
     * */
    fun host(): String
    /**
     * 提供application Interceptor，无需添加日志和gzip 类型Interceptor，直接激活即可
     * */
    fun interceptors(): Array<Interceptor>? = null

    /**
     * 提供network类型Interceptor，无需日志和gzip 类型Interceptor，直接激活即可
     * */
    fun networkInterceptors(): Array<Interceptor>? = null

    /**
     * 若添加了请求头,比如userAgent，将自动配置HeaderInterceptor
     * */
    fun requestHeaders():Map<String, String>? = null

    /**
     * 是否激活日志Interceptor，默认true
     * */
    fun logEnable(): Boolean = true

    /**
     * 是否激活压缩，默认true
     * */
    fun gzipRequestEnable() = true

    //fun configHttps(builder: OkHttpClient.Builder) = {}

    /**
     * 提供CookieJar
     * */
    fun cookie(): CookieJar? = null

    /**
     * 连接超时ms，默认20000ms即20秒
     * */
    fun connectTimeoutMs(): Long = DefaultClientConfig.connectTimeoutMs

    /**
     * 读超时ms，默认10000ms即10秒
     * */
    fun readTimeoutMs(): Long = DefaultClientConfig.readTimeoutMs

    /**
     * 写超时ms，默认10000ms即10秒
     * */
    fun writeTimeoutMs(): Long = DefaultClientConfig.writeTimeoutMs


    /**
     * 是否激活自定义受信任证书，即服务器身份证书
     * */
    fun enableCustomTrust(): Boolean = false

    /**
     * 服务器身份证书输入流列表，必须打开开关enableCustomTrust
     * 须pem格式，即crt文件中内容
     * */
    fun trustCertInputStreamList(): List<InputStream>? = null


    /**
     * 用于双向认证，服务器端需要对客户端认证时，客户端身份证书输入流，必须打开开关enableCustomTrust
     * 须 PKCS12或BKS 等android支持的文件输入流
     * */
    fun clientCertInputStream():InputStream? = null

    /**
     * 客户端证书密码
     * */
    fun clientPass():String? = null

    /**
     * 客户端证书输入流格式，默认PKCS12
     * */
    fun clientCertType() :String = "PKCS12"

    /**
     * 将资源文件转换为用于转换流，方便使用之目的
     * */
    fun convertCertificatesReources(application: Application, array: Array<Int>)
            = array.map { application.resources.openRawResource(it) }

/**
 * 指定adapter
 *
 *  GsonConverterFactory.create(GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create())
 *  CoroutineCallAdapterFactory()
 *  RxJava2CallAdapterFactory.create()
 *  Json.asConverterFactory("application/json".toMediaType()) //kotlinx.serialiation
 *
 * */
    fun adapterFactory(): Converter.Factory
 }