package com.example.accura

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody.Part
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.OpenableColumns

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val CAMERA_REQUEST_CODE = 1001
    private val IMAGE_REQUEST_CODE = 1002
    private val CAMERA_PERMISSION_REQUEST_CODE = 2001
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val editTextUrl = findViewById<EditText>(R.id.editTextUrl)
        val editTextMessage = findViewById<EditText>(R.id.editTextMessage)
        val buttonSend = findViewById<Button>(R.id.buttonSend)
        val buttonTakePicture = findViewById<Button>(R.id.buttonTakePicture)
        val buttonChooseImage = findViewById<Button>(R.id.buttonChooseImage)
        val textViewImagePath = findViewById<TextView>(R.id.textViewImagePath)

        // Choose image from storage
        buttonChooseImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, IMAGE_REQUEST_CODE)
        }

        // Check camera permission before enabling the camera button
        if (checkCameraPermission()) {
            buttonTakePicture.setOnClickListener {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
            }
        } else {
            requestCameraPermission()
        }

        buttonSend.setOnClickListener {
            val url = editTextUrl.text.toString()
            val message = editTextMessage.text.toString()

            if (url.isNotEmpty() && message.isNotEmpty() && imageUri != null) {
                sendTextAndImage(url, message, imageUri!!)
            } else {
                Toast.makeText(this, "Please enter text, URL, and select/take a picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Check if camera permission is granted
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    // Request camera permission
    private fun requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    // Handle the result of permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, enable camera functionality
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle the image result from the camera or file picker
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    val photo = data?.extras?.get("data") as Bitmap
                    imageUri = saveImageToFile(photo)
                }
                IMAGE_REQUEST_CODE -> {
                    imageUri = data?.data
                }
            }

            imageUri?.let {
                findViewById<TextView>(R.id.textViewImagePath).text = it.lastPathSegment
            }
        }
    }

    // Save the captured image to a file
    private fun saveImageToFile(photo: Bitmap): Uri {
        val file = File(cacheDir, "captured_image.jpg")
        val outStream: OutputStream = FileOutputStream(file)
        photo.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
        outStream.flush()
        outStream.close()

        return Uri.fromFile(file)
    }

    // Send text and image to Flask API
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

    // Get the file name from URI
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
