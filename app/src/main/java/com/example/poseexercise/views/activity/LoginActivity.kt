package com.example.poseexercise.views.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.poseexercise.databinding.ActivityLoginBinding
import com.example.poseexercise.network.ApiClient
import com.example.poseexercise.network.AuthManager
import com.example.poseexercise.network.LoginRequest
import com.example.poseexercise.network.RegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        binding.loginButton.setOnClickListener { login() }
        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun login() {
        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Remplissez tous les champs", Toast.LENGTH_SHORT).show()
            return
        }

        binding.loginButton.isEnabled = false
        binding.loginButton.text = "Connexion..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.login(LoginRequest(username, password))
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null) {
                            authManager.token = body.accessToken
                            authManager.username = username
                            ApiClient.setToken(body.accessToken)
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finishAffinity()
                        }
                    } else {
                        val msg = when (response.code()) {
                            401 -> "Identifiants incorrects"
                            else -> "Erreur: ${response.message()}"
                        }
                        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Erreur réseau: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.loginButton.isEnabled = true
                    binding.loginButton.text = "Se connecter"
                }
            }
        }
    }
}
