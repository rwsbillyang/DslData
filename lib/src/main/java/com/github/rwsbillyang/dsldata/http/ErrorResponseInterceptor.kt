package com.github.rwsbillyang.dsldata.http

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

import java.io.IOException

/**
 * 错误处理handler 第一个参数标识了HttpStatus状态码，第二个参数标识了错误信息
 * */
typealias OnErrHandler = (Int,String?) -> Unit

/**
 * 针对错误的Interceptor
 * */
internal class ErrorResponseInterceptor(private var errHandler: OnErrHandler): Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {

        val response = chain.proceed(chain.request())
        //Returns true if the code is in [200..300), which means the request was successfully received, understood, and accepted.
        if(response.isSuccessful)
        {
            return response
        }else
        {
            //出现诸如404，500之内的错误, refer to ErrorMap
            when(val code = response.code)
            {
                in ErrorCodes ->{
                    val msg = response.request.url.encodedPath + " return " +( (ErrorMap[code])?: response.message)
                    Log.w("ErrorInterceptor", msg)
                    errHandler(code, msg)
                }
            }
        }

        return response
    }

}