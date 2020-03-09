package com.github.rwsbillyang.dsldata

import android.util.Log

/**
 * 数据结果状态
 */
enum class Status {
    /**
     * 网络不可用
     * */
    NO_NETWORK,
    /**
     * 正在加载状态
     * */
    LOADING,

    /**
     * 请求错误，一般为网络错误，如404，403，401等
     * */
    HTTP_ERR,

    /**
     * 请求结果正常
     * */
    HTTP_OK
}

/**
 * 封装了请求获取的数据结果
 * @param status 请求状态
 * @param identifier: 表示发起调用的identifer，调用者发起调用时提供，然后Result原样返回，用于标识某个请求
 * @param data  T类型结果数据
 * @param msg 请求出错时的错误信息
 * @param httpStatus http请求状态码
</T> */
data class StateResult<out T>(val status: Status,
                           val identifier: Any?,
                           val data: T?,
                           val msg: String? = null,
                           val httpStatus: Int? = null)
{
    companion object {
        /**
         * 构建一个请求正常的数据结果
         * */
        fun <T> ok(identifier: Any?, data: T?)
                = StateResult(Status.HTTP_OK, identifier,data,null,200)

        /**
         * 构建一个网络请求错误的数据结果
         * */
        fun <T> err(identifier: Any?, msg: String, data: T? = null, httpStatus: Int? = null)
                = StateResult(Status.HTTP_ERR, identifier, data, msg, httpStatus)

        /**
         * 标明正在loading
         * */
        fun <T> loading(identifier: Any?, data: T? = null, msg: String? = null)
                = StateResult(Status.LOADING, identifier, data, msg)

        /**
         * 没有网络的错误
         * */
        fun <T> noNetwork(identifier: Any?,data: T? = null)
                = StateResult(Status.NO_NETWORK, identifier,null)
    }
}

/**
 * DSL配置各种类型的处理函数，用于处理DataResult
 * 若在DSL未被配置，则使用默认的defaultHandler去处理，defaultHandler可以通过全局进行配置
 *
 * @param dt 待处理的dataResult
 * @param init DSL配置，用于给DataResultHandlerConfig中的成员变量（即用于处理各种类型的dataResult的函数代码块）赋值，
 * 赋值之后该值将被用于处理对应的类型的dataResult
 *
 <code>
    handleDataResult(dt){
        onLoading {   }
        onNoNetwork {  }
        onHttpErr {msg, httpStatus ->  }
        onHttpOK {data ->  }
    }
    handleDataResultFull(dt){
        onLoadingFull { identifier, data, msg ->  }
        onNoNetworkFull { identifier, data ->  }
        onHttpErrFull { identifier, msg, data, httpStatus ->  }
        onHttpOKFull { identifier, data ->  }
    }
 </code>
 * */
fun <T> handleStateResult(dt: StateResult<T>, init: StateResultHandlerConfig<T>.() -> Unit)
        = StateResultHandlerConfig<T>().apply { init() }.handle(dt)

fun <T> handleStateResultFull(dt: StateResult<T>, init: StateResultHandlerConfig<T>.() -> Unit)
        = StateResultHandlerConfig<T>().apply { init() }.handle(dt)

/**
 * app通过handleDataResult使用DSL，配置各种处理函数，替换DataResultHandlerConfig里面的成员变量，实现自己的处理
 * 若在DSL未被配置，则使用默认的defaultHandler去处理，defaultHandler可以通过全局进行配置
 * */
open class StateResultHandlerConfig<T>{
    open fun handle(dt: StateResult<T>) {
        when (dt.status) {
            Status.NO_NETWORK ->
                onNoNetworkFunc?.apply { invoke() }?: StateResultHandlerManager.defaultHandler?.onNoNetwork()

            Status.LOADING ->
                onLoadingFunc?.apply { invoke() }
                    ?: StateResultHandlerManager.defaultHandler?.onLoading()

            Status.HTTP_ERR ->
                onHttpErrFunc?.apply { invoke(dt.msg!!,dt.httpStatus) } ?: StateResultHandlerManager.defaultHandler?.onHttpErr(dt.msg,dt.httpStatus)

            Status.HTTP_OK ->
                if (onHttpOKFunc != null) {
                    onHttpOKFunc!!.invoke(dt.data)
                } else {
                    if (dt.data == null) {
                        Log.e("DataResult", "responseBox is null")
                        StateResultHandlerManager.defaultHandler?.onHttpOKIfDataIsNull()
                    } else {
                        StateResultHandlerManager.defaultHandler?.onHttpOK(dt.data)
                    }
                }
        }
    }

    protected var onNoNetworkFunc: (() -> Unit)? = null
    protected var onLoadingFunc: (() -> Unit)? = null
    protected var onHttpErrFunc: ((msg: String, httpStatus: Int? ) -> Unit)? = null
    protected var onHttpOKFunc:((data: T?) -> Unit)? = null


    /**
     * DSL配置，指定无网络时的处理代码
     * */
    fun onNoNetwork(func: () -> Unit){
        onNoNetworkFunc = func
    }

    /**
     * DSL配置，指定loading时的处理代码
     * */
    fun onLoading(func: () -> Unit){
        onLoadingFunc = func
    }

