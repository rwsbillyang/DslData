package com.github.rwsbillyang.dsldata


import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import kotlinx.coroutines.*
import retrofit2.Call
import java.io.IOException
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 从远程和本地均返回的T类型数据
 *
 * 这是最常用的情景
 *
 * @param init DataFetcher的各lambda初始化代码块
 * @return LiveData<Result<T>> 需要在主线程中observe然后进行UI更新，且须由lifeCycleOwner进行observe
 * 返回值不能在主线程中进行observeForever
 * */
fun <T> dataFetcher(init: DataFetcher<T,T>.() -> Unit): LiveData<StateResult<T>>
        = observableDataFetcher(init).asLiveData()

/**
 * dataFetcher的observable版，去除LiveData在background中运行的限制
 * */
fun <T> observableDataFetcher(init: DataFetcher<T,T>.() -> Unit): ObservableResult<StateResult<T>> {
    val fetcher = DataFetcher<T,T>()
    fetcher.init()
    fetcher.fetchData()
    return fetcher.observableResult
}

/**
 * 远程返回类型为R，不是所需的类型时
 * */
fun <T,R> dataFetcher2(init: DataFetcher<T,R>.() -> Unit): LiveData<StateResult<T>>
        = observableDataFetcher2(init).asLiveData()

fun <T,R> observableDataFetcher2(init: DataFetcher<T,R>.() -> Unit): ObservableResult<StateResult<T>> {
    val fetcher = DataFetcher<T,R>()
    fetcher.init()
    fetcher.fetchData()
    return fetcher.observableResult
}



/**
 * 将数据结果转换为可观察的Observable
 * */
class ObservableResult<T>(var data: T? = null): Observable()
{
    /**
     * 只要有赋值动作，就会触发观察者调用，且和观察者处在同一调用线程中，对于UI观察者需注意
     * */
    fun setValue(value: T?)
    {
        data = value

        result.postValue(value)

        setChanged()// mark as value changed
        notifyObservers(value)// trigger notification
    }

    private val result = MediatorLiveData<T>()
    /**
     * 请求得到的数据储存于此，被viewModel中的LiveData观察
     * */
    fun asLiveData() = result as LiveData<T>

}

/**
 * 数据获取期，亦适用于于提交数据
 * T为请求数据类型，R为远程请求返回的数据类型，二者可能不一致
 * */
open class DataFetcher<T,R>{
    companion object{
        /**
         * 日志开关，默认true
         * 全局控制开关，不特殊指定的话，将使用全局配置，而全局配置又可以在app初始化时进行统一指定
         * */
        var EnableLog = true

        /**
         * metrics性能衡量开关，默认false
         * */
        var EnableMetrics = false

        var TAG = "kdata"
    }

    /**
     * 请求得到的数据储存于此，被viewModel中的LiveData观察
     * */
    val observableResult = ObservableResult<StateResult<T>>()



    /**
     * 是否打开log，默认true
     * */
    var enableLog = EnableLog
    /**
     * 是否打开metric的控制开关，用于输出耗时数据
     * */
    var enableMetrics = EnableMetrics


    /**
     * 添加上名字，便于调试知道是哪个dataFetcher在执行取数据操作
     * */
    var debugName: String = ""

    /**
     * 表示发起调用的identifier，调用者发起调用时提供，然后在Result中原样返回
     * 可以标示出该Result是哪次发起的调用的返回结果.
     * 若不提供，将为null，返回的Result中的identifier同样为null
     *
     * 上面的debugName为repository提供调试名称，不向最终的调用者暴露该信息，同时它只是表示某一类调用，不表示某一个调用
     * */
    var identifier: Any? = ""

    /**
     *
     * remoteAsBackend == false时，本地存储作为aside模式时：将首先以来自远程网络的数据为准，获取网络数据失败时才考虑本地数据
     *
     * 为true时，网络数据将作为本地数据的backend，网络数据先进行本地存储，再对本地存储进行查询。
     * 即：若需要从远程获取数据，并且save后有修改记录（即save代码块返回值大于0），然后再从本地查询一次作为结果返回
     * 通常适用于列表分页数据，如将远程数据与本地数据合并后再进行分页查询。
     *
     * 为true时，只有在local和remote都有效时，才能执行，否则跳过
     *
     *
     * 默认为false表示获取的结果是以远程数据为准（若forceRefresh的话）
     *
     *
     * */
    var enableRemoteAsBackend = false

