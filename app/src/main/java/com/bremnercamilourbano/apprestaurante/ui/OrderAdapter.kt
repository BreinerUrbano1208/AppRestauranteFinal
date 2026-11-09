package com.bremnercamilourbano.apprestaurante.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderDto
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import com.bremnercamilourbano.apprestaurante.data.OrderType
import com.bremnercamilourbano.apprestaurante.databinding.ItemOrderBinding
import com.bremnercamilourbano.apprestaurante.util.orderStatusColor
import com.bremnercamilourbano.apprestaurante.util.orderStatusLabel
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.util.toShortDateTime

private object OrderDiff : DiffUtil.ItemCallback<OrderDto>() {
    override fun areItemsTheSame(oldItem: OrderDto, newItem: OrderDto) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: OrderDto, newItem: OrderDto) = oldItem == newItem
}

/**
 * Tarjeta de pedido. Si [onAdvance] es null (historial) no se muestran botones.
 * [canCancel] decide si se muestra "Cancelar" (solo administrador).
 */
class OrderAdapter(
    private val onAdvance: ((OrderDto) -> Unit)? = null,
    private val onCancel: ((OrderDto) -> Unit)? = null,
    private val canCancel: () -> Boolean = { false }
) : ListAdapter<OrderDto, OrderAdapter.ViewHolder>(OrderDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: OrderDto) {
            val context = binding.root.context
            binding.txtTitle.text = context.getString(R.string.order_item_title, order.id, order.customerName)

            binding.txtStatus.text = context.getString(orderStatusLabel(order.status))
            binding.txtStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, orderStatusColor(order.status)))

            val where = if (order.type == OrderType.MESA && order.tableNumber != null) {
                context.getString(R.string.order_table, order.tableNumber)
            } else {
                context.getString(R.string.order_pickup)
            }
            binding.txtMeta.text =
                context.getString(R.string.order_meta, where, order.phone, order.createdAt.toShortDateTime())

            binding.txtItems.text = order.items.joinToString("\n") {
                context.getString(R.string.order_line, it.quantity, it.name)
            }

            binding.txtNote.isVisible = order.note.isNotBlank()
            binding.txtNote.text = context.getString(R.string.order_note_label, order.note)

            val reason = order.cancelReason
            binding.txtReason.isVisible = order.status == OrderStatus.CANCELADO && !reason.isNullOrBlank()
            if (binding.txtReason.isVisible) {
                binding.txtReason.text = context.getString(R.string.myorder_reason, reason)
            }

            binding.txtTotal.text = context.getString(R.string.order_total_label, order.total.toPrice())

            val next = OrderStatus.next(order.status)
            val showAdvance = onAdvance != null && next != null
            val showCancel = onCancel != null && canCancel() && order.status in OrderStatus.ACTIVOS
            binding.panelActions.isVisible = showAdvance || showCancel

            binding.btnAdvance.isVisible = showAdvance
            binding.btnAdvance.setText(
                if (order.status == OrderStatus.RECIBIDO) R.string.kitchen_start else R.string.kitchen_finish
            )
            binding.btnAdvance.setOnClickListener { onAdvance?.invoke(order) }

            binding.btnCancel.isVisible = showCancel
            binding.btnCancel.setOnClickListener { onCancel?.invoke(order) }
        }
    }
}
