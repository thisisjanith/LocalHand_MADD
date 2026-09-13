package com.example.localhand_new.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

fun String.toRequestBody(): RequestBody = this.toRequestBody("text/plain".toMediaType())

// A wildcard image type isn't a real MIME type — the backend's Multer
// fileFilter checks for an exact "image/jpeg" or "image/png" match and
// rejects anything else, including a wildcard. copyPhotoToAppStorage always
// saves the picked photo as a .jpg regardless of its original format, so
// image/jpeg is always the correct type to declare here.
fun File.toFormDataPart(partName: String, fileName: String): MultipartBody.Part {
    val body = this.asRequestBody("image/jpeg".toMediaType())
    return MultipartBody.Part.createFormData(partName, fileName, body)
}
