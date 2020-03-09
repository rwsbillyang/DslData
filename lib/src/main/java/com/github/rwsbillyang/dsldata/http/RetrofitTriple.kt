package com.github.rwsbillyang.dsldata.http

import okhttp3.OkHttpClient
import retrofit2.Retrofit

internal data class RetrofitTriple(var config: ClientConfiguration,
                          var retrofit: Retrofit?,
                          var client: OkHttpClient?)