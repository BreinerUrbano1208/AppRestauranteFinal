package com.bremnercamilourbano.apprestaurante.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.OrderDto
import com.bremnercamilourbano.apprestaurante.data.OrderItemRequest
import com.bremnercamilourbano.apprestaurante.data.OrderStatus
import com.bremnercamilourbano.apprestaurante.data.PaymentMethod
import com.bremnercamilourbano.apprestaurante.data.TableAccountDto
import com.bremnercamilourbano.apprestaurante.databinding.FragmentTableDetailBinding
import com.bremnercamilourbano.apprestaurante.databinding.ItemBillLineBinding
import com.bremnercamilourbano.apprestaurante.databinding.ItemPickProductBinding
import com.bremnercamilourbano.apprestaurante.databinding.ItemTableOrderBinding
import com.bremnercamilourbano.apprestaurante.model.Product
import com.bremnercamilourbano.apprestaurante.util.loadImage
import com.bremnercamilourbano.apprestaurante.util.matchesQuery
import com.bremnercamilourbano.apprestaurante.util.orderStatusColor
import com.bremnercamilourbano.apprestaurante.util.orderStatusLabel
import com.bremnercamilourbano.apprestaurante.util.paymentLabel
import com.bremnercamilourbano.apprestaurante.util.toPrice
import com.bremnercamilourbano.apprestaurante.viewmodel.AdminViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla de una mesa (cajero). Dos pestañas: «Agregar pedido» (todos los productos con imagen,
 * precio y buscador) y «Mirar pedido» (cuenta de la mesa con tiempo de espera y botón Finalizar).
 */
class TableDetailFragment : Fragment(R.layout.fragment_table_detail) {

    private val adminViewModel: AdminViewModel by activityViewModels()
    private val restaurantViewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentTableDetailBinding? = null
    private val binding get() = _binding!!

    private val number: Int get() = requireArguments().getInt(ARG_NUMBER)
    private val quantities = mutableMapOf<Int, Int>()
    private var query = ""
    private var account: TableAccountDto? = null

    private lateinit var pickAdapter: PickAdapter
    private lateinit var orderAdapter: TableOrderAdapter
    private lateinit var billAdapter: BillAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTableDetailBinding.bind(view)

