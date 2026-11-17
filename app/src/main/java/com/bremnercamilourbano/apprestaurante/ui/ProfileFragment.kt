package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.FragmentProfileBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.normalizeColombianPhone
import com.bremnercamilourbano.apprestaurante.util.popIn
import com.bremnercamilourbano.apprestaurante.util.staggerEnter
import com.bremnercamilourbano.apprestaurante.viewmodel.AccountViewModel
import kotlinx.coroutines.launch

/** Pantalla de entrada: iniciar sesión o crear cuenta. (Al entrar, el correo decide a qué pantalla se va.) */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: AccountViewModel by activityViewModels()
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var registerMode = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)
        binding.header.applyStatusBarPadding()

        binding.btnSwitchMode.setOnClickListener {
            registerMode = !registerMode
            renderMode()
        }
        binding.inputPassword.doAfterTextChanged { binding.layoutPassword.error = null }
        binding.btnPolicy.setOnClickListener { DrawerDialogs.privacyPolicy(requireActivity() as androidx.appcompat.app.AppCompatActivity) }
        binding.btnForgot.setOnClickListener {
            AuthDialogs.recoverPassword(requireActivity() as androidx.appcompat.app.AppCompatActivity, viewModel,
                binding.inputEmail.text?.toString().orEmpty().trim())
        }
        binding.btnSubmit.setOnClickListener { submit() }
        renderMode()

        // Entrada animada: el logo aparece con rebote y el formulario sube por partes.
        binding.imgHero.popIn(delay = 80)
        staggerEnter(binding.txtBrand, binding.txtSubtitle, binding.formCard, start = 220L, step = 110L)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.busy.collect { binding.btnSubmit.isEnabled = !it } }
                launch {
                    viewModel.verifyRequests.collect {
                        AuthDialogs.verifyEmail(requireActivity() as androidx.appcompat.app.AppCompatActivity, it, viewModel)
                    }
                }
                launch {
                    viewModel.loginFailed.collect {
                        binding.layoutPassword.error = getString(R.string.login_error_credentials)
                        binding.inputPassword.text?.clear()
                    }
                }
            }
        }
    }

    private fun renderMode() {
        binding.layoutName.isVisible = registerMode
        binding.layoutPhone.isVisible = registerMode
        binding.layoutAccept.isVisible = registerMode
        binding.btnPolicy.isVisible = registerMode
        binding.btnForgot.isVisible = !registerMode
        binding.txtAuthTitle.setText(if (registerMode) R.string.profile_register_title else R.string.profile_login_title)
        binding.btnSubmit.setText(if (registerMode) R.string.profile_register_button else R.string.profile_login_button)
        binding.btnSwitchMode.setText(if (registerMode) R.string.profile_to_login else R.string.profile_to_register)
        listOf(binding.layoutName, binding.layoutEmail, binding.layoutPhone, binding.layoutPassword)
            .forEach { it.error = null }
    }

    private fun submit() {
        val name = binding.inputName.text?.toString().orEmpty().trim()
        val email = binding.inputEmail.text?.toString().orEmpty().trim()
        val phoneText = binding.inputPhone.text?.toString().orEmpty()
        val phone = normalizeColombianPhone(phoneText)
        val password = binding.inputPassword.text?.toString().orEmpty()

        val emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches()
        binding.layoutEmail.error = if (emailOk) null else getString(R.string.profile_error_email)

        if (!registerMode) {
            binding.layoutPassword.error = if (password.isNotEmpty()) null else getString(R.string.profile_error_password)
            if (emailOk && password.isNotEmpty()) viewModel.login(email, password)
            return
        }

        val nameOk = name.length >= 2
        val passwordOk = password.length >= 8
        binding.layoutName.error = if (nameOk) null else getString(R.string.profile_error_name)
        binding.layoutPhone.error = if (phone != null) null else getString(R.string.order_error_phone)
        binding.layoutPassword.error = if (passwordOk) null else getString(R.string.profile_error_password)
        if (!binding.checkAccept.isChecked) {
            com.bremnercamilourbano.apprestaurante.util.Toasts.error(binding.root, R.string.register_accept_required)
            return
        }
        if (nameOk && emailOk && phone != null && passwordOk) viewModel.register(name, email, phone, password, true)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
