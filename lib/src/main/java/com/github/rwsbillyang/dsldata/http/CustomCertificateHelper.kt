package com.github.rwsbillyang.dsldata.http

import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.*
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*

class ClientKeyCertificates(val key: PrivateKey?, val certificates: Array<X509Certificate?>?)

internal object CustomCertificateHelper {

    fun getSSLSocketFactory(provider: ClientConfiguration): SSLSocketFactory? {

        //val trustMangers = prepareTrustManagerFactory(trustedInputStreamArray[0], trustStorePwd).trustManagers
        val trustMangers = prepareTrustManagerFactory(provider.trustCertInputStreamList()!!)

        if (trustMangers.size != 1 || trustMangers[0] !is X509TrustManager) {
            throw IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustMangers))
        }

        val keyManagers =
            prepareKeyManager(provider.clientCertInputStream(), provider.clientPass(), provider.clientCertType())

        val sslSocketFactory = SSLContext.getInstance("TLS")
            .apply {
                init(keyManagers, trustMangers, SecureRandom())
            }
            .socketFactory

        return if (Build.VERSION.SDK_INT in 16 until 22) Tls12SocketFactory(sslSocketFactory) else sslSocketFactory
    }


    /**
     * @param inputStream 受信任证书输入流  输入流的格式必须为pem格式,通常为crt文件
     * @param password 受信任证书密码
     * @return 返回添加了受信任证书的TrustManagerFactory
     *
     * Load CAs from an InputStream，could be from a resource or ByteArrayInputStream or ...
     *
     * https://developer.android.google.cn/training/articles/security-ssl
     * */
     fun prepareTrustManagerFactory(caInput: InputStream):Array<TrustManager>{
        val cf: CertificateFactory = CertificateFactory.getInstance("X.509")
        // From https://www.washington.edu/itconnect/security/ca/load-der.crt
        //val caInput: InputStream = BufferedInputStream(FileInputStream("load-der.crt"))
        val ca: X509Certificate = caInput.use {
            cf.generateCertificate(it) as X509Certificate
        }
        caInput.close()

        //log("ca=" + ca.subjectDN)

        // Create a KeyStore containing our trusted CAs
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca", ca)
        }

        // Create a TrustManager that trusts the CAs inputStream our KeyStore
        val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
        val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
            init(keyStore)
        }

        return tmf.trustManagers
    }
    /**
     * @param inputStream 受信任证书输入流列表，受信任证书为列表元素 输入流的格式必须为bks格式
     * @param password 受信任证书密码
     * @return 返回添加了受信任证书的TrustManagerFactory
     * */
     fun prepareTrustManagerFactory(inputStreamArray: List<InputStream>):Array<TrustManager>{

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        val certificateFactory = CertificateFactory.getInstance("X.509")
        var inputStream: InputStream?
        for (i in 0 until inputStreamArray.size) {
            inputStream = inputStreamArray.get(i)
            keyStore.setCertificateEntry("ca$i", certificateFactory.generateCertificate(inputStream))
            inputStream.close()
        }

        //Create a TrustManager that trusts the CAs in our keyStore
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        return trustManagerFactory.trustManagers
    }


    /**
     * @param inputStream 客户端证书输入流，来自第三个参数type格式文件
     * @param password 证书密码
     * @param type 输入流格式，BKS、PKCS12等 但BKS不工作，nginx提示未发送证书，原因未明，可能证书格式有问题？
     * @return 返回添加了身份认证证书的KeyManagerFactory.keyManagers
     * */
     fun prepareKeyManager(inputStream: InputStream?, pass: String?, type: String): Array<KeyManager>? {
        try {
            if (inputStream == null) return null
            val pwd = pass?.toCharArray()
            val keyStore = KeyStore.getInstance(type)
            keyStore.load(inputStream, pwd)
            inputStream.close()

            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, pwd)
            return keyManagerFactory.keyManagers
        } catch (e: KeyStoreException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: UnrecoverableKeyException) {
            e.printStackTrace()
        } catch (e: CertificateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * 通过配置提供client证书的keyStore
     * */
    fun prepareClientKeyCertificates(provider: ClientConfiguration):ClientKeyCertificates?
    {
        val inputStream = provider.clientCertInputStream()
        if (inputStream == null) return null

        val pwd = provider.clientPass()?.toCharArray()

        val keyStore = KeyStore.getInstance(provider.clientCertType())
        keyStore.load(inputStream, pwd)
        inputStream.close()

        var mCertificates: Array<X509Certificate?>? = null
        var mPrivateKey: PrivateKey? = null

        for(alias in keyStore.aliases())
        {
            val key = keyStore.getKey(alias, provider.clientPass()?.toCharArray())
            if (key is PrivateKey) {
                mPrivateKey = key
                val arrayOfCertificate = keyStore.getCertificateChain(alias)
                mCertificates = arrayOfNulls<X509Certificate>(arrayOfCertificate.size)
                for (i in 0 until mCertificates.size) {
                    mCertificates[i] = arrayOfCertificate[i] as X509Certificate
                }
                break
            }
        }

        return ClientKeyCertificates(mPrivateKey, mCertificates)
    }

    /**
     * 检查server证书是否被信任
     * */
    fun isServerTrusted(provider: ClientConfiguration, error: SslError):Boolean
    {
        try {
            //Get the X509 trust manager from your ssl certificate
            val trustMangers = prepareTrustManagerFactory(provider.trustCertInputStreamList()!!)

            if (trustMangers.size != 1 || trustMangers[0] !is X509TrustManager) {
                throw IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustMangers))
            }
            val trustManager:X509TrustManager = trustMangers[0] as X509TrustManager


            //Get the certificate from error object
            val bytes = SslCertificate.saveState(error.certificate).getByteArray("x509-certificate")
            val x509Certificate: X509Certificate? = if (bytes == null) {
                null
            } else {
                CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            }
            val serverCertificates = arrayOfNulls<X509Certificate>(1)
            serverCertificates[0] = x509Certificate

            // check weather the certificate is trusted
            trustManager.checkServerTrusted(serverCertificates, "ECDH_RSA")

            //log("Certificate from  ${error.url} is trusted.")

            return true
        } catch (e: Exception) {
           // logw("Failed to access ${error.url}  Error + ${error.primaryError}")
           return false
        }
    }


}