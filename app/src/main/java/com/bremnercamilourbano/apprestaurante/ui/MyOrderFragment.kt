package com.bremnercamilourbano.apprestaurante.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.MainActivity
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import com.bremnercamilourbano.apprestaurante.data.OrderType
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.databinding.FragmentMyOrderBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.orderProgress
import com.bremnercamilourbano.apprestaurante.util.orderStatusColor
import com.bremnercamilourbano.apprestaurante.util.orderStatusHelp
import com.bremnercamilourbano.apprestaurante.util.orderStatusLabel
import com.bremnercamilourbano.apprestaurante.viewmodel.MyOrder
import com.bremnercamilourbano.apprestaurante.viewmodel.OrdersViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pantalla "Mi pedido": consulta el estado al servidor cada pocos segundos mientras se ve. */
class MyOrderFragment : Fragment(R.layout.fragment_my_order) {

    private val viewModel: OrdersViewModel by activityViewModels()
    private var _binding: FragmentMyOrderBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMyOrderBinding.bind(view)
        binding.header.applyStatusBarPadding()
        binding.btnDrawer.setOnClickListener { (activity as? com.bremnercamilourbano.apprestaurante.MainActivity)?.openDrawer() }

        binding.btnNewOrder.setOnClickListener {
            viewModel.clearMyOrder()
            (activity as? MainActivity)?.goToMenu()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.myOrder.collect { render(it) } }
                launch {
                    while (true) {
                        viewModel.refreshMyOrder()
                        delay(POLL_MS)
                    }
                }
            }
        }
    }

    private fun render(order: MyOrder?) {
        binding.txtEmpty.isVisible = order == null
        binding.panelOrder.isVisible = order != null
        if (order == null) return

        val status = order.status
        binding.txtOrderId.text = getString(R.string.myorder_number)
        binding.txtStatus.text = getString(orderStatusLabel(status))
        binding.txtStatus.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), orderStatusColor(status)))
        binding.progressOrder.setProgressCompat(orderProgress(status), true)
        binding.txtHelp.setText(orderStatusHelp(status))

        val reason = order.cancelReason
        binding.txtReason.isVisible = status == OrderStatus.CANCELADO && !reason.isNullOrBlank()
        if (binding.txtReason.isVisible) binding.txtReason.text = getString(R.string.myorder_reason, reason)

        binding.txtOffline.isVisible = order.offline
        binding.btnNewOrder.isVisible = status != null && status in OrderStatus.TERMINADOS

        renderTimeline(status)
        renderDetail(order)
    }

    /** Línea de tiempo: ✓ pasos hechos, ● paso actual, ○ pasos que faltan. Si se canceló, no se muestra. */
    private fun renderTimeline(status: String?) {
        binding.cardTimeline.isVisible = status != OrderStatus.CANCELADO
        val steps = listOf(
            binding.step1 to (OrderStatus.RECIBIDO to R.string.status_recibido),
            binding.step2 to (OrderStatus.EN_PREPARACION to R.string.status_en_preparacion),
            binding.step3 to (OrderStatus.LISTO to R.string.status_listo),
            binding.step4 to (OrderStatus.ENTREGADO to R.string.status_entregado)
        )
        val order = steps.map { it.second.first }
        val current = order.indexOf(status)
        val primary = com.google.android.material.color.MaterialColors.getColor(
            binding.root, androidx.appcompat.R.attr.colorPrimary
        )
        val muted = com.google.android.material.color.MaterialColors.getColor(
            binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        steps.forEachIndexed { index, (view, step) ->
            val label = getString(step.second)
            when {
                current < 0 || index > current -> {
                    view.text = getString(R.string.myorder_step_pending, label)
                    view.setTextColor(muted)
                }
                // El último paso (Entregado) cuenta como terminado, no como "en curso".
                index == current && index < order.lastIndex -> {
                    view.text = getString(R.string.myorder_step_current, label)
                    view.setTextColor(primary)
                }
                else -> {
                    view.text = getString(R.string.myorder_step_done, label)
                    view.setTextColor(primary)
                }
            }
        }
    }

    private fun renderDetail(order: MyOrder) {
        val hasDetail = order.type != null
        binding.cardDetail.isVisible = hasDetail
        if (!hasDetail) return
        binding.txtType.text = when {
            order.type == OrderType.MESA && order.tableNumber != null ->
                getString(R.string.myorder_type_mesa, order.tableNumber)
            order.type == OrderType.DOMICILIO -> getString(R.string.myorder_type_domicilio)
            else -> getString(R.string.myorder_type_recoger)
        }
        binding.txtItems.text = order.items.joinToString("\n") {
            getString(R.string.order_line, it.quantity, it.name)
        }
        binding.txtTotal.text = getString(R.string.order_total_label, order.total.toPrice())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val POLL_MS = 7_000L
    }
}
