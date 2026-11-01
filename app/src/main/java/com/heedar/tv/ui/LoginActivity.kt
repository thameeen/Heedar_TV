package com.heedar.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.heedar.tv.MainActivity
import com.heedar.tv.R
import com.heedar.tv.data.AppDatabase
import com.heedar.tv.data.PlaylistEntity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit.etServerUrl: EditText
    private lateinit.etUsername: EditText
    private lateinit.etPassword: EditText
    private lateinit.btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val url = etServerUrl.text.toString().trim()
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (url.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
                val playlist = PlaylistEntity(
                    name = "Heedar Server",
                    url = url,
                    username = user,
                    password = pass,
                    type = "xtream"
                )

                // حفظ الحساب في قاعدة البيانات المحلية
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.playlistDao().insertPlaylist(playlist)

                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "تم تسجيل الدخول بنجاح", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            } else {
                Toast.makeText(this, "الرجاء تعبئة جميع الحقول بشكل صحيح", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
