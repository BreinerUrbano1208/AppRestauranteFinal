package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.DialogProductFormBinding
import com.bremnercamilourbano.apprestaurante.databinding.FragmentAdminBinding
import com.bremnercamilourbano.apprestaurante.model.Product
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
class AdminFragment : Fragment(R.layout.fragment_admin) {

    private val viewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminBinding.bind(view)
        binding.header.applyStatusBarPadding()

        val adapter = AdminProductAdapter(
            onEdit = { product -> showProductForm(product) },
            onToggleAvailable = { product -> viewModel.toggleAvailability(product.id) }
        )
        binding.recyclerAdmin.adapter = adapter

        binding.btnLogin.setOnClickListener { tryLogin() }
        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryLogin()
                true
            } else {
                false
            }
        }
        binding.btnLogout.setOnClickListener { viewModel.logoutAdmin() }
        binding.btnAddProduct.setOnClickListener { showProductForm(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.adminLogged.collect { logged ->
                        binding.loginGroup.isVisible = !logged
                        binding.adminGroup.isVisible = logged
                        binding.btnLogout.isVisible = logged
                        if (!logged) binding.inputPassword.text?.clear()
                    }
                }
                launch { viewModel.products.collect { adapter.submitList(it) } }
                launch {
                    viewModel.loginError.collect { error ->
                        binding.layoutPassword.error = error?.let { getString(it.res) }
                    }
                }
                launch { viewModel.loginBusy.collect { binding.btnLogin.isEnabled = !it } }
            }
        }
    }

    private fun tryLogin() {
        viewModel.loginAdmin(binding.inputPassword.text?.toString().orEmpty())
    }

  private fun showProductForm(product: Product?) {
        val form = DialogProductFormBinding.inflate(layoutInflater)
        if (product != null) {
            form.inputName.setText(product.title)
            form.inputDescription.setText(product.description)
            form.inputCategory.setText(product.category)
            form.inputPrice.setText(formatPriceInput(product.price))
            form.inputImage.setText(product.imageUrl.orEmpty())
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (product == null) R.string.admin_new_title else R.string.admin_edit_title)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()


        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = form.inputName.text?.toString().orEmpty().trim()
                val price = form.inputPrice.text?.toString().orEmpty()
                    .trim().replace(',', '.').toDoubleOrNull()

                form.layoutName.error = if (name.isEmpty()) getString(R.string.error_name_required) else null
                form.layoutPrice.error =
                    if (price == null || price < 0) getString(R.string.error_price_invalid) else null

                if (name.isNotEmpty() && price != null && price >= 0) {
                    viewModel.saveProduct(
                        id = product?.id,
                        title = name,
                        description = form.inputDescription.text?.toString().orEmpty().trim(),
                        category = form.inputCategory.text?.toString().orEmpty().trim().ifBlank { "General" },
                        price = price,
                        imageUrl = form.inputImage.text?.toString().orEmpty().trim().ifBlank { null }
                    )
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun formatPriceInput(price: Double): String =
        if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()

    override fun onDestroyView() {
        binding.recyclerAdmin.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
