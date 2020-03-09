package com.github.rwsbillyang.dsldata


import android.app.Application
import android.net.ConnectivityManager

/**
 * app应该继承自该APP，可以感知是否有网络
 * */
open class NetAwareApplication: Application() {

    companion object {
       lateinit var Instance: NetAwareApplication
    }

    var CONNECTIVITY_MANAGER: ConnectivityManager? = null
    override fun onCreate() {
        super.onCreate()

        CONNECTIVITY_MANAGER = this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        Instance = this
    }



    fun isNetworkAvailable(): Boolean = CONNECTIVITY_MANAGER?.activeNetworkInfo?.isConnected ?: false
    //    {
//        if(CONNECTIVITY_MANAGER == null) {
//            logw("CONNECTIVITY_MANAGER is null")
//            return false
//        }
//        if(CONNECTIVITY_MANAGER!!.activeNetworkInfo == null)
//        {
//            logw("CONNECTIVITY_MANAGER.activeNetworkInfo is null")
//            return false
//        }
//        log("state= $(CONNECTIVITY_MANAGER!!.activeNetworkInfo!!.detailedState)")
//       return CONNECTIVITY_MANAGER!!.activeNetworkInfo!!.isConnected
//    }





}