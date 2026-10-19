package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.MainActivity
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.FragmentCartBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CartFragment : Fragment(R.layout.fragment_cart) {

    private val viewModel: RestaurantViewModel by activityViewModels()
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
        binding.btnCheckout.setOnClickListener { showOrderSummary() }
        binding.btnClear.setOnClickListener { viewModel.clearCart() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.orderPlaced.collect { (activity as? MainActivity)?.goToMenu() } }
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

    private fun showOrderSummary() {
        val items = viewModel.cartItems.value
        if (items.isEmpty()) return
        val lines = items.joinToString("\n") {
            "${it.quantity} × ${it.product.title} — ${it.subtotal.toPrice()}"
        }
        val input = EditText(requireContext()).apply {
            setHint(R.string.order_name_hint)
            setSingleLine()
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.order_title)
            .setMessage(getString(R.string.order_message, lines, viewModel.cartTotal.value.toPrice()))
            .setView(container)
            .setNegativeButton(R.string.order_back, null)
            .setPositiveButton(R.string.order_finish, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString().orEmpty().trim()
                if (name.isEmpty()) {
                    input.error = getString(R.string.order_name_required)
                } else {
                    viewModel.submitOrder(name, "")
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
