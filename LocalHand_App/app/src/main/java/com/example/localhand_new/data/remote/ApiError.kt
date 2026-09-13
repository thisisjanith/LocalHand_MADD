package com.example.localhand_new.data.remote

import com.example.localhand_new.data.remote.dto.ErrorResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.HttpException
import java.io.IOException

private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
private val errorAdapter = moshi.adapter(ErrorResponse::class.java)

/** Turns any repository-layer failure into a plain-language message safe to show as-is. */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val backendMessage = try {
            response()?.errorBody()?.string()?.let { errorAdapter.fromJson(it)?.error }
        } catch (_: Exception) {
            null
        }
        backendMessage ?: when (code()) {
            401 -> "You need to log in again."
            403 -> "You don't have permission to do that."
            404 -> "That couldn't be found."
            in 500..599 -> "Something went wrong on our end. Please try again."
            else -> "Something went wrong. Please try again."
        }
    }
    is IOException -> "Can't reach the server. Check your connection and try again."
    else -> "Something went wrong. Please try again."
}
