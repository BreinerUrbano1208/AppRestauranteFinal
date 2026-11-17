package com.bremnercamilourbano.apprestaurante.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.ClientStore
import com.bremnercamilourbano.apprestaurante.data.DeviceRequest
import com.bremnercamilourbano.apprestaurante.data.ProductsApi
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notificaciones push (Firebase). El teléfono guarda su "token" en el servidor al iniciar sesión,
 * y el servidor lo usa para avisar de pedidos nuevos o cambios de estado, incluso con la app cerrada.
 */
object Push {
    const val CHANNEL_STAFF = "pedidos_personal"
    const val CHANNEL_CLIENT = "estado_pedido"

    private const val PREFS = "restapp_settings"
    private const val KEY_ENABLED = "push_enabled"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bearer del último usuario que estuvo conectado, para poder quitar el token al cerrar sesión. */
    private var lastBearer: String? = null

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) register(context) else unregister()
    }

    /** Dos canales, cada uno con su propio sonido: uno para el personal y otro para el cliente. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun sound(res: Int) = Uri.parse("android.resource://${context.packageName}/$res")

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STAFF, context.getString(R.string.channel_staff), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_staff_desc)
                setSound(sound(R.raw.sonido_pedido), attrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CLIENT, context.getString(R.string.channel_client), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_client_desc)
                setSound(sound(R.raw.sonido_estado), attrs)
                enableVibration(true)
            }
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun currentBearer(): String? =
        SessionStore.session.value?.token ?: ClientStore.session.value?.token

    /** Envía el token de este teléfono al servidor, asociado a quien tiene la sesión abierta. */
    fun register(context: Context) {
        if (!isEnabled(context)) return
        val bearer = currentBearer() ?: return
        lastBearer = bearer
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = task.result
            if (!task.isSuccessful || token.isNullOrBlank()) return@addOnCompleteListener
            scope.launch {
                runCatching { ProductsApi.create().registerDevice("Bearer $bearer", DeviceRequest(token)) }
            }
        }
    }

    /** Quita este teléfono del servidor (al cerrar sesión o apagar las notificaciones). */
    fun unregister(bearerOverride: String? = null) {
        val bearer = bearerOverride ?: currentBearer() ?: lastBearer ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = task.result
            if (!task.isSuccessful || token.isNullOrBlank()) return@addOnCompleteListener
            scope.launch {
                runCatching { ProductsApi.create().unregisterDevice("Bearer $bearer", DeviceRequest(token)) }
            }
        }
    }

    /** Se llama cuando la sesión cambia: registra al entrar, y limpia al salir. */
    fun onSessionChanged(context: Context) {
        val bearer = currentBearer()
        if (bearer != null) {
            register(context)
        } else {
            val old = lastBearer
            lastBearer = null
            if (old != null) unregister(old)
        }
    }
}
