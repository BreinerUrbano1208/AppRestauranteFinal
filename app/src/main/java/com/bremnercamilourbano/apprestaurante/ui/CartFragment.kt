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
import com.bremnercamilourbano.apprestaurante.data.ClientStore
import com.bremnercamilourbano.apprestaurante.data.OrderType
import com.bremnercamilourbano.apprestaurante.data.PaymentMethod
import com.bremnercamilourbano.apprestaurante.databinding.DialogOrderBinding
import com.bremnercamilourbano.apprestaurante.databinding.FragmentCartBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.normalizeColombianPhone
import com.bremnercamilourbano.apprestaurante.util.paymentLabel
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.viewmodel.OrdersViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.chip.Chip
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
        binding.btnDrawer.setOnClickListener { (activity as? com.bremnercamilourbano.apprestaurante.MainActivity)?.openDrawer() }

        val adapter = CartAdapter(
            onChangeQuantity = { id, delta -> viewModel.changeQuantity(id, delta) },
            onRemove = { id -> viewModel.removeFromCart(id) }
        )
        binding.recyclerCart.adapter = adapter
        viewModel.refreshJornada()
        binding.btnCheckout.setOnClickListener {
            if (ClientStore.session.value == null) askLogin() else showOrderForm()
        }
        binding.btnClear.setOnClickListener { viewModel.clearCart() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.orderPlaced.collect { id ->
                        ordersViewModel.trackOrder(id)
                        (activity as? MainActivity)?.goToMyOrder()
                    }
                }
                launch {
                    combine(viewModel.orderBusy, viewModel.jornadaOpen) { busy, open -> busy to open }
                        .collect { (busy, open) ->
                            binding.btnCheckout.isEnabled = !busy && open != false
                            binding.txtClosed.isVisible = open == false
                        }
                }
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

    /** Para pedir hay que tener cuenta: se lleva al cliente a Perfil. */
    private fun askLogin() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cart_login_title)
            .setMessage(R.string.cart_login_message)
            .setNegativeButton(R.string.order_back, null)
            .setPositiveButton(R.string.cart_login_go) { _, _ -> (activity as? MainActivity)?.goToProfile() }
            .show()
    }

    private fun showOrderForm() {
        val items = viewModel.cartItems.value
        if (items.isEmpty()) return
        val lines = items.joinToString("\n") {
            "${it.quantity} × ${it.product.title} — ${it.subtotal.toPrice()}"
        }
        val form = DialogOrderBinding.inflate(layoutInflater)
        form.txtSummary.text = getString(R.string.order_summary, lines, viewModel.cartTotal.value.toPrice())
        // Nombre y celular vienen de la cuenta; se pueden cambiar para este pedido.
        ClientStore.session.value?.let {
            form.inputName.setText(it.name)
            form.inputPhone.setText(it.phone)
        }

        // Método de pago: solo se registra, no se cobra desde la app.
        PaymentMethod.ALL.forEachIndexed { index, method ->
            val chip = layoutInflater.inflate(R.layout.item_chip_category, form.chipsPayment, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = method
            chip.text = getString(paymentLabel(method))
            form.chipsPayment.addView(chip)
            if (index == 0) form.chipsPayment.check(chip.id)
        }

        // Dirección y barrio solo si es a domicilio. Recoger no pide nada más.
        fun updateTypeFields() {
            form.layoutAddress.isVisible = form.radioDomicilio.isChecked
            form.layoutNeighborhood.isVisible = form.radioDomicilio.isChecked
        }
        form.radioType.setOnCheckedChangeListener { _, _ -> updateTypeFields() }
        updateTypeFields()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.order_title)
            .setView(form.root)
            .setNegativeButton(R.string.order_back, null)
            .setPositiveButton(R.string.order_finish, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = form.inputName.text?.toString().orEmpty().trim()
                val phone = normalizeColombianPhone(form.inputPhone.text?.toString().orEmpty())
                val isDelivery = form.radioDomicilio.isChecked
                val address = form.inputAddress.text?.toString().orEmpty().trim()
                val neighborhood = form.inputNeighborhood.text?.toString().orEmpty().trim()

                val nameOk = name.length >= 2
                val phoneOk = phone != null
                val addressOk = !isDelivery || address.length >= 5
                val neighborhoodOk = !isDelivery || neighborhood.length >= 2

                form.layoutName.error = if (nameOk) null else getString(R.string.order_error_name)
                form.layoutPhone.error = if (phoneOk) null else getString(R.string.order_error_phone)

                form.layoutAddress.error = if (addressOk) null else getString(R.string.order_error_address)
                form.layoutNeighborhood.error =
                    if (neighborhoodOk) null else getString(R.string.order_error_neighborhood)

                if (nameOk && phone != null && addressOk && neighborhoodOk) {
                    viewModel.submitOrder(
                        customerName = name,
                        phone = phone,
                        type = if (isDelivery) OrderType.DOMICILIO else OrderType.RECOGER,
                        tableNumber = null,
                        address = if (isDelivery) address else null,
                        neighborhood = if (isDelivery) neighborhood else null,
                        paymentMethod = form.chipsPayment
                            .findViewById<Chip>(form.chipsPayment.checkedChipId)?.tag as? String
                            ?: PaymentMethod.EFECTIVO,
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
