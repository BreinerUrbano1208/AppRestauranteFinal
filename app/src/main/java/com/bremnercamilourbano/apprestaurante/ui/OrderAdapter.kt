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
import com.bremnercamilourbano.apprestaurante.util.paymentLabel
import com.bremnercamilourbano.apprestaurante.util.orderStatusLabel
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.util.toShortDateTime

private object OrderDiff : DiffUtil.ItemCallback<OrderDto>() {
    override fun areItemsTheSame(oldItem: OrderDto, newItem: OrderDto) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: OrderDto, newItem: OrderDto) = oldItem == newItem
}

/**
 * Tarjeta de pedido. Si [onAdvance] es null (historial) no se muestran botones.
 * [canCancel] dice si quien mira es cajero o administrador: solo ellos ven "Cancelar", "Cambiar mesa" y "Método de pago".
 * [canAdvance] dice si quien mira puede pasar ese pedido al siguiente estado (cocina no entrega).
 */
class OrderAdapter(
    private val onAdvance: ((OrderDto) -> Unit)? = null,
    private val onCancel: ((OrderDto) -> Unit)? = null,
    private val onChangeTable: ((OrderDto) -> Unit)? = null,
    private val onChangePayment: ((OrderDto) -> Unit)? = null,
    private val canCancel: () -> Boolean = { false },
    private val canAdvance: (OrderDto) -> Boolean = { true }
) : ListAdapter<OrderDto, OrderAdapter.ViewHolder>(OrderDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    // El contador de espera corre cada segundo solo mientras la tarjeta está a la vista.
    override fun onViewAttachedToWindow(holder: ViewHolder) = holder.startTicking()
    override fun onViewDetachedFromWindow(holder: ViewHolder) = holder.stopTicking()

    inner class ViewHolder(private val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root) {

        private var current: OrderDto? = null
        private val tick = object : Runnable {
            override fun run() {
                renderWait()
                binding.txtWait.postDelayed(this, 1000L)
            }
        }

        fun startTicking() {
            binding.txtWait.removeCallbacks(tick)
            tick.run()
        }

        fun stopTicking() {
            binding.txtWait.removeCallbacks(tick)
        }

        /** Pedido en curso: cuánto lleva esperando el cliente (verde, naranja desde 10 min, rojo desde 20 min).
         *  Pedido entregado: cuánto tardó en total. Cancelado: sin contador. */
        private fun renderWait() {
            val order = current ?: return
            val context = binding.root.context
            val view = binding.txtWait
            when (order.status) {
                in OrderStatus.ACTIVOS -> {
                    val ms = (System.currentTimeMillis() - order.createdAt).coerceAtLeast(0L)
                    val minutes = ms / 60_000L
                    view.text = context.getString(R.string.wait_running, ms.toClock())
                    val color = when {
                        minutes >= 20 -> R.color.wait_late
                        minutes >= 10 -> R.color.wait_warn
                        else -> R.color.wait_ok
                    }
                    view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
                    view.isVisible = true
                }
                OrderStatus.ENTREGADO -> {
                    val ms = (order.updatedAt - order.createdAt).coerceAtLeast(0L)
                    view.text = context.getString(R.string.wait_done, ms.toClock())
                    view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_finalizado))
                    view.isVisible = true
                }
                else -> view.isVisible = false
            }
        }

        private fun Long.toClock(): String {
            val total = this / 1000L
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
        }

        fun bind(order: OrderDto) {
            val context = binding.root.context
            current = order
            renderWait()
            binding.txtTitle.text = context.getString(R.string.order_item_title, order.id, order.customerName)

            binding.txtStatus.text = context.getString(orderStatusLabel(order.status))
            binding.txtStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, orderStatusColor(order.status)))

            val where = when {
                order.type == OrderType.MESA && order.tableNumber != null ->
                    context.getString(R.string.order_table, order.tableNumber)
                order.type == OrderType.DOMICILIO ->
                    context.getString(R.string.order_delivery, order.address.orEmpty(), order.neighborhood.orEmpty())
                else -> context.getString(R.string.order_pickup)
            }
            binding.txtMeta.text = context.getString(
                R.string.order_meta,
                where,
                context.getString(paymentLabel(order.paymentMethod)),
                order.phone,
                order.createdAt.toShortDateTime()
            )

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
            val showAdvance = onAdvance != null && next != null && canAdvance(order)
            val showCancel = onCancel != null && canCancel() && order.status in OrderStatus.ACTIVOS
            binding.panelActions.isVisible = showAdvance || showCancel

            binding.btnAdvance.isVisible = showAdvance
            binding.btnAdvance.setText(
                when (order.status) {
                    OrderStatus.RECIBIDO -> R.string.kitchen_start
                    OrderStatus.EN_PREPARACION -> R.string.kitchen_ready
                    else -> R.string.kitchen_deliver
                }
            )
            binding.btnAdvance.setOnClickListener { onAdvance?.invoke(order) }

            binding.btnCancel.isVisible = showCancel
            binding.btnCancel.setOnClickListener { onCancel?.invoke(order) }

            // Cambios que solo hace el administrador.
            val isAdmin = canCancel()
            val showTable = onChangeTable != null && isAdmin &&
                order.type == OrderType.MESA && order.status in OrderStatus.ACTIVOS
            val showPayment = onChangePayment != null && isAdmin && order.status != OrderStatus.CANCELADO
            binding.btnChangeTable.isVisible = showTable
            binding.btnChangePayment.isVisible = showPayment
            binding.panelAdmin.isVisible = showTable || showPayment
            binding.btnChangeTable.setOnClickListener { onChangeTable?.invoke(order) }
            binding.btnChangePayment.setOnClickListener { onChangePayment?.invoke(order) }
        }
    }
}
