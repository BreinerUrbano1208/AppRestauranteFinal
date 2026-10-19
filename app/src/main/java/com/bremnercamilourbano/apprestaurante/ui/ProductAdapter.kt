package com.bremnercamilourbano.apprestaurante.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bremnercamilourbano.apprestaurante.databinding.ItemProductBinding
import com.bremnercamilourbano.apprestaurante.model.Product
import com.bremnercamilourbano.apprestaurante.util.loadImage
import com.bremnercamilourbano.apprestaurante.util.toPrice

internal object ProductDiff : DiffUtil.ItemCallback<Product>() {
    override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
}

class ProductAdapter(
    private val onOpen: (Product) -> Unit,
    private val onAdd: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ViewHolder>(ProductDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.imgProduct.loadImage(product.imageUrl)
            binding.txtName.text = product.title
            binding.txtCategory.text = product.category
            binding.txtDescription.text = product.description
            binding.txtPrice.text = product.price.toPrice()
            binding.root.setOnClickListener { onOpen(product) }
            binding.btnAdd.setOnClickListener { onAdd(product) }
        }
    }
}
