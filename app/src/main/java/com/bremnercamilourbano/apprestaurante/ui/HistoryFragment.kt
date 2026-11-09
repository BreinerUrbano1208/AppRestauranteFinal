package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import com.bremnercamilourbano.apprestaurante.databinding.FragmentHistoryBinding
import com.bremnercamilourbano.apprestaurante.util.toApiDate
import com.bremnercamilourbano.apprestaurante.viewmodel.LoadState
import com.bremnercamilourbano.apprestaurante.viewmodel.OrdersViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Historial de pedidos finalizados y cancelados (solo administrador). */
class HistoryFragment : Fragment(R.layout.fragment_history) {

    private val viewModel: OrdersViewModel by activityViewModels()
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHistoryBinding.bind(view)

        val adapter = OrderAdapter() // sin botones: solo lectura
        binding.recyclerHistory.adapter = adapter

        val filter = viewModel.historyFilter.value
        binding.inputSearch.setText(filter.query)
        binding.chipsStatus.check(
            when (filter.status) {
                OrderStatus.FINALIZADO -> R.id.chipDone
                OrderStatus.CANCELADO -> R.id.chipCancelled
                else -> R.id.chipAll
            }
        )

        binding.inputSearch.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty()
            if (q != viewModel.historyFilter.value.query) {
                viewModel.updateHistoryFilter(debounce = true) { it.copy(query = q) }
            }
        }
        binding.chipsStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            val status = when (checkedIds.firstOrNull()) {
                R.id.chipDone -> OrderStatus.FINALIZADO
                R.id.chipCancelled -> OrderStatus.CANCELADO
                else -> null
            }
            if (status != viewModel.historyFilter.value.status) {
                viewModel.updateHistoryFilter { it.copy(status = status) }
            }
        }
        binding.btnDates.setOnClickListener { pickDates() }
        binding.btnClearDates.setOnClickListener {
            viewModel.updateHistoryFilter { it.copy(from = null, to = null) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loadHistory() // al volver a esta pestaña se actualiza
                launch {
                    combine(viewModel.history, viewModel.historyState) { list, state -> list to state }
                        .collect { (list, state) ->
                            adapter.submitList(list)
                            val has = list.isNotEmpty()
                            binding.progress.isVisible = state is LoadState.Loading && !has
                            binding.txtError.isVisible = state is LoadState.Error && !has
                            binding.txtEmpty.isVisible = state is LoadState.Loaded && !has
                            binding.recyclerHistory.isVisible = has
                        }
                }
                launch {
                    viewModel.historyFilter.collect { f ->
                        val hasDates = f.from != null && f.to != null
                        binding.btnClearDates.isVisible = hasDates
                        binding.btnDates.text = if (hasDates) {
                            getString(R.string.history_dates_range, f.from, f.to)
                        } else {
                            getString(R.string.history_dates)
                        }
                    }
                }
            }
        }
    }

    private fun pickDates() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.history_date_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            val from = range.first
            val to = range.second
            if (from != null && to != null) {
                viewModel.updateHistoryFilter { it.copy(from = from.toApiDate(), to = to.toApiDate()) }
            }
        }
        picker.show(childFragmentManager, "history_dates")
    }

    override fun onDestroyView() {
        binding.recyclerHistory.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
