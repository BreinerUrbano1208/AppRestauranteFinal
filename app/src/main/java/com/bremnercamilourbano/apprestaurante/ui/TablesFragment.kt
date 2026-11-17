package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.AdminTableDto
import com.bremnercamilourbano.apprestaurante.data.OrderItemRequest
import com.bremnercamilourbano.apprestaurante.data.PaymentMethod
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.bremnercamilourbano.apprestaurante.util.orderStatusLabel
import com.bremnercamilourbano.apprestaurante.util.paymentLabel
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.bremnercamilourbano.apprestaurante.databinding.DialogTableFormBinding
import com.bremnercamilourbano.apprestaurante.databinding.FragmentTablesBinding
import com.bremnercamilourbano.apprestaurante.viewmodel.AdminViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.LoadState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Mesas. Administrador: registrar, editar y eliminar. Cajero: ver libres/ocupadas, cargar pedidos
 * a una mesa y facturar (el cliente pagó) para liberarla.
 */
class TablesFragment : Fragment(R.layout.fragment_tables) {

    private val viewModel: AdminViewModel by activityViewModels()
    private val restaurantViewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentTablesBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTablesBinding.bind(view)

        // Administrador: crea y edita mesas. Cajero: toca una mesa para cargar pedidos y facturar.
        val isAdmin = SessionStore.session.value?.isAdmin == true
        val adapter = TableAdapter { if (isAdmin) showOptions(it) else showAccount(it) }
        binding.recyclerTables.adapter = adapter
        binding.btnAddTable.isVisible = isAdmin
        binding.tableDetailContainer.isVisible =
            childFragmentManager.findFragmentByTag(DETAIL_TAG) != null
        binding.btnAddTable.setOnClickListener { showNewTableForm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.tables, viewModel.tablesState) { tables, state -> tables to state }
                        .collect { (tables, state) ->
                            adapter.submitList(tables)
                            val has = tables.isNotEmpty()
                            binding.progress.isVisible = state is LoadState.Loading && !has
                            binding.txtError.isVisible = state is LoadState.Error && !has
                            binding.txtEmpty.isVisible = state is LoadState.Loaded && !has
                            binding.recyclerTables.isVisible = has
                        }
                }
                launch {
                    // La ocupación cambia con los pedidos: se actualiza cada 10 segundos.
                    while (true) {
                        viewModel.refreshTables()
                        delay(POLL_MS)
                    }
                }
            }
        }
    }

    // ---------- Cajero: pantalla de la mesa ----------
    private fun showAccount(table: AdminTableDto) {
        if (!table.active) return
        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.tableDetailContainer, TableDetailFragment.newInstance(table.number), DETAIL_TAG)
        }
        binding.tableDetailContainer.isVisible = true
    }

    fun closeDetail() {
        childFragmentManager.findFragmentByTag(DETAIL_TAG)?.let { child ->
            childFragmentManager.commit { remove(child) }
        }
        _binding?.tableDetailContainer?.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch { viewModel.refreshTables() }
    }

    private fun showOptions(table: AdminTableDto) {
        val options = arrayOf(
            getString(R.string.tables_option_capacity),
            getString(if (table.active) R.string.tables_option_deactivate else R.string.tables_option_activate),
            getString(R.string.tables_option_delete)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.tables_form_title_edit, table.number))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> askCapacity(table)
                    1 -> viewModel.toggleActive(table)
                    else -> confirmDelete(table)
                }
            }
            .show()
    }

    private fun showNewTableForm() {
        val form = DialogTableFormBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tables_form_title_new)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = form.inputNumber.text?.toString().orEmpty().trim().toIntOrNull()
                val capacity = form.inputCapacity.text?.toString().orEmpty().trim().toIntOrNull()
                val numberOk = number != null && number in 1..200
                val capacityOk = capacity != null && capacity in 1..50
                form.layoutNumber.error = if (numberOk) null else getString(R.string.tables_error_number)
                form.layoutCapacity.error = if (capacityOk) null else getString(R.string.tables_error_capacity)
                if (numberOk && capacityOk) {
                    viewModel.createTable(number!!, capacity!!)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun askCapacity(table: AdminTableDto) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(table.capacity.toString())
            setSelection(text.length)
            hint = getString(R.string.field_table_capacity)
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.tables_form_title_edit, table.number))
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val capacity = input.text?.toString().orEmpty().trim().toIntOrNull()
                if (capacity == null || capacity !in 1..50) {
                    input.error = getString(R.string.tables_error_capacity)
                } else {
                    viewModel.setCapacity(table, capacity)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(table: AdminTableDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.tables_delete_title, table.number))
            .setMessage(R.string.tables_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.admin_delete_confirm) { _, _ -> viewModel.deleteTable(table) }
            .show()
    }

    override fun onDestroyView() {
        binding.recyclerTables.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val POLL_MS = 10_000L
        const val DETAIL_TAG = "table_detail"
    }
}
