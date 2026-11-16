package com.example.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface FrappeApiService {

    @POST("api/method/login")
    suspend fun login(@Body body: FrappeLoginRequest): Response<FrappeMessageResponse>

    @GET("api/method/frappe.auth.get_logged_user")
    suspend fun getLoggedUser(): Response<FrappeMessageResponse>

    @GET("crm")
    suspend fun fetchCrmPage(): Response<ResponseBody>

    @GET("app")
    suspend fun fetchAppPage(): Response<ResponseBody>

    @GET("api/resource/{doctype}")
    suspend fun listResources(
        @Path("doctype") doctype: String,
        @Query("fields") fields: String = "[\"*\"]",
        @Query("limit_page_length") limit: Int = 100,
        @Query("order_by") orderBy: String = "modified desc",
        @Query("filters") filters: String? = null
    ): Response<FrappeListEnvelope>

    @POST("api/resource/{doctype}")
    suspend fun createResource(
        @Path("doctype") doctype: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any?>
    ): Response<FrappeDocEnvelope>

    @PUT("api/resource/{doctype}/{name}")
    suspend fun updateResource(
        @Path("doctype") doctype: String,
        @Path("name") name: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any?>
    ): Response<FrappeDocEnvelope>

    @Multipart
    @POST("api/method/upload_file")
    suspend fun uploadPrivateFile(
        @Part parts: List<MultipartBody.Part>
    ): Response<FrappeUploadEnvelope>
}
