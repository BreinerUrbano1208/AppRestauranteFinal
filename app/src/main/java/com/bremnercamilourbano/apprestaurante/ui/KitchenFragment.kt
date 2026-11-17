package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderDto
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import com.bremnercamilourbano.apprestaurante.data.OrderType
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.bremnercamilourbano.apprestaurante.databinding.FragmentKitchenBinding
import com.bremnercamilourbano.apprestaurante.viewmodel.LoadState
import com.bremnercamilourbano.apprestaurante.viewmodel.OrdersViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Pedidos activos para cocina y administrador. Se actualiza cada 10 segundos mientras se ve. */
class KitchenFragment : Fragment(R.layout.fragment_kitchen) {

    private val viewModel: OrdersViewModel by activityViewModels()
    private val restaurantViewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentKitchenBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKitchenBinding.bind(view)

        val adapter = OrderAdapter(
            onAdvance = { viewModel.advance(it) },
            onCancel = { askCancel(it) },
            onChangeTable = { order ->
                showChangeTableDialog(order, restaurantViewModel.tables.value) { viewModel.changeTable(order, it) }
            },
            onChangePayment = { order ->
                showChangePaymentDialog(order) { viewModel.changePayment(order, it) }
            },
            canCancel = { SessionStore.session.value?.canManage == true },
            canAdvance = { order ->
                // Cocina prepara; solo cajero/administrador marcan "entregado".
                OrderStatus.next(order.status) != OrderStatus.ENTREGADO ||
                    (SessionStore.session.value?.canManage == true && order.type != OrderType.MESA)
            }
        )
        val manager = SessionStore.session.value?.canManage == true
        binding.chipDelivered.isVisible = manager
        binding.chipCancelledOrders.isVisible = manager
        binding.recyclerOrders.adapter = adapter
        restaurantViewModel.loadTables() // para el diálogo "Cambiar mesa"

        // Estado de los filtros guardado en el ViewModel (sobrevive a girar la pantalla).
        binding.chipsStatus.check(
            when (viewModel.statusFilter.value) {
                OrderStatus.RECIBIDO -> R.id.chipReceived
                OrderStatus.EN_PREPARACION -> R.id.chipPreparing
                OrderStatus.LISTO -> R.id.chipReady
                OrderStatus.ENTREGADO -> R.id.chipDelivered
                OrderStatus.CANCELADO -> R.id.chipCancelledOrders
                else -> R.id.chipActive
            }
        )
        binding.chipsStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setStatusFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.chipReceived -> OrderStatus.RECIBIDO
                    R.id.chipPreparing -> OrderStatus.EN_PREPARACION
                    R.id.chipReady -> OrderStatus.LISTO
                    R.id.chipDelivered -> OrderStatus.ENTREGADO
                    R.id.chipCancelledOrders -> OrderStatus.CANCELADO
                    else -> null
                }
            )
        }
        viewModel.tableFilter.value?.let { binding.inputTable.setText(it.toString()) }
        binding.inputTable.doAfterTextChanged {
            viewModel.setTableFilter(it?.toString()?.trim()?.toIntOrNull())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.kitchenOrders, viewModel.activeState) { orders, state -> orders to state }
                        .collect { (orders, state) ->
                            adapter.submitList(orders)
                            val has = orders.isNotEmpty()
                            binding.progress.isVisible = state is LoadState.Loading && !has
                            binding.txtError.isVisible = state is LoadState.Error && !has
                            binding.txtEmpty.isVisible = state is LoadState.Loaded && !has
                            binding.recyclerOrders.isVisible = has
                        }
                }
                launch {
                    while (true) {
                        viewModel.refreshActive()
                        delay(POLL_MS)
                    }
                }
            }
        }
    }

    private fun askCancel(order: OrderDto) {
        val input = EditText(requireContext()).apply {
            setHint(R.string.kitchen_cancel_reason_hint)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(200))
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.kitchen_cancel_title, order.id))
            .setMessage(R.string.kitchen_cancel_message)
            .setView(container)
            .setNegativeButton(R.string.kitchen_cancel_back, null)
            .setPositiveButton(R.string.kitchen_cancel_confirm) { _, _ ->
                viewModel.cancel(order, input.text?.toString().orEmpty())
            }
            .show()
    }

    override fun onDestroyView() {
        binding.recyclerOrders.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val POLL_MS = 10_000L
    }
}