    /**
     * 设置为true，则在新线程的协程里取数据；为false则在协程里取数据。
     * 若使用ROOM作为本地存储来说，必须设置为true，否则android报异常
     * */
    var enableCreateNewThread = false


    private var localFunc: (() -> T?)? = null
    private var remoteFunc: (() -> Call<R>)? = null
    /**
     * 返回受影响的记录条数，只在enableRemoteAsBackend = true时起作用，决定着是否再次查询本地数据
     * */
    private var saveRemoteFunc: ((R) -> Int)? = null

    /**
     * 将ApiSuccessResponse<RequestType>转换成RequestType类型
     * */
    private var processResponseFunc: ((ApiSuccessResponse<R>) -> R) = { it.body }

    /**
     * 是否强制刷新，即强制从网络获取数据,适用于remoteAsBackend=false
     * */
    private var isForceRefreshBlock: ((T?) -> Boolean) = { true }

    private var onFetchFailBlock: ((code: Int?, msg: String?) -> Unit)? = null


    private var convertFunc: (R?) -> T? = { if(it==null) null else it as T}

    /**
     * 从本地加载或向本地加载数据的函数，返回的数据T类型数据
     *
     * 默认为空，表示不从本地获取数据
     * */
    fun local(func: () -> T?) {
        localFunc = func
    }


    /**
     * 从远程加载或向远程提交数据的的函数，返回的是一个Call调用，返回的数据T类型数据
     * 可为空，表示不从远程加载
     * */
    fun remote(func: () -> Call<R>) {
        remoteFunc = func
    }

    /**
     * 将远程返回的类型转换为最终需要返回的类型
     * */
    fun converter(func: (R?) -> T?) {
        convertFunc = func
    }
    /**
     * 存入本地storage的代码块，将从远程网络返回的数据传递给存储代码块
     * 默认为空不执行任何操作
     *
     * 只在enableRemoteAsBackend = true,且指定了remote、并且返回OK的情况下，才会执行此操作
     *
     * 该代码块保存影响的记录，若没有影响或出错则返回0，否则返回真实的影响记录，这时将导致从
     * */
    fun saveRemote(func: (R) -> Int) {
        saveRemoteFunc = func
    }

    /**
     * 是否强制刷新，即是否强制从远程获取最新数据
     *
     * 传递的数据通常是从本地存储中获取的数据，然后对其进行判断是否该获取最新的数据
     *
     * 为true时，才尝试从远程获取，并尝试保存获取结果
     * */
    fun isForceRefresh(block: (T?) -> Boolean) {
        isForceRefreshBlock = block
    }

    /**
     * 将ApiSuccessResponse转换成RequestType，通常是response.body就是所需的payload数据，
     * 可以对远程返回的响应结果进行额外处理，但如果响应头中还有额外的payload数据，可以在此做额外的操作
     * */
    fun processResponse(func: (ApiSuccessResponse<R>) -> R) {
        processResponseFunc = func
    }


    /**
     * 从网络返回数据失败时的回调
     *
     * 默认为空不执行任何操作
     * */
    fun onFetchFail(block: (code: Int?, msg: String?) -> Unit) {
        onFetchFailBlock = block
    }


    /**
     * 更新结果，更新后会通知UI/ViewModel中的监察者
     * */
    private fun setValue(newValue: StateResult<T>?) {
        if (newValue != null && observableResult.data != newValue)
        {
            observableResult.setValue(newValue)
        }
    }

    private fun log(msg: String){
        if(enableLog) Log.d(TAG,msg)
    }

