package com.example.accura

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody.Part
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val PICK_IMAGE_REQUEST = 1
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editTextUrl = findViewById<EditText>(R.id.editTextUrl)
        val editTextMessage = findViewById<EditText>(R.id.editTextMessage)
        val buttonSend = findViewById<Button>(R.id.buttonSend)
        val buttonChooseImage = findViewById<Button>(R.id.buttonChooseImage)
        val textViewImagePath = findViewById<TextView>(R.id.textViewImagePath)

        buttonChooseImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        buttonSend.setOnClickListener {
            val url = editTextUrl.text.toString()
            val message = editTextMessage.text.toString()

            if (url.isNotEmpty() && message.isNotEmpty() && imageUri != null) {
                sendTextAndImage(url, message, imageUri!!)
            } else {
                Toast.makeText(this, "Please enter text, URL, and select an image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            findViewById<TextView>(R.id.textViewImagePath).text = imageUri?.lastPathSegment
        }
    }

    private fun sendTextAndImage(url: String, message: String, imageUri: Uri) {
        val inputStream = contentResolver.openInputStream(imageUri)
        val file = File(cacheDir, getFileName(imageUri))
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()

        val imageRequestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
        val imagePart = Part.createFormData("image", file.name, imageRequestBody)
        val textPart = message.toRequestBody("text/plain".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("text", message)
                .addFormDataPart("image", file.name, imageRequestBody)
                .build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(applicationContext, "Upload successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "Server error: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun getFileName(uri: Uri): String {
        var name = "temp_image"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