        binding.txtTableTitle.text = getString(R.string.tables_account_title, number)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.groupCheckout.isVisible) exitCheckout() else close()
                }
            }
        )

        pickAdapter = PickAdapter(
            quantityOf = { quantities[it] ?: 0 },
            onChange = { id, delta ->
                val value = ((quantities[id] ?: 0) + delta).coerceIn(0, 99)
                if (value == 0) quantities.remove(id) else quantities[id] = value
                updatePickTotal()
            }
        )
        binding.recyclerProducts.adapter = pickAdapter
        orderAdapter = TableOrderAdapter()
        binding.recyclerAccount.adapter = orderAdapter
        billAdapter = BillAdapter()
        binding.recyclerBill.adapter = billAdapter
        binding.chipsMethod.setOnCheckedStateChangeListener { _, ids ->
            binding.chipsTransfer.isVisible = ids.contains(R.id.chipTransfer)
        }
        binding.btnBack.setOnClickListener {
            if (binding.groupCheckout.isVisible) exitCheckout() else close()
        }
        binding.btnConfirmBill.setOnClickListener { confirmBill() }

        binding.tabsTable.addTab(binding.tabsTable.newTab().setText(R.string.table_tab_add))
        binding.tabsTable.addTab(binding.tabsTable.newTab().setText(R.string.table_tab_view))
        binding.tabsTable.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.inputSearch.doAfterTextChanged {
            query = it?.toString().orEmpty()
            renderProducts()
        }
        binding.btnSend.setOnClickListener { showSummary() }
        binding.txtPickTotal.setOnClickListener { showSummary() }
        binding.btnAddMore.setOnClickListener { binding.tabsTable.getTabAt(0)?.select() }
        binding.btnFinish.setOnClickListener { showCheckout() }

        restaurantViewModel.loadProducts(silent = true)
        updatePickTotal()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { restaurantViewModel.products.collect { renderProducts() } }
                launch {
                    while (true) {
                        loadAccount()
                        delay(10_000L)
                    }
                }
                launch {
                    // El contador de espera avanza cada segundo.
                    while (true) {
                        delay(1_000L)
                        if (orderAdapter.itemCount > 0) {
                            orderAdapter.notifyItemRangeChanged(0, orderAdapter.itemCount, TICK)
                        }
                    }
                }
            }
        }
    }

    private fun showTab(position: Int) {
        binding.groupAdd.isVisible = position == 0
        binding.groupAccount.isVisible = position == 1
        if (position == 1) viewLifecycleOwner.lifecycleScope.launch { loadAccount() }
    }

    private fun renderProducts() {
        val list = restaurantViewModel.products.value.filter { it.available && it.matchesQuery(query) }
        pickAdapter.submitList(list)
        binding.txtNoProducts.isVisible = list.isEmpty()
    }

    private fun updatePickTotal() {
        val products = restaurantViewModel.products.value.associateBy { it.id }
        val count = quantities.values.sum()
        val total = quantities.entries.sumOf { (id, q) -> (products[id]?.price ?: 0.0) * q }
        binding.txtPickTotal.text = getString(R.string.table_pick_total, count, total.toPrice())
        binding.btnSend.isEnabled = count > 0
    }

    /** Detalle del pedido antes de enviarlo a cocina: qué pidió el cliente, cantidades y total. */
    private fun showSummary() {
        if (quantities.isEmpty()) return
        val products = restaurantViewModel.products.value.associateBy { it.id }
        var total = 0.0
        val text = quantities.entries.mapNotNull { (id, q) ->
            val product = products[id] ?: return@mapNotNull null
            total += product.price * q
            getString(R.string.order_line, q, product.title) + "  ·  " + (product.price * q).toPrice()
        }.joinToString("\n") + "\n\n" + getString(R.string.table_summary_total, total.toPrice())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.table_summary_title, number))
            .setMessage(text)
            .setNegativeButton(R.string.table_summary_edit, null)
            .setPositiveButton(R.string.table_summary_send) { _, _ -> send() }
            .show()
    }

    private fun send() {
        if (quantities.isEmpty()) return
        val items = quantities.map { (id, q) -> OrderItemRequest(id, q) }
        binding.btnSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = adminViewModel.addTableOrder(number, items)
            if (_binding == null) return@launch
            if (ok) {
                quantities.clear()
                binding.inputSearch.text?.clear()
                pickAdapter.notifyDataSetChanged()
                updatePickTotal()
                loadAccount()
                binding.tabsTable.getTabAt(1)?.select()
            } else {
                updatePickTotal()
            }
        }
    }

    private suspend fun loadAccount() {
        val result = adminViewModel.fetchAccount(number) ?: return
        if (_binding == null) return
        account = result
        orderAdapter.submitList(result.orders)
        binding.txtAccountEmpty.isVisible = result.orders.isEmpty()
        binding.txtAccountTotal.isVisible = result.orders.isNotEmpty()
        binding.txtAccountTotal.text = getString(R.string.table_account_line_total, result.total.toPrice())
        binding.btnFinish.isEnabled = result.orders.isNotEmpty()
    }

    /** Pantalla de cobro: productos consumidos (como el carrito) y forma de pago. */
    private fun showCheckout() {
        val current = account ?: return
        if (current.orders.isEmpty()) return
        val images = restaurantViewModel.products.value.associate { it.id to it.imageUrl }
        val lines = current.orders
            .filter { it.status != OrderStatus.CANCELADO }
            .flatMap { it.items }
            .groupBy { it.name }
            .map { (_, group) ->
                val first = group.first()
                BillLine(first.name, images[first.productId], first.price, group.sumOf { it.quantity })
            }
        billAdapter.submitList(lines)
        binding.txtBillTotal.text = current.total.toPrice()
        binding.txtTableTitle.text = getString(R.string.tables_bill_title, number)
        binding.tabsTable.isVisible = false
        binding.groupAdd.isVisible = false
        binding.groupAccount.isVisible = false
        binding.groupCheckout.isVisible = true
    }

    private fun exitCheckout() {
        binding.txtTableTitle.text = getString(R.string.tables_account_title, number)
        binding.groupCheckout.isVisible = false
        binding.tabsTable.isVisible = true
        showTab(binding.tabsTable.selectedTabPosition.coerceAtLeast(0))
    }

    private fun confirmBill() {
        val method = when {
            binding.chipsMethod.checkedChipId == R.id.chipCard -> PaymentMethod.TARJETA
            binding.chipsMethod.checkedChipId == R.id.chipTransfer ->
                if (binding.chipsTransfer.checkedChipId == R.id.chipDaviplata) PaymentMethod.DAVIPLATA
                else PaymentMethod.NEQUI
            else -> PaymentMethod.EFECTIVO
        }
        binding.btnConfirmBill.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = adminViewModel.billTable(number, method)
            if (_binding == null) return@launch
            if (ok) close() else binding.btnConfirmBill.isEnabled = true
        }
    }

    private fun close() {
        (parentFragment as? TablesFragment)?.closeDetail()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // ---------- Adaptadores ----------
    private class PickAdapter(
        private val quantityOf: (Int) -> Int,
        private val onChange: (id: Int, delta: Int) -> Unit
    ) : ListAdapter<Product, PickAdapter.VH>(object : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
        override fun areContentsTheSame(a: Product, b: Product) = a == b
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPickProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        inner class VH(private val b: ItemPickProductBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(product: Product) {
                b.imgProduct.loadImage(product.imageUrl)
                b.txtName.text = product.title
                b.txtPrice.text = product.price.toPrice()
                b.txtQuantity.text = quantityOf(product.id).toString()
                b.btnMinus.setOnClickListener { change(product.id, -1) }
                b.btnPlus.setOnClickListener { change(product.id, 1) }
            }

            private fun change(id: Int, delta: Int) {
                onChange(id, delta)
                b.txtQuantity.text = quantityOf(id).toString()
            }
        }
    }

    private data class BillLine(val name: String, val imageUrl: String?, val price: Double, val quantity: Int)

    private class BillAdapter :
        ListAdapter<BillLine, BillAdapter.VH>(object : DiffUtil.ItemCallback<BillLine>() {
            override fun areItemsTheSame(a: BillLine, b: BillLine) = a.name == b.name
            override fun areContentsTheSame(a: BillLine, b: BillLine) = a == b
        }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemBillLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        class VH(private val b: ItemBillLineBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(line: BillLine) {
                val context = b.root.context
                b.imgProduct.loadImage(line.imageUrl)
                b.txtName.text = line.name
                b.txtDetail.text = context.getString(R.string.table_bill_detail, line.quantity, line.price.toPrice())
                b.txtSubtotal.text = (line.price * line.quantity).toPrice()
            }
        }
    }

    private class TableOrderAdapter :
        ListAdapter<OrderDto, TableOrderAdapter.VH>(object : DiffUtil.ItemCallback<OrderDto>() {
            override fun areItemsTheSame(a: OrderDto, b: OrderDto) = a.id == b.id
            override fun areContentsTheSame(a: OrderDto, b: OrderDto) = a == b
        }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemTableOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

        class VH(private val b: ItemTableOrderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(order: OrderDto) {
                val context = b.root.context
                b.txtTitle.text = context.getString(R.string.order_number_staff, order.id)
                b.txtStatus.text = context.getString(orderStatusLabel(order.status))
                b.txtStatus.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, orderStatusColor(order.status)))
                b.txtLines.text = order.items.joinToString("\n") {
                    context.getString(R.string.order_line, it.quantity, it.name)
                }
                b.txtTotal.text = order.total.toPrice()
                renderWait(order)
            }

            private fun renderWait(order: OrderDto) {
                val context = b.root.context
                val view = b.txtWait
                when (order.status) {
                    in OrderStatus.ACTIVOS -> {
                        val ms = (System.currentTimeMillis() - order.createdAt).coerceAtLeast(0L)
                        view.text = context.getString(R.string.wait_running, clock(ms))
                        val color = when {
                            ms / 60_000L >= 20 -> R.color.wait_late
                            ms / 60_000L >= 10 -> R.color.wait_warn
                            else -> R.color.wait_ok
                        }
                        view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
                        view.isVisible = true
                    }
                    OrderStatus.ENTREGADO -> {
                        val ms = (order.updatedAt - order.createdAt).coerceAtLeast(0L)
                        view.text = context.getString(R.string.wait_done, clock(ms))
                        view.backgroundTintList =
                            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_finalizado))
                        view.isVisible = true
                    }
                    else -> view.isVisible = false
                }
            }

            private fun clock(ms: Long): String {
                val total = ms / 1000L
                val h = total / 3600
                val m = (total % 3600) / 60
                val s = total % 60
                return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
            }
        }
    }

    companion object {
        private const val ARG_NUMBER = "number"
        private const val TICK = "tick"

        fun newInstance(number: Int) = TableDetailFragment().apply {
            arguments = Bundle().apply { putInt(ARG_NUMBER, number) }
        }
    }
}
