package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.FragmentDetailBinding
import com.bremnercamilourbano.apprestaurante.model.Product
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.util.loadImage
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import kotlinx.coroutines.launch

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private val viewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val productId: Int get() = requireArguments().getInt(ARG_PRODUCT_ID)
    private var product: Product? = null
    private var quantity = 1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailBinding.bind(view)
        binding.header.applyStatusBarPadding()
        quantity = savedInstanceState?.getInt(KEY_QUANTITY) ?: 1

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                renderQuantity()
            }
        }
        binding.btnPlus.setOnClickListener {
            if (quantity < MAX_QUANTITY) {
                quantity++
                renderQuantity()
            }
        }
        binding.btnAddToCart.setOnClickListener {
            product?.let {
                viewModel.addToCart(it, quantity)
                parentFragmentManager.popBackStack()
            }
        }

        // GET /products/{id}
        viewModel.loadProductDetail(productId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.products.collect { list ->
                    product = list.firstOrNull { it.id == productId }
                    renderProduct()
                }
            }
        }
    }

    private fun renderProduct() {
        val current = product
        binding.content.isVisible = current != null
        binding.txtLoading.isVisible = current == null
        if (current != null) {
            binding.imgProduct.loadImage(current.imageUrl)
            binding.txtName.text = current.title
            binding.txtCategory.text = getString(
                R.string.category_status,
                current.category,
                getString(if (current.available) R.string.available else R.string.unavailable)
            )
            binding.txtPrice.text = current.price.toPrice()
            binding.txtDescription.text = current.description
        }
        renderQuantity()
    }

    private fun renderQuantity() {
        binding.txtQuantity.text = quantity.toString()
        val current = product
        if (current != null && current.available) {
            binding.btnAddToCart.isEnabled = true
            binding.btnAddToCart.text =
                getString(R.string.detail_add_to_cart, (current.price * quantity).toPrice())
        } else {
            binding.btnAddToCart.isEnabled = false
            binding.btnAddToCart.setText(R.string.detail_unavailable_button)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_QUANTITY, quantity)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_PRODUCT_ID = "product_id"
        private const val KEY_QUANTITY = "quantity"
        private const val MAX_QUANTITY = 99

        fun newInstance(productId: Int) = DetailFragment().apply {
            arguments = bundleOf(ARG_PRODUCT_ID to productId)
        }
    }
}
