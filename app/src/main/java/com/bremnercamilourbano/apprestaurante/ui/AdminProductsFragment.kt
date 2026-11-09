package com.bremnercamilourbano.apprestaurante.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isInvisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.DialogProductFormBinding
import com.bremnercamilourbano.apprestaurante.databinding.FragmentAdminProductsBinding
import com.bremnercamilourbano.apprestaurante.model.Product
import com.bremnercamilourbano.apprestaurante.util.ImageCompressor
import com.bremnercamilourbano.apprestaurante.util.loadImage
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gestión del menú (solo administrador): buscar, crear, editar, disponibilidad y foto. */
class AdminProductsFragment : Fragment(R.layout.fragment_admin_products) {

    private val viewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentAdminProductsBinding? = null
    private val binding get() = _binding!!

    // Formulario abierto y foto elegida (todavía sin subir).
    private var openForm: DialogProductFormBinding? = null
    private var pickedUri: Uri? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pickedUri = uri
                openForm?.let { form ->
                    Glide.with(this).load(uri).centerCrop().into(form.imgPreview)
                    form.btnPickImage.setText(R.string.admin_change_image)
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminProductsBinding.bind(view)

        val adapter = AdminProductAdapter(
            onEdit = { product -> showProductForm(product) },
            onToggleAvailable = { product -> viewModel.toggleAvailability(product.id) }
        )
        binding.recyclerAdmin.adapter = adapter
        binding.btnAddProduct.setOnClickListener { showProductForm(null) }

        binding.inputSearch.setText(viewModel.adminQuery.value)
        binding.inputSearch.doAfterTextChanged { viewModel.setAdminQuery(it?.toString().orEmpty()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.adminProducts.collect { adapter.submitList(it) } }
                launch { viewModel.saveBusy.collect { binding.progressSave.isInvisible = !it } }
            }
        }
    }

    private fun showProductForm(product: Product?) {
        val form = DialogProductFormBinding.inflate(layoutInflater)
        openForm = form
        pickedUri = null
        if (product != null) {
            form.inputName.setText(product.title)
            form.inputDescription.setText(product.description)
            form.inputCategory.setText(product.category)
            form.inputPrice.setText(formatPriceInput(product.price))
            if (!product.imageUrl.isNullOrBlank()) {
                form.imgPreview.loadImage(product.imageUrl)
                form.btnPickImage.setText(R.string.admin_change_image)
            }
        }
        // Photo Picker: no pide permisos de galería.
        form.btnPickImage.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (product == null) R.string.admin_new_title else R.string.admin_edit_title)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnDismissListener {
            if (openForm === form) openForm = null
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = form.inputName.text?.toString().orEmpty().trim()
                val price = form.inputPrice.text?.toString().orEmpty()
                    .trim().replace(',', '.').toDoubleOrNull()

                form.layoutName.error = if (name.isEmpty()) getString(R.string.error_name_required) else null
                form.layoutPrice.error =
                    if (price == null || price < 0) getString(R.string.error_price_invalid) else null

                if (name.isNotEmpty() && price != null && price >= 0) {
                    save(
                        product = product,
                        name = name,
                        description = form.inputDescription.text?.toString().orEmpty().trim(),
                        category = form.inputCategory.text?.toString().orEmpty().trim().ifBlank { "General" },
                        price = price,
                        uri = pickedUri
                    )
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /** Reduce la foto (si se eligió una) y deja que el ViewModel la suba y guarde el producto. */
    private fun save(
        product: Product?,
        name: String,
        description: String,
        category: String,
        price: Double,
        uri: Uri?
    ) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            var bytes: ByteArray? = null
            if (uri != null) {
                bytes = withContext(Dispatchers.IO) { ImageCompressor.compress(appContext, uri) }
                if (bytes == null) {
                    viewModel.showMessage(R.string.msg_image_error)
                    return@launch
                }
            }
            viewModel.saveProduct(
                id = product?.id,
                title = name,
                description = description,
                category = category,
                price = price,
                imageUrl = product?.imageUrl,
                imageBytes = bytes
            )
        }
    }

    private fun formatPriceInput(price: Double): String =
        if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()

    override fun onDestroyView() {
        binding.recyclerAdmin.adapter = null
        openForm = null
        _binding = null
        super.onDestroyView()
    }
}
