package com.makay.cleaner.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricManager(private val context: Context) {

    private val biometricManager: BiometricManager = BiometricManager.from(context)

    /**
     * Biyometrik kimlik doğrulamanın kullanılabilir olup olmadığını kontrol eder
     */
    fun isBiometricAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Biyometrik kimlik doğrulama başlatır
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Biyometrik Doğrulama",
        subtitle: String = "Uygulamaya erişmek için kimliğinizi doğrulayın",
        negativeButtonText: String = "İptal",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Doğrulama başarısız")
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Biyometrik türünü döndürür
     */
    fun getBiometricType(): String {
        return when {
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS -> {
                "Biyometrik (Parmak İzi/Yüz)"
            }
            else -> "Kullanılamıyor"
        }
    }
}