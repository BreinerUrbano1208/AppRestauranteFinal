package com.bremnercamilourbano.apprestaurante.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bremnercamilourbano.apprestaurante.BuildConfig
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.ClientStore
import com.bremnercamilourbano.apprestaurante.databinding.DialogSettingsBinding
import com.bremnercamilourbano.apprestaurante.push.Push
import com.bremnercamilourbano.apprestaurante.util.ThemeStore
import com.bremnercamilourbano.apprestaurante.util.normalizeColombianPhone
import com.bremnercamilourbano.apprestaurante.viewmodel.AccountViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Ventanas del menú lateral: editar perfil, información del restaurante, configuración y cerrar sesión. */
object DrawerDialogs {

    fun privacyPolicy(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.policy_title)
            .setMessage(R.string.policy_body)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    fun restaurantInfo(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.restaurant_info_title)
            .setMessage(R.string.restaurant_info_body)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    fun settings(activity: AppCompatActivity) {
        val binding = DialogSettingsBinding.inflate(LayoutInflater.from(activity))
        binding.switchNotifications.isChecked = Push.isEnabled(activity)
        binding.switchNotifications.setOnCheckedChangeListener { _, checked -> Push.setEnabled(activity, checked) }
        val currentMode = ThemeStore.mode(activity)
        binding.radioTheme.check(
            when (currentMode) {
                ThemeStore.LIGHT -> R.id.radioThemeLight
                ThemeStore.DARK -> R.id.radioThemeDark
                else -> R.id.radioThemeAuto
            }
        )
        binding.radioTheme.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.radioThemeLight -> ThemeStore.LIGHT
                R.id.radioThemeDark -> ThemeStore.DARK
                else -> ThemeStore.AUTO
            }
            if (mode != currentMode) ThemeStore.set(activity, mode)
        }
        binding.txtVersion.text = activity.getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        binding.btnSystemSettings.setOnClickListener {
            // Desde aquí la persona puede cambiar el sonido o el volumen de cada tipo de aviso.
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${activity.packageName}"))
            }
            activity.startActivity(intent)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.settings_title)
            .setView(binding.root)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    fun confirmLogout(activity: AppCompatActivity, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.action_cancel_dialog, null)
            .setPositiveButton(R.string.action_logout) { _, _ -> onConfirm() }
            .show()
    }
}
