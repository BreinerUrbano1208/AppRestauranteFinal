package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.MainActivity
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.databinding.FragmentMenuBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.viewmodel.ErrorKind
import com.bremnercamilourbano.apprestaurante.viewmodel.LoadState
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MenuFragment : Fragment(R.layout.fragment_menu) {

    private val viewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMenuBinding.bind(view)
        binding.header.applyStatusBarPadding()

        val adapter = ProductAdapter(
            onOpen = { product -> (activity as? MainActivity)?.openDetail(product.id) },
            onAdd = { product -> viewModel.addToCart(product) }
        )
        binding.recyclerProducts.adapter = adapter
        binding.btnRefresh.setOnClickListener { viewModel.loadProducts() }
        binding.btnRetry.setOnClickListener { viewModel.loadProducts() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.menu, viewModel.loadState) { menu, state -> menu to state }
                    .collect { (menu, state) ->
                        adapter.submitList(menu)
                        val loading = state is LoadState.Loading
                        val hasItems = menu.isNotEmpty()
                        binding.btnRefresh.isEnabled = !loading
                        binding.progress.isVisible = loading && !hasItems
                        binding.recyclerProducts.isVisible = hasItems
                        binding.layoutError.isVisible = state is LoadState.Error && !hasItems
                        binding.txtEmpty.isVisible = state is LoadState.Loaded && !hasItems
                        if (state is LoadState.Error) {
                            binding.txtError.setText(
                                when (state.kind) {
                                    ErrorKind.NETWORK -> R.string.error_network
                                    ErrorKind.SERVER -> R.string.error_server
                                    ErrorKind.UNKNOWN -> R.string.error_unknown
                                }
                            )
                        }
                    }
            }
        }
    }

    override fun onDestroyView() {
        binding.recyclerProducts.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
