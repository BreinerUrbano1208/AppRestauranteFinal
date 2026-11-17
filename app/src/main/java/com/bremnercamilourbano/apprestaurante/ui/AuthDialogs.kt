package com.bremnercamilourbano.apprestaurante.ui

import android.util.Patterns
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.DialogCodeBinding
import com.bremnercamilourbano.apprestaurante.viewmodel.AccountViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Diálogos de cuenta con código por correo y confirmación con contraseña. */
object AuthDialogs {

    /** Verificar el correo: escribe el código de 6 dígitos que llegó. */
    fun verifyEmail(activity: AppCompatActivity, email: String, vm: AccountViewModel) {
        val form = DialogCodeBinding.inflate(activity.layoutInflater)
        form.txtInfo.text = activity.getString(R.string.verify_info, email)
        form.layoutCode.isVisible = true
        form.btnResend.isVisible = true
        form.btnResend.setOnClickListener { vm.resend(email) }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.verify_title)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.verify_button, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = form.inputCode.text?.toString().orEmpty().trim()
                form.layoutCode.error = if (code.length == 6) null else activity.getString(R.string.code_error)
                if (code.length == 6) vm.verify(email, code) { dialog.dismiss() }
            }
        }
        dialog.show()
    }

    /** Olvidé mi contraseña: primero el correo, luego el código y la contraseña nueva. */
    fun recoverPassword(activity: AppCompatActivity, vm: AccountViewModel, presetEmail: String = "") {
        val form = DialogCodeBinding.inflate(activity.layoutInflater)
        form.txtInfo.setText(R.string.recover_info_email)
        form.layoutEmail.isVisible = true
        form.inputEmail.setText(presetEmail)
        var step = 1
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.recover_title)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.recover_send, null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val email = form.inputEmail.text?.toString().orEmpty().trim()
                if (step == 1) {
                    val ok = Patterns.EMAIL_ADDRESS.matcher(email).matches()
                    form.layoutEmail.error = if (ok) null else activity.getString(R.string.profile_error_email)
                    if (ok) vm.forgot(email) {
                        step = 2
                        form.txtInfo.text = activity.getString(R.string.recover_info_code, email)
                        form.layoutEmail.isVisible = false
                        form.layoutCode.isVisible = true
                        form.layoutNewPassword.isVisible = true
                        positive.setText(R.string.recover_change)
                    }
                } else {
                    val code = form.inputCode.text?.toString().orEmpty().trim()
                    val password = form.inputNewPassword.text?.toString().orEmpty()
                    form.layoutCode.error = if (code.length == 6) null else activity.getString(R.string.code_error)
                    form.layoutNewPassword.error = if (password.length >= 8) null else activity.getString(R.string.profile_error_password)
                    if (code.length == 6 && password.length >= 8) vm.reset(email, code, password) { dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    /** Eliminar mi cuenta (Ley 1581): pide la contraseña para confirmar. */
    fun deleteAccount(activity: AppCompatActivity, vm: AccountViewModel, onDeleted: () -> Unit) {
        val form = DialogCodeBinding.inflate(activity.layoutInflater)
        form.txtInfo.setText(R.string.delete_account_info)
        form.layoutNewPassword.isVisible = true
        form.layoutNewPassword.hint = activity.getString(R.string.delete_account_password)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_account_title)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_account_button, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = form.inputNewPassword.text?.toString().orEmpty()
                form.layoutNewPassword.error = if (password.isNotEmpty()) null else activity.getString(R.string.profile_error_password)
                if (password.isNotEmpty()) vm.deleteAccount(password) { dialog.dismiss(); onDeleted() }
            }
        }
        dialog.show()
    }

    /** Eliminar cuenta de personal: hay que escribir ELIMINAR para confirmar. */
    fun confirmDeleteStaff(activity: AppCompatActivity, name: String, onConfirm: () -> Unit) {
        val form = DialogCodeBinding.inflate(activity.layoutInflater)
        form.txtInfo.text = activity.getString(R.string.staff_delete_info, name)
        form.layoutCode.isVisible = true
        form.layoutCode.hint = activity.getString(R.string.staff_delete_hint)
        form.inputCode.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        form.inputCode.filters = emptyArray()
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.staff_delete_title)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.staff_delete_button, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ok = form.inputCode.text?.toString().orEmpty().trim().equals("ELIMINAR", ignoreCase = true)
                form.layoutCode.error = if (ok) null else activity.getString(R.string.staff_delete_hint)
                if (ok) { dialog.dismiss(); onConfirm() }
            }
        }
        dialog.show()
    }
}
