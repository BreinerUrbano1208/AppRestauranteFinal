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
import com.bremnercamilourbano.apprestaurante.data.AdminTableDto
import com.bremnercamilourbano.apprestaurante.databinding.ItemTableBinding

private object TableDiff : DiffUtil.ItemCallback<AdminTableDto>() {
    override fun areItemsTheSame(oldItem: AdminTableDto, newItem: AdminTableDto) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: AdminTableDto, newItem: AdminTableDto) = oldItem == newItem
}

/** Tarjeta de mesa: verde = disponible, naranja = ocupada, gris = inactiva. */
class TableAdapter(
    private val onClick: (AdminTableDto) -> Unit
) : ListAdapter<AdminTableDto, TableAdapter.ViewHolder>(TableDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTableBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemTableBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(table: AdminTableDto) {
            val context = binding.root.context
            binding.txtNumber.text = context.getString(R.string.tables_card_title, table.number)
            binding.txtCapacity.text = context.getString(R.string.tables_capacity, table.capacity)

            val (label, color) = when {
                !table.active -> R.string.tables_state_off to R.color.table_inactive
                table.occupied -> R.string.tables_state_busy to R.color.status_recibido
                else -> R.string.tables_state_free to R.color.status_finalizado
            }
            binding.txtState.setText(label)
            binding.txtState.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))

            binding.txtOrders.isVisible = table.orderIds.isNotEmpty()
            if (table.orderIds.isNotEmpty()) {
                binding.txtOrders.text = context.getString(
                    R.string.tables_orders,
                    table.orderIds.joinToString(", ") { "#$it" }
                )
            }
            binding.root.setOnClickListener { onClick(table) }
        }
    }
}
