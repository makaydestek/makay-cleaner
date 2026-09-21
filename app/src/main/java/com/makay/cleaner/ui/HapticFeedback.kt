package com.makay.cleaner.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

@Suppress("DEPRECATION")
object HapticFeedback {

    /**
     * Hafif titreşim - Buton tıklaması
     */
    fun lightClick(context: Context) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                }
                else -> {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Titreşim yoksa sessizce geç
        }
    }

    /**
     * Orta titreşim - Başarılı işlem
     */
    fun success(context: Context) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
                else -> {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(longArrayOf(0, 50, 50, 50), 0)
                }
            }
        } catch (e: Exception) {
            // Titreşim yoksa sessizce geç
        }
    }

    /**
     * Ağır titreşim - Hata veya uyarı
     */
    fun warning(context: Context) {
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                }
                else -> {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vibrator.vibrate(longArrayOf(0, 100, 50, 100), 0)
                }
            }
        } catch (e: Exception) {
            // Titreşim yoksa sessizce geç
        }
    }

    /**
     * View üzerinden haptic feedback
     */
    fun performHapticFeedback(view: View, feedbackConstant: Int = HapticFeedbackConstants.VIRTUAL_KEY) {
        try {
            view.performHapticFeedback(feedbackConstant)
        } catch (e: Exception) {
            // Haptic feedback yoksa sessizce geç
        }
    }
}