    /**
     * DSL配置，指定出现http诸如401，404错误时的处理代码
     * */
    fun onHttpErr(func: (msg: String, httpStatus: Int? ) -> Unit){
        onHttpErrFunc = func
    }

    /**
     * DSL配置，指定出现http请求OK时的处理代码，若指定了则完全由指定的代码进行处理。
     * 若没指定，则交由DataResultHandlerManager中的defaultHandler（若存在的话）中
     * 的onHttpOK和onHttpOKIfDataIsNull去处理
     * */
    fun onHttpOK(func: (data: T?) -> Unit){
        onHttpOKFunc = func
    }

}

/**
 * 如果使用full版本，参数更多更全面
 *
 * 大多数情况下，我们需要使用少量真正有效的参数，比如无需使用请求标识符，如果使用full版本，
 * 将导致app写lambda时，书写大量参数，导致代码不简洁，也额外增加输入工作量。
 * 因此，只有需要时才启用full版本
 * */
open class StateResultHandlerConfigFull<T>{
    open fun handle(dt: StateResult<T>) {
        when (dt.status) {
            Status.NO_NETWORK ->
                onNoNetworkFunc?.apply { invoke(dt.identifier, dt.data) }?: StateResultHandlerManager.defaultHandler?.onNoNetwork(dt.identifier)

            Status.LOADING ->
                onLoadingFunc?.apply { invoke(dt.identifier, dt.data, dt.msg) }
                    ?: StateResultHandlerManager.defaultHandler?.onLoading(dt.identifier)

            Status.HTTP_ERR ->
                onHttpErrFunc?.apply {
                    invoke(
                        dt.identifier,
                        dt.msg!!,
                        dt.data,
                        dt.httpStatus
                    )
                } ?: StateResultHandlerManager.defaultHandler?.onHttpErr(
                    dt.msg,
                    dt.httpStatus,
                    dt.identifier
                )

            Status.HTTP_OK ->
                if (onHttpOKFunc != null) {
                    onHttpOKFunc!!.invoke(dt.identifier, dt.data)
                } else {
                    if (dt.data == null) {
                        Log.e("DataResult", "responseBox is null")
                        StateResultHandlerManager.defaultHandler?.onHttpOKIfDataIsNull(dt.identifier)
                    } else {
                        StateResultHandlerManager.defaultHandler?.onHttpOK(
                            dt.data,
                            dt.identifier
                        )
                    }
                }
        }
    }


    protected var onNoNetworkFunc: ((identifier: Any?,data: T?) -> Unit)? = null
    protected var onLoadingFunc: ((identifier: Any?, data: T? , msg: String?) -> Unit)? = null
    protected var onHttpErrFunc: ((identifier: Any?, msg: String, data: T?, httpStatus: Int? ) -> Unit)? = null
    protected var onHttpOKFunc:((identifier: Any?, data: T?) -> Unit)? = null

    /**
     * DSL配置，指定无网络时的处理代码
     * */
    fun onNoNetwork(func: (identifier: Any?,data: T?) -> Unit){
        onNoNetworkFunc = func
    }

    /**
     * DSL配置，指定loading时的处理代码
     * */
    fun onLoading(func: (identifier: Any?, data: T? , msg: String?) -> Unit){
        onLoadingFunc = func
    }

    /**
     * DSL配置，指定出现http诸如401，404错误时的处理代码
     * */
    fun onHttpErr(func: (identifier: Any?, msg: String, data: T?, httpStatus: Int? ) -> Unit){
        onHttpErrFunc = func
    }

    /**
     * DSL配置，指定出现http请求OK时的处理代码，若指定了则完全由指定的代码进行处理。
     * 若没指定，则交由DataResultHandlerManager中的defaultHandler（若存在的话）中
     * 的onHttpOK和onHttpOKIfDataIsNull去处理
     * */
    fun onHttpOK(func: (identifier: Any?, data: T?) -> Unit){
        onHttpOKFunc = func
    }
}



/**
 * App通过此manager设置缺省的handler，若DSL中未配置相应的处理函数，将使用此处配置的
 * 用于APP自定义一个IDataResultHandler之后，指定给SDK作为默认的handler处理器
 * */
object StateResultHandlerManager{
    var defaultHandler: IStateResultHandler? = null
}


/**
 * 处理DataResult的接口，可以实现此接口然后将其设置默认的处理器
 * */
interface IStateResultHandler{
    /**
     * 正在发送请求
     * @param identity 请求标识符
     * */
    fun onLoading(identity: Any? = null)

    /**
     * 网络不可用
     * @param identity 请求标识符
     * */
    fun onNoNetwork(identity: Any? = null)

    /**
     * 请求发生了错误，如401，403，404，500等错误
     * @param httpStatus 请求出错，即http返回的状态码，如404，401等
     * @param msg 具体的错误信息
     * @param identifier 请求标识符
     * */
    fun onHttpErr(msg: String?, httpStatus: Int?, identifier: Any? = null)

    /**
     * 请求正常 应交由IResponseBoxHandler去处理
     * @param code 业务逻辑中的返回码
     * @param msg 具体的错误信息
     * @param data 最终的结果数据
     * @param identity 请求标识符
     * */
    fun <T> onHttpOK(data: T, identity: Any? = null)

    /**
     * 请求正常,但里面的DataResult.data为空，此种情况较少见
     * */
    fun  onHttpOKIfDataIsNull(identity: Any? = null)
}

