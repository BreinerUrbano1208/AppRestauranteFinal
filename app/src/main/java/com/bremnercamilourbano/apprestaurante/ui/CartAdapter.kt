package com.bremnercamilourbano.apprestaurante.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.ItemCartBinding
import com.bremnercamilourbano.apprestaurante.model.CartItem
import com.bremnercamilourbano.apprestaurante.util.loadImage
import com.bremnercamilourbano.apprestaurante.util.toPrice

private object CartDiff : DiffUtil.ItemCallback<CartItem>() {
    override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem) =
        oldItem.product.id == newItem.product.id

    override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem) = oldItem == newItem
}


class CartAdapter(
    private val onChangeQuantity: (productId: Int, delta: Int) -> Unit,
    private val onRemove: (productId: Int) -> Unit
) : ListAdapter<CartItem, CartAdapter.ViewHolder>(CartDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemCartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItem) {
            val product = item.product
            binding.imgProduct.loadImage(product.imageUrl)
            binding.txtName.text = product.title
            binding.txtUnitPrice.text =
                binding.root.context.getString(R.string.cart_unit_price, product.price.toPrice())
            binding.txtSubtotal.text = item.subtotal.toPrice()
            binding.txtQuantity.text = item.quantity.toString()
            binding.btnMinus.setOnClickListener { onChangeQuantity(product.id, -1) }
            binding.btnPlus.setOnClickListener { onChangeQuantity(product.id, 1) }
            binding.btnRemove.setOnClickListener { onRemove(product.id) }
        }
    }
}
