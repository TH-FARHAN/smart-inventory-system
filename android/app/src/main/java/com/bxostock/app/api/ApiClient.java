package com.bxostock.app.api;

import android.content.Context;

import com.bxostock.app.util.TokenStore;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // The live backend on Render. The trailing slash is required.
    public static final String BASE_URL = "https://smart-inventory-api-4k6o.onrender.com/api/";
    private static ApiService service;

    public static synchronized ApiService get(Context context) {
        if (service == null) {
            final Context app = context.getApplicationContext();
            OkHttpClient client = new OkHttpClient.Builder()
                    // the free Render plan can take about a minute to wake up
                    .connectTimeout(90, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .writeTimeout(90, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request req = chain.request();
                        String token = TokenStore.token(app);
                        if (token != null) {
                            req = req.newBuilder().header("Authorization", "Bearer " + token).build();
                        }
                        return chain.proceed(req);
                    })
                    .build();
            service = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return service;
    }
}