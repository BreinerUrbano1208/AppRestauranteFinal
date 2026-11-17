package com.bremnercamilourbano.apprestaurante.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.ClientStore
import com.bremnercamilourbano.apprestaurante.data.ProfileUpdateRequest
import com.bremnercamilourbano.apprestaurante.databinding.FragmentEditProfileBinding
import com.bremnercamilourbano.apprestaurante.util.ImageCompressor
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.loadAvatar
import com.bremnercamilourbano.apprestaurante.util.normalizeColombianPhone
import com.bremnercamilourbano.apprestaurante.viewmodel.AccountViewModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.bremnercamilourbano.apprestaurante.util.Toasts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Editar perfil: foto y datos personales del cliente (Ley 1581 de 2012). */
class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {

    private val viewModel: AccountViewModel by activityViewModels()
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    /** Foto actual del formulario: la de la cuenta, una nueva, o null si se quitó. */
    private var photo: String? = null
    private var birthDate: String? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { ImageCompressor.avatar(requireContext(), uri) }
            if (result == null) {
                Toasts.error(binding.root, R.string.edit_profile_photo_error)
            } else {
                photo = result
                renderPhoto()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEditProfileBinding.bind(view)
        binding.header.applyStatusBarPadding()
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val client = ClientStore.session.value
        if (client == null) {
            parentFragmentManager.popBackStack()
            return
        }

        photo = client.photo
        birthDate = client.birthDate
        binding.inputName.setText(client.name)
        binding.inputLastName.setText(client.lastName.orEmpty())
        binding.inputDocNumber.setText(client.docNumber.orEmpty())
        binding.inputPhone.setText(client.phone)
        binding.inputEmail.setText(client.email)
        binding.inputAddress.setText(client.address.orEmpty())
        binding.inputNeighborhood.setText(client.neighborhood.orEmpty())
        binding.inputCity.setText(client.city.orEmpty())
        binding.checkAccept.isChecked = client.acceptedData
        binding.checkAccept.isEnabled = !client.acceptedData // la autorización ya dada no se quita desde aquí

        binding.inputDocType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, DOC_TYPES.map { it.second })
        )
        DOC_TYPES.firstOrNull { it.first == client.docType }?.let { binding.inputDocType.setText(it.second, false) }

        binding.inputBirthDate.setText(birthDate.orEmpty())
        binding.inputBirthDate.setOnClickListener { pickBirthDate() }
        binding.layoutBirthDate.setEndIconOnClickListener { pickBirthDate() }

        binding.btnChangePhoto.setOnClickListener { pickPhoto.launch("image/*") }
        binding.btnRemovePhoto.setOnClickListener {
            photo = null
            renderPhoto()
        }
        binding.btnPolicy.setOnClickListener { DrawerDialogs.privacyPolicy(requireActivity() as androidx.appcompat.app.AppCompatActivity) }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDeleteAccount.setOnClickListener {
            AuthDialogs.deleteAccount(requireActivity() as androidx.appcompat.app.AppCompatActivity, viewModel) {
                parentFragmentManager.popBackStack()
            }
        }
        binding.inputName.doAfterTextChanged { renderPhoto() }
        renderPhoto()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.busy.collect { binding.btnSave.isEnabled = !it } }
            }
        }
    }

    private fun renderPhoto() {
        val hasPhoto = photo != null
        binding.imgPhoto.isVisible = hasPhoto
        binding.btnRemovePhoto.isVisible = hasPhoto
        binding.imgPhoto.loadAvatar(photo)
        binding.txtPhotoInitial.text =
            binding.inputName.text?.toString().orEmpty().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    }

    private fun pickBirthDate() {
        val constraints = CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.profile_field_birth)
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            birthDate = format.format(Date(millis))
            binding.inputBirthDate.setText(birthDate)
        }
        picker.show(parentFragmentManager, "birth")
    }

    private fun save() {
        val name = binding.inputName.text?.toString().orEmpty().trim()
        val phone = normalizeColombianPhone(binding.inputPhone.text?.toString().orEmpty())
        val docLabel = binding.inputDocType.text?.toString().orEmpty()
        val docType = DOC_TYPES.firstOrNull { it.second == docLabel }?.first
        val docNumber = binding.inputDocNumber.text?.toString().orEmpty().trim()

        binding.layoutName.error = if (name.length >= 2) null else getString(R.string.profile_error_name)
        binding.layoutPhone.error = if (phone != null) null else getString(R.string.order_error_phone)
        val docOk = (docType == null && docNumber.isEmpty()) || (docType != null && docNumber.length in 5..15 && docNumber.all { it.isLetterOrDigit() })
        binding.layoutDocNumber.error = if (docOk) null else getString(R.string.profile_error_doc)
        if (name.length < 2 || phone == null || !docOk) return

        if (!binding.checkAccept.isChecked) {
            Toasts.error(binding.root, R.string.accept_data_required)
            return
        }

        fun text(input: com.google.android.material.textfield.TextInputEditText) =
            input.text?.toString().orEmpty().trim().ifEmpty { null }

        viewModel.updateProfile(
            ProfileUpdateRequest(
                name = name,
                lastName = text(binding.inputLastName),
                phone = phone,
                docType = docType,
                docNumber = docNumber.ifEmpty { null },
                birthDate = birthDate,
                address = text(binding.inputAddress),
                neighborhood = text(binding.inputNeighborhood),
                city = text(binding.inputCity),
                photo = photo,
                acceptData = true
            )
        ) { parentFragmentManager.popBackStack() }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val DOC_TYPES = listOf(
            "CC" to "Cédula de ciudadanía",
            "CE" to "Cédula de extranjería",
            "TI" to "Tarjeta de identidad",
            "PAS" to "Pasaporte",
            "PPT" to "Permiso por protección temporal",
            "NIT" to "NIT"
        )
    }
}
