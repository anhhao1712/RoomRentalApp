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
    fun service(context: Context): ApiService {
        val session = SessionManager(context.applicationContext)
        val auth = Interceptor { chain ->
            val request = chain.request().newBuilder().apply { session.token?.let { header("Authorization", "Bearer $it") } }.build()
            chain.proceed(request)
        }
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(auth).addInterceptor(logger).connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        return Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}
