package com.shadowhat.app.dispatcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.net.URLEncoder

object WhatsAppDispatcher {

    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val PACKAGE_WHATSAPP_STANDARD = "com.whatsapp"

    enum class AppAvailability {
        WHATSAPP_BUSINESS,
        WHATSAPP_STANDARD,
        NONE
    }

    /**
     * Checks which WhatsApp variant is installed on the user's Android phone.
     */
    fun checkInstalledWhatsApp(context: Context): AppAvailability {
        val pm = context.packageManager
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP_BUSINESS)) {
            return AppAvailability.WHATSAPP_BUSINESS
        }
        if (isPackageInstalled(pm, PACKAGE_WHATSAPP_STANDARD)) {
            return AppAvailability.WHATSAPP_STANDARD
        }
        return AppAvailability.NONE
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Opens WhatsApp Business app directly if installed.
     */
    fun openWhatsAppBusinessApp(context: Context): Boolean {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(PACKAGE_WHATSAPP_BUSINESS)
            ?: pm.getLaunchIntentForPackage(PACKAGE_WHATSAPP_STANDARD)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else {
            Toast.makeText(context, "WhatsApp Business no está instalado en este dispositivo", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Dispatches text to WhatsApp Business with assisted clipboard backup.
     * Never claims the message was sent automatically—only prepares it and opens WhatsApp
     * so the user can select the group, press Send, and confirm in SHADOWHAT.
     */
    fun dispatchMessage(
        context: Context,
        message: String,
        targetGroupHint: String,
        preferBusiness: Boolean = true
    ): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("ShadowHat Message ($targetGroupHint)", message)
        clipboard?.setPrimaryClip(clip)

        val availability = checkInstalledWhatsApp(context)
        val targetPackage = if (preferBusiness && availability == AppAvailability.WHATSAPP_BUSINESS) {
            PACKAGE_WHATSAPP_BUSINESS
        } else if (availability != AppAvailability.NONE) {
            PACKAGE_WHATSAPP_STANDARD
        } else {
            null
        }

        return try {
            if (targetPackage != null) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    `package` = targetPackage
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                Toast.makeText(context, "Grupo destino: $targetGroupHint (copiado al portapapeles)", Toast.LENGTH_LONG).show()
                true
            } else {
                val encoded = URLEncoder.encode(message, "UTF-8")
                val uri = Uri.parse("https://api.whatsapp.com/send?text=$encoded")
                val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(viewIntent)
                true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error abriendo WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