    /**
     * 先从本地获取数据，并通知给上层观察者；
     * 然后在支持刷新且网络可用时，再向网络请求数据；若网络不可用，先通知UI上层观察者
     * 若请求成功，则保存数据到本地
     *
     * 若为remoteAsBackend模式时： 再同步保存到本地后再从本地获取数据。
     *
     *
     * https://developer.android.google.cn/kotlin/coroutines
     * https://developer.android.google.cn/topic/libraries/architecture/coroutines
     * */
    open fun fetchData() = CoroutineScope(Dispatchers.IO).launch(
        if (enableCreateNewThread) newSingleThreadContext("io") else EmptyCoroutineContext
    ) {

        val localResult = executeLocal()?.also {
            setValue(StateResult.ok(identifier,it))
        }

        if (isForceRefreshBlock(localResult)) {
            if(!NetAwareApplication.Instance.isNetworkAvailable()){
                setValue(StateResult.noNetwork(identifier,null))
            }else{
                executeRemote()?.let {
                    val stateResult: StateResult<R> = it.toStateResult()
                    if(stateResult.status == Status.HTTP_OK && it.trySaveIntoLocalStorage() > 0 && enableRemoteAsBackend)
                    {
                        log("$debugName:$identifier reload data from local after save...")
                        setValue(StateResult.ok(identifier,  executeLocal()))
                    }else
                    {
                        setValue(StateResult(stateResult.status, stateResult.msg, convertFunc(stateResult.data)))
                    }
                }
            }
        }
    }


    private suspend fun executeLocal(): T? {
        localFunc?.let {
            setValue(StateResult.loading(identifier,null))
            log("$debugName:$identifier fetch data from local...")
            return if(enableMetrics)
            {
                val now = System.currentTimeMillis()
                val ret = withContext(Dispatchers.IO) { localFunc!!() }
                val delta = System.currentTimeMillis()- now
                log("$debugName:$identifier fetch data from local, spend $delta ms")

                ret
            }else
            {
                withContext(Dispatchers.IO) { localFunc!!() }
            }
        }
        return null
    }

    private suspend fun executeRemote(): ApiResponse<R>? {
        remoteFunc?.let {
            setValue(StateResult.loading(identifier,null))
            log("$debugName:$identifier fetch data from remote...")
            return if(enableMetrics){
                val now = System.currentTimeMillis()
                val ret = withContext(Dispatchers.IO) { remoteFunc!!().toApiResponse() }
                val delta = System.currentTimeMillis()- now
                log("$debugName:$identifier fetch data from remote, spend $delta ms")

                ret
            }else
            {
                withContext(Dispatchers.IO) { remoteFunc!!().toApiResponse() }
            }
        }

        return null
    }

    /**
     * 执行call调用，根据执行结果得到不同类型ApiResponse
     * */
    private fun Call<R>.toApiResponse(): ApiResponse<R> {
        return try {
            this.execute().let {
                log(
                    "$debugName:$identifier response: message=${it.message()}, raw=${it.raw()}," +
                            " success=${it.isSuccessful},code=${it.code()},errBody=${it.errorBody()}"
                )
                if (it.isSuccessful)
                    ApiResponse.create(it)
                else {
                    ApiErrorResponse(it.message(), it.code())
                }
            }
        } catch (ioe: IOException) {
            val msg = "$debugName:$identifier IOException: ${ioe.message}"
            Log.w(TAG,msg)
            ApiErrorResponse("IOException: ${ioe.message}")
        } catch (e: RuntimeException) {
            val msg = "$debugName:$identifier RuntimeException: ${e.message}"
            Log.w(TAG,msg)
            ApiErrorResponse("RuntimeException: ${e.message}")
        } catch (e: Exception) {
            val msg = "$debugName:$identifier Exception: ${e.message}"
            Log.w(TAG,msg)
            ApiErrorResponse("Exception: ${e.message}")
        }
    }


    /**
     * 将ApiResponse转换为不同类型的StateResult
     * */
    private fun ApiResponse<R>.toStateResult(): StateResult<R> {
        return when (this) {
            is ApiEmptyResponse -> {
                Log.w(TAG,"$debugName:$identifier got empty response from remote,correct?")
                StateResult.err(identifier,"return nothing", null)
            }
            is ApiErrorResponse -> {
                onFetchFailBlock?.let { it(this.httpStatus, this.errorMessage) }
                StateResult.err(identifier,this.errorMessage, null, this.httpStatus)
            }
            is ApiSuccessResponse -> {
                StateResult.ok(identifier,processResponseFunc(this))
            }
        }
    }

    /**
     * 对ApiResponse进行保存操作，只对成功的结果进行保存
     * */
    private fun ApiResponse<R>.trySaveIntoLocalStorage(): Int {
        if (saveRemoteFunc != null && this is ApiSuccessResponse) {
            log("$debugName:$identifier save data into local async...")
            val apiResponse = processResponseFunc(this)
            return saveRemoteFunc!!(apiResponse)
        }
        return 0
    }
}