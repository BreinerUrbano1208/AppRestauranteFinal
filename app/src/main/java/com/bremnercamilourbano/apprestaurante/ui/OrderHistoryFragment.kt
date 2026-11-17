package com.bremnercamilourbano.apprestaurante.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bremnercamilourbano.apprestaurante.MainActivity
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderDto
import com.bremnercamilourbano.apprestaurante.data.OrderType
import com.bremnercamilourbano.apprestaurante.databinding.FragmentOrderHistoryBinding
import com.bremnercamilourbano.apprestaurante.databinding.ItemProfileOrderBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.orderStatusColor
import com.bremnercamilourbano.apprestaurante.util.orderStatusLabel
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.util.toShortDateTime
import com.bremnercamilourbano.apprestaurante.viewmodel.AccountViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.OrdersViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Historial de pedidos del cliente (se abre desde el menú lateral). */
class OrderHistoryFragment : Fragment(R.layout.fragment_order_history) {

    private val viewModel: AccountViewModel by activityViewModels()
    private val ordersViewModel: OrdersViewModel by activityViewModels()
    private var _binding: FragmentOrderHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOrderHistoryBinding.bind(view)
        binding.header.applyStatusBarPadding()
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = ProfileOrderAdapter { order ->
            ordersViewModel.trackOrder(order.id)
            parentFragmentManager.popBackStack()
            (activity as? MainActivity)?.goToMyOrder()
        }
        binding.recyclerOrders.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.orders.collect { list ->
                        val first = adapter.itemCount == 0 && list.isNotEmpty()
                        adapter.submitList(list)
                        if (first) binding.recyclerOrders.scheduleLayoutAnimation()
                        binding.txtNoOrders.isVisible = list.isEmpty()
                        binding.recyclerOrders.isVisible = list.isNotEmpty()
                    }
                }
                launch {
                    // Los pedidos cambian de estado: se actualizan cada 15 segundos mientras se ve.
                    while (true) {
                        viewModel.refreshOrders()
                        delay(POLL_MS)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.recyclerOrders.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val POLL_MS = 15_000L
    }
}

private object ProfileOrderDiff : DiffUtil.ItemCallback<OrderDto>() {
    override fun areItemsTheSame(oldItem: OrderDto, newItem: OrderDto) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: OrderDto, newItem: OrderDto) = oldItem == newItem
}

/** Fila del historial. Al tocarla se sigue ese pedido en "Mi pedido". */
private class ProfileOrderAdapter(
    private val onClick: (OrderDto) -> Unit
) : ListAdapter<OrderDto, ProfileOrderAdapter.ViewHolder>(ProfileOrderDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemProfileOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemProfileOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(order: OrderDto) {
            val context = binding.root.context
            binding.txtTitle.text = context.getString(R.string.profile_order_title, order.createdAt.toShortDateTime())
            val where = when (order.type) {
                OrderType.DOMICILIO -> context.getString(R.string.order_type_domicilio)
                OrderType.MESA -> context.getString(R.string.order_table, order.tableNumber ?: 0)
                else -> context.getString(R.string.order_pickup)
            }
            binding.txtMeta.text = where
            binding.txtTotal.text = order.total.toPrice()
            binding.txtStatus.setText(orderStatusLabel(order.status))
            binding.txtStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, orderStatusColor(order.status)))
            binding.root.setOnClickListener { onClick(order) }
        }
    }
}
