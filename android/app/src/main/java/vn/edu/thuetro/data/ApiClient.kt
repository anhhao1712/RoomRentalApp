package vn.edu.thuetro.data

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.edu.thuetro.BuildConfig
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    private var retrofit: Retrofit? = null

    fun service(context: Context): ApiService {
        val appContext = context.applicationContext

        val instance = retrofit ?: synchronized(this) {
            retrofit ?: createRetrofit(appContext).also {
                retrofit = it
            }
        }

        return instance.create(ApiService::class.java)
    }

    private fun createRetrofit(context: Context): Retrofit {
        val session = SessionManager(context)

        val auth = Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .apply {
                    session.token?.let {
                        header("Authorization", "Bearer $it")
                    }
                }
                .build()

            chain.proceed(request)
        }

        val logger = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
