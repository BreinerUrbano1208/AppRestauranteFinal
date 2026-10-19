package com.bremnercamilourbano.apprestaurante.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.ItemAdminProductBinding
import com.bremnercamilourbano.apprestaurante.model.Product
import com.bremnercamilourbano.apprestaurante.util.loadImage
import com.bremnercamilourbano.apprestaurante.util.toPrice


class AdminProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onToggleAvailable: (Product) -> Unit
) : ListAdapter<Product, AdminProductAdapter.ViewHolder>(ProductDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAdminProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemAdminProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            val context = binding.root.context
            binding.imgProduct.loadImage(product.imageUrl)
            binding.txtName.text = product.title
            binding.txtInfo.text = context.getString(
                R.string.category_status,
                product.category,
                context.getString(if (product.available) R.string.available else R.string.unavailable)
            )
            binding.txtPrice.text = product.price.toPrice()
            binding.btnEdit.setOnClickListener { onEdit(product) }

            binding.switchAvailable.setOnCheckedChangeListener(null)
            binding.switchAvailable.isChecked = product.available
            binding.switchAvailable.setOnCheckedChangeListener { _, _ -> onToggleAvailable(product) }
        }
    }
}
