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
        binding.txtOrderId.text = getString(R.string.myorder_number, order.id)
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
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val POLL_MS = 7_000L
    }
}
