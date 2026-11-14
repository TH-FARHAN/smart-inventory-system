package com.bxostock.app.api;

import com.bxostock.app.model.AuditLog;
import com.bxostock.app.model.ChangePasswordRequest;
import com.bxostock.app.model.ResetPasswordRequest;
import com.bxostock.app.model.CaptchaResponse;
import com.bxostock.app.model.Category;
import com.bxostock.app.model.CategoryRequest;
import com.bxostock.app.model.CreateUserRequest;
import com.bxostock.app.model.Customer;
import com.bxostock.app.model.CustomerRequest;
import com.bxostock.app.model.DashboardResponse;
import com.bxostock.app.model.InventoryReport;
import com.bxostock.app.model.LoginRequest;
import com.bxostock.app.model.LoginResponse;
import com.bxostock.app.model.OrderRequest;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.Product;
import com.bxostock.app.model.ProductRequest;
import com.bxostock.app.model.SalesReport;
import com.bxostock.app.model.StockRequest;
import com.bxostock.app.model.UserResponse;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest body);

    @GET("auth/captcha")
    Call<CaptchaResponse> captcha();

    @POST("auth/logout")
    Call<Void> logout();

    @GET("dashboard")
    Call<DashboardResponse> dashboard();

    @GET("categories")
    Call<List<Category>> categories();

    @POST("categories")
    Call<Category> addCategory(@Body CategoryRequest body);

    @GET("products")
    Call<List<Product>> products(@Query("keyword") String keyword);

    @POST("products")
    Call<Product> addProduct(@Body ProductRequest body);

    @PUT("products/{id}")
    Call<Product> updateProduct(@Path("id") long id, @Body ProductRequest body);

    @DELETE("products/{id}")
    Call<ResponseBody> deleteProduct(@Path("id") long id);

    @GET("inventory/low-stock")
    Call<List<Product>> lowStock();

    @POST("inventory/stock-in")
    Call<Product> stockIn(@Body StockRequest body);

    @POST("inventory/stock-out")
    Call<Product> stockOut(@Body StockRequest body);

    @GET("customers")
    Call<List<Customer>> customers(@Query("keyword") String keyword);

    @POST("customers")
    Call<Customer> addCustomer(@Body CustomerRequest body);

    @PUT("customers/{id}")
    Call<Customer> updateCustomer(@Path("id") long id, @Body CustomerRequest body);

    @GET("orders")
    Call<List<OrderResponse>> orders();

    @POST("orders")
    Call<OrderResponse> placeOrder(@Body OrderRequest body);

    @POST("orders/{id}/cancel")
    Call<ResponseBody> cancelOrder(@Path("id") long id);

    @POST("account/change-password")
    Call<ResponseBody> changePassword(@Body ChangePasswordRequest body);

    @PATCH("admin/users/{id}/reset-password")
    Call<ResponseBody> resetPassword(@Path("id") long id, @Body ResetPasswordRequest body);

    // ----- admin only -----
    @GET("admin/users")
    Call<List<UserResponse>> users();

    @POST("admin/users")
    Call<UserResponse> addUser(@Body CreateUserRequest body);

    @PATCH("admin/users/{id}/activate")
    Call<UserResponse> activateUser(@Path("id") long id);

    @PATCH("admin/users/{id}/deactivate")
    Call<UserResponse> deactivateUser(@Path("id") long id);

    @GET("admin/audit-logs")
    Call<List<AuditLog>> auditLogs();

    @GET("reports/sales")
    Call<SalesReport> salesReport(@Query("from") String from, @Query("to") String to);

    @GET("reports/inventory")
    Call<InventoryReport> inventoryReport();
}