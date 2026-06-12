package com.example.poseexercise.views.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.poseexercise.databinding.ActivityRegisterBinding
import com.example.poseexercise.network.ApiClient
import com.example.poseexercise.network.AuthManager
import com.example.poseexercise.network.RegisterRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        binding.registerButton.setOnClickListener { register() }
        binding.loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun register() {
        val email = binding.emailInput.text.toString().trim()
        val username = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()
        val confirmPassword = binding.confirmPasswordInput.text.toString().trim()

        if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Remplissez tous les champs", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 4) {
            Toast.makeText(this, "Mot de passe trop court (min 4 caractères)", Toast.LENGTH_SHORT).show()
            return
        }

        binding.registerButton.isEnabled = false
        binding.registerButton.text = "Inscription..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.apiService.register(
                    RegisterRequest(email, username, password)
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@RegisterActivity, "Compte créé ! Connectez-vous", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        val msg = when (response.code()) {
                            400 -> "Email ou nom d'utilisateur déjà pris"
                            else -> "Erreur: ${response.message()}"
                        }
                        Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RegisterActivity, "Erreur réseau: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.registerButton.isEnabled = true
                    binding.registerButton.text = "Créer un compte"
                }
            }
        }
    }
}
