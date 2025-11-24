package com.oddworks.onelogs

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity



class FullscreenImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_image)
        val imageView = findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.fullscreenImageView)
        imageView.maximumScale = 8f

        val imagePath = intent.getStringExtra("image_path")
        if (imagePath != null) {
            val decodedBitmap = android.graphics.BitmapFactory.decodeFile(imagePath)
            if (decodedBitmap != null) {
                imageView.setImageBitmap(decodedBitmap)
            } else {
                imageView.setImageResource(android.R.color.black)
            }
        }
    }

}

