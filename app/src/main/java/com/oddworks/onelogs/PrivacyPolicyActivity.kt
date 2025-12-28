package com.oddworks.onelogs

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val textView = findViewById<TextView>(R.id.privacyText)
        textView.text = getString(R.string.privacy_policy_onelogs)
        textView.movementMethod = LinkMovementMethod.getInstance() // make links clickable [web:98]
    }
}
