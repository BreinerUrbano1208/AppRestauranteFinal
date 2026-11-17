package com.bremnercamilourbano.apprestaurante.ui

import androidx.fragment.app.Fragment
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderDto
import com.bremnercamilourbano.apprestaurante.data.PaymentMethod
import com.bremnercamilourbano.apprestaurante.data.TableDto
import com.bremnercamilourbano.apprestaurante.util.paymentLabel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Lista de mesas registradas para mover un pedido. */
fun Fragment.showChangeTableDialog(order: OrderDto, tables: List<TableDto>, onPick: (Int) -> Unit) {
    val builder = MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.change_table_title, order.id))
        .setNegativeButton(R.string.cancel, null)
    if (tables.isEmpty()) {
        builder.setMessage(R.string.order_no_tables).show()
        return
    }
    val labels = tables.map { getString(R.string.tables_capacity_line, it.number, it.capacity) }.toTypedArray()
    val checked = tables.indexOfFirst { it.number == order.tableNumber }
    builder.setSingleChoiceItems(labels, checked) { dialog, which ->
        onPick(tables[which].number)
        dialog.dismiss()
    }.show()
}

/** Lista de métodos de pago para corregir el que se registró. */
fun Fragment.showChangePaymentDialog(order: OrderDto, onPick: (String) -> Unit) {
    val methods = PaymentMethod.ALL
    val labels = methods.map { getString(paymentLabel(it)) }.toTypedArray()
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.change_payment_title, order.id))
        .setSingleChoiceItems(labels, methods.indexOf(order.paymentMethod)) { dialog, which ->
            onPick(methods[which])
            dialog.dismiss()
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
