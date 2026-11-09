package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.MainActivity
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderType
import com.bremnercamilourbano.apprestaurante.databinding.DialogOrderBinding
import com.bremnercamilourbano.apprestaurante.databinding.FragmentCartBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.viewmodel.OrdersViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CartFragment : Fragment(R.layout.fragment_cart) {

    private val viewModel: RestaurantViewModel by activityViewModels()
    private val ordersViewModel: OrdersViewModel by activityViewModels()
    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCartBinding.bind(view)
        binding.header.applyStatusBarPadding()

        val adapter = CartAdapter(
            onChangeQuantity = { id, delta -> viewModel.changeQuantity(id, delta) },
            onRemove = { id -> viewModel.removeFromCart(id) }
        )
        binding.recyclerCart.adapter = adapter
        binding.btnCheckout.setOnClickListener { showOrderForm() }
        binding.btnClear.setOnClickListener { viewModel.clearCart() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.orderPlaced.collect { id ->
                        ordersViewModel.trackOrder(id)
                        (activity as? MainActivity)?.goToMyOrder()
                    }
                }
                launch { viewModel.orderBusy.collect { binding.btnCheckout.isEnabled = !it } }
                launch {
                    combine(viewModel.cartItems, viewModel.cartTotal) { items, total -> items to total }
                        .collect { (items, total) ->
                            adapter.submitList(items)
                            val empty = items.isEmpty()
                            binding.txtEmpty.isVisible = empty
                            binding.recyclerCart.isVisible = !empty
                            binding.panelCheckout.isVisible = !empty
                            binding.txtTotal.text = getString(R.string.cart_total, total.toPrice())
                        }
                }
            }
        }
    }

    private fun showOrderForm() {
        val items = viewModel.cartItems.value
        if (items.isEmpty()) return
        val lines = items.joinToString("\n") {
            "${it.quantity} × ${it.product.title} — ${it.subtotal.toPrice()}"
        }
        val form = DialogOrderBinding.inflate(layoutInflater)
        form.txtSummary.text = getString(R.string.order_summary, lines, viewModel.cartTotal.value.toPrice())

        fun updateTableVisibility() {
            form.layoutTable.isVisible = form.radioMesa.isChecked
        }
        form.radioType.setOnCheckedChangeListener { _, _ -> updateTableVisibility() }
        updateTableVisibility()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.order_title)
            .setView(form.root)
            .setNegativeButton(R.string.order_back, null)
            .setPositiveButton(R.string.order_finish, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = form.inputName.text?.toString().orEmpty().trim()
                val phone = form.inputPhone.text?.toString().orEmpty().filter { it.isDigit() }
                val isTable = form.radioMesa.isChecked
                val table = form.inputTable.text?.toString().orEmpty().trim().toIntOrNull()

                val nameOk = name.length >= 2
                val phoneOk = phone.length in 7..15
                val tableOk = !isTable || (table != null && table in 1..200)

                form.layoutName.error = if (nameOk) null else getString(R.string.order_error_name)
                form.layoutPhone.error = if (phoneOk) null else getString(R.string.order_error_phone)
                form.layoutTable.error = if (tableOk) null else getString(R.string.order_error_table)

                if (nameOk && phoneOk && tableOk) {
                    viewModel.submitOrder(
                        customerName = name,
                        phone = phone,
                        type = if (isTable) OrderType.MESA else OrderType.RECOGER,
                        tableNumber = if (isTable) table else null,
                        note = form.inputNote.text?.toString().orEmpty()
                    )
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        binding.recyclerCart.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
