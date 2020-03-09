# DslData
A DSL framework which fetch/submit data from/to remote server(or/and local storage) based on Android LiveData, Kotlin coroutines, retrofit2 and okhttp.

## setup
```
 implementation 'com.github.rwsbillyang:dsldata:1.0.0'
```
## Quick Start
### Fetch/Submit Data DSL
code demo
```kotlin
    //eg1: only fetch data from loacal storage
    fun getWordList(filter: WordListFilter, pageSize: Int)= dataFetcher<List<String>>
    {
        local { dao.getWordLearnBriefList(filter,pageSize) }
        isForceRefresh { false } //defaut true
    }

    //eg2: only from remote server and save the resulet data into local storage if success
    fun fetchLearnedWordListFromRemote()
            = dataFetcher<LearnWordListResponse>
    {
        //if set true, the finally return result is from local after "update" by remote 
        //enableRemoteAsBackend = true 
        remote { api.getLearnedWordList() }
        saveRemote {
            if(!it.data.isNullOrEmpty()){
                saveWordLearnBriefBeanList(it.data!!)
            }
            0 //only makes sense when enableRemoteAsBackend=true and has local block
            
        }
        isForceRefresh { true }
    }



    //eg3: firstly fetches data from local, if the result is null or forceRefresh is true, then fetch from remote 
   fun fetchWordBook(id: Int,forceRefresh:Boolean = false): LiveData<StateResult<WordBookResponse>>
            = dataFetcher<WordBookResponse>
    {
        local { WordBookResponse(data = dao.fetchWordBook(id)) }
        remote { api.fetchWordBook(id) }
        saveRemote { if(it.data!=null) dao.upsertWordBook(it.data!!) else 0 }
        isForceRefresh{ forceRefresh  || it?.data == null }
    }

   //eg4: save the data into local, if forceRefresh is true, then submit to the remote
   fun setKnowLevel(word: String,level: Int,  forceRefresh: Boolean = true)  = dataFetcher<StringDataBox>
    {
        local {
            dao.updateKnowLevel(word, level)
            StringDataBox()
        }
        remote { api.setKnowLevel(word, level) }

        isForceRefresh { forceRefresh }
    }

    //eg5: more parameters 
    fun phoneLogin(phone: String, verify: Int, code: String?) = dataFetcher<LoginResponse>
    {
        enableLog = SDKConfig.enableLog
        debugName = "phoneLogin"
        identifier = "$phone"
        enableMetrics = SDKConfig.enableLog

        remote { api.phoneLogin(phone,verify,code) }
        
        isForceRefresh { true }
    }
    

    // eg6: remote returns ResponseBox<List<WordLearnBriefBean>>, but the final return result should be ResponseBox<List<String>>
    // so need a converter
    fun getLearnedWordPage(filter: WordListFilter, pageSize: Int,  forceRefresh: Boolean)
            = dataFetcher2<ResponseBox<List<String>>,ResponseBox<List<WordLearnBriefBean>>>
    {
        local { ResponseBox(data = learnDao.getLearnedWordPage(filter,pageSize)) }
        remote { api.getLearnedWordPage(filter,pageSize) }
        saveRemote {
            if(!it.data.isNullOrEmpty()){
                saveWordLearnBriefBeanList(it.data)
            }
            0
        }
        converter {
            if(it == null) null
            else ResponseBox(it.err, it.msg, it.data?.map { it.word })
        }
        isForceRefresh { forceRefresh }
    }
```


If need global control, set the parameters in your init code:
```kotlin
        DataFetcher.EnableLog = enableLog
        DataFetcher.EnableMetrics = enableLog
        DataFetcher.TAG = "sdk"
```

### Handle data DSL

Two versions:
```kotlin
handleStateResult(dt){
        onLoading { }
        onNoNetwork {  }
        onHttpErr { msg, httpStatus ->  }
        onHttpOK { data ->  }
    }
    
handleStateResultFull(dt){
        onLoading { identifier, data, msg ->  }
        onNoNetwork { identifier, data ->  }
        onHttpErr { identifier, msg, data, httpStatus ->  }
        onHttpOK { identifier, data ->  }
    }
```

Example:
```kotlin
  loginViewModel.sendCodeByEmail(email).observe(viewLifecycleOwner, Observer {
                        handleStateResult(it){
                            onNoNetwork {
                                Toast.makeText(this@EmailLoginFragment.requireContext(),R.string.network_unavailable, Toast.LENGTH_LONG).show()
                                sendEmailVerifyCodeView.isEnabled  = true
                            }
                            onHttpErr { msg, httpStatus ->
                                Toast.makeText(this@EmailLoginFragment.requireContext(),"$httpStatus:$msg", Toast.LENGTH_LONG).show()
                                sendEmailVerifyCodeView.isEnabled  = true
                            }
                            onHttpOK  {
                                if(it?.err == Code.OK){
                                    emailLogin.isEnabled = true
                                    sendEmailVerifyCodeView.isEnabled = true
                                }
                            }
                        }
                    })
```

### config retrofit/okhttp

```kotlin
class MyClientConfiguration : ClientConfiguration {
    override fun host() = "you api host"

    //...

    override fun adapterFactory(): Converter.Factory = JacksonConverterFactory.create()

}
```

For adapterFactory, any one of retrofit2 adapter:
```kotlin
RxJava2CallAdapterFactory.create()

JacksonConverterFactory.create()

GsonConverterFactory.create(GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create())

CoroutineCallAdapterFactory()

Json.asConverterFactory("application/json".toMediaType()) //kotlinx.serialiation
```

Do not forget the related dependency:
```gradle
    //retrofit2 adapter
    //implementation "com.squareup.retrofit2:adapter-rxjava2:$rootProject.ext.retrofitVersion"
    
    implementation "com.squareup.retrofit2:converter-jackson:2.7.2"
    implementation "com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+"
    
    //implementation "com.squareup.retrofit2:converter-gson:$rootProject.ext.retrofitVersion"
    //implementation 'com.jakewharton.retrofit:retrofit2-kotlin-coroutines-adapter:0.9.2'
    //implementation 'com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:0.4.0'
```
