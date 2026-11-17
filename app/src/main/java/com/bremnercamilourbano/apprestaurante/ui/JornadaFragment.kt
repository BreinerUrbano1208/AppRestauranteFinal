package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.JornadaDto
import com.bremnercamilourbano.apprestaurante.data.OrderType
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.bremnercamilourbano.apprestaurante.databinding.FragmentJornadaBinding
import com.bremnercamilourbano.apprestaurante.databinding.ItemJornadaBinding
import com.bremnercamilourbano.apprestaurante.databinding.ItemMethodSaleBinding
import com.bremnercamilourbano.apprestaurante.util.orderStatusLabel
import com.bremnercamilourbano.apprestaurante.util.paymentLabel
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.util.toShortDateTime
import com.bremnercamilourbano.apprestaurante.viewmodel.AdminViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.LoadState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Jornada del restaurante. El administrador abre el sistema; el resumen cuenta desde ese momento.
 * Cajero o administrador pueden cerrar la caja con su clave. El administrador ve las jornadas anteriores.
 */
class JornadaFragment : Fragment(R.layout.fragment_jornada) {

    private val viewModel: AdminViewModel by activityViewModels()
    private var _binding: FragmentJornadaBinding? = null
    private val binding get() = _binding!!
    private var closeDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentJornadaBinding.bind(view)

        binding.btnOpen.setOnClickListener { viewModel.openJornada() }
        binding.btnClose.setOnClickListener { askClose() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.jornada, viewModel.jornadaState) { j, state -> j to state }
                        .collect { (j, state) ->
                            binding.txtError.isVisible = state is LoadState.Error && j == null
                            if (j != null) render(j)
                        }
                }
                launch { viewModel.jornadas.collect { renderHistory(it) } }
                launch {
                    viewModel.jornadaBusy.collect {
                        binding.btnOpen.isEnabled = !it
                        binding.btnClose.isEnabled = !it
                    }
                }
                launch { viewModel.closed.collect { closeDialog?.dismiss() } }
                launch {
                    while (true) {
                        viewModel.refreshJornada()
                        delay(POLL_MS)
                    }
                }
            }
        }
    }

    private fun render(j: JornadaDto) {
        val session = SessionStore.session.value
        val isAdmin = session?.isAdmin == true
        val isCajero = session?.isCajero == true

        binding.txtState.setText(if (j.open) R.string.jornada_open_state else R.string.jornada_closed_state)
        binding.txtSince.isVisible = j.open
        if (j.open) binding.txtSince.text = getString(R.string.jornada_since, j.openedAt.toShortDateTime())

        // El cajero abre y cierra la jornada; el administrador solo la consulta.
        binding.btnOpen.isVisible = !j.open && isCajero
        binding.btnClose.isVisible = j.open && isCajero
        binding.txtClosedInfo.isVisible = !j.open
        binding.txtClosedInfo.setText(
            if (isCajero) R.string.jornada_closed_info_cajero else R.string.jornada_closed_info
        )
        binding.groupSummary.isVisible = j.open

        if (!j.open) return
        binding.txtTotal.text = j.total.toPrice()
        binding.txtOrders.text = j.orders.toString()
        binding.txtCancelled.text = j.cancelled.toString()
        binding.txtActive.text = j.active.toString()
        binding.txtAverage.text = getString(R.string.sales_average) + ": " + j.averageTicket.toPrice()

        binding.listMethods.removeAllViews()
        j.byMethod.orEmpty().forEach { row ->
            val item = ItemMethodSaleBinding.inflate(LayoutInflater.from(requireContext()), binding.listMethods, false)
            item.txtMethod.setText(paymentLabel(row.method))
            item.txtMethodOrders.text = getString(R.string.sales_method_orders, row.orders)
            item.txtMethodTotal.text = row.total.toPrice()
            binding.listMethods.addView(item.root)
        }
    }

    private fun renderHistory(list: List<JornadaDto>) {
        binding.txtHistoryTitle.isVisible = list.isNotEmpty()
        binding.listJornadas.removeAllViews()
        list.forEach { j ->
            val item = ItemJornadaBinding.inflate(LayoutInflater.from(requireContext()), binding.listJornadas, false)
            item.txtJornadaTitle.text = getString(R.string.jornada_item_title, j.id)
            item.txtJornadaRange.text = getString(
                R.string.jornada_item_range,
                j.openedAt.toShortDateTime(),
                (j.closedAt ?: j.openedAt).toShortDateTime()
            )
            item.txtJornadaTotal.text = j.total.toPrice()
            item.txtJornadaMeta.text = getString(
                R.string.jornada_item_meta, j.orders, j.cancelled, j.closedBy.orEmpty()
            )
            item.root.setOnClickListener { showEvidence(j) }
            binding.listJornadas.addView(item.root)
        }
    }

    /** Evidencia de una jornada cerrada: lista de sus pedidos (solo administrador). */
    private fun showEvidence(j: JornadaDto) {
        viewLifecycleOwner.lifecycleScope.launch {
            val orders = viewModel.fetchJornadaOrders(j.id) ?: return@launch
            val text = if (orders.isEmpty()) {
                getString(R.string.jornada_evidence_empty)
            } else {
                orders.joinToString("\n") {
                    val where = when {
                        it.type == OrderType.MESA && it.tableNumber != null ->
                            getString(R.string.order_table, it.tableNumber)
                        it.type == OrderType.DOMICILIO -> getString(R.string.order_type_domicilio)
                        else -> getString(R.string.order_pickup)
                    }
                    getString(
                        R.string.jornada_evidence_line,
                        it.id, where, getString(orderStatusLabel(it.status)), it.total.toPrice()
                    )
                }
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.jornada_evidence_title, j.id))
                .setMessage(text)
                .setPositiveButton(R.string.tables_close, null)
                .show()
        }
    }

    /** Pide la clave de quien cierra. El diálogo solo se cierra si la clave es correcta. */
    private fun askClose() {
        viewModel.clearCloseError()
        val layout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.jornada_close_password_hint)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val input = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        layout.addView(input)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(layout)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.jornada_close_title)
            .setMessage(R.string.jornada_close_message)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.jornada_close_confirm, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                viewModel.closeJornada(input.text?.toString().orEmpty())
            }
        }
        closeDialog = dialog

        val errorJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.closeError.collect { layout.error = it }
        }
        dialog.setOnDismissListener { errorJob.cancel() }
        dialog.show()
    }

    override fun onDestroyView() {
        closeDialog?.dismiss()
        closeDialog = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val POLL_MS = 15_000L
    }
}
