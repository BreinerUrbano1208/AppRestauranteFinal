package com.bremnercamilourbano.apprestaurante.push

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bremnercamilourbano.apprestaurante.MainActivity
import com.bremnercamilourbano.apprestaurante.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RestAppMessagingService : FirebaseMessagingService() {

    /** Firebase cambió el token de este teléfono: se vuelve a registrar. */
    override fun onNewToken(token: String) {
        Push.register(applicationContext)
    }

    /**
     * Con la app cerrada o en segundo plano, Android muestra la notificación solo (con el sonido del canal).
     * Este método se ejecuta cuando la app está abierta: ahí la mostramos nosotros para que también suene.
     */
    @SuppressLint("MissingPermission")
    override fun onMessageReceived(message: RemoteMessage) {
        Push.createChannels(applicationContext)
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val channel = message.notification?.channelId
            ?: if (message.data["tipo"] == "estado") Push.CHANNEL_CLIENT else Push.CHANNEL_STAFF

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        if (Push.hasPermission(this)) {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
