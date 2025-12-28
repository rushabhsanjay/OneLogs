package com.oddworks.onelogs

import android.content.Intent
import android.view.View

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide

class GifTransitionActivity : AppCompatActivity() {

    // set this to your gif length in ms
    private val gifDurationMs = 8000L   // e.g. 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_gif_transition)

        val root = findViewById<View>(R.id.main_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val gifView = findViewById<ImageView>(R.id.gifView)

        Glide.with(this)
            .asGif()
            .load(R.drawable.about_transition)
            .into(gifView)

        // After gifDurationMs, open AboutActivity
        gifView.postDelayed({
            startActivity(Intent(this, AboutActivity::class.java))
            finish()
        }, gifDurationMs)
    }
}


