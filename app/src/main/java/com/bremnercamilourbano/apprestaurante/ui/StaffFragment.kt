package com.bremnercamilourbano.apprestaurante.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.Session
import com.bremnercamilourbano.apprestaurante.databinding.FragmentStaffBinding
import com.bremnercamilourbano.apprestaurante.util.applyStatusBarPadding
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * Sección del personal. Sin sesión muestra el acceso por clave.
 * Con sesión, las pestañas dependen del rol (cocina, cajero o administrador).
 */
class StaffFragment : Fragment(R.layout.fragment_staff) {

    private val viewModel: RestaurantViewModel by activityViewModels()
    private var _binding: FragmentStaffBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStaffBinding.bind(view)
        binding.header.applyStatusBarPadding()

        binding.btnLogin.setOnClickListener { tryLogin() }
        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryLogin()
                true
            } else {
                false
            }
        }
        binding.btnLogout.setOnClickListener { viewModel.logout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.session.collect { render(it) } }
                launch {
                    viewModel.loginError.collect { error ->
                        binding.layoutPassword.error = error?.let { getString(it.res) }
                    }
                }
                launch { viewModel.loginBusy.collect { binding.btnLogin.isEnabled = !it } }
            }
        }
    }

    private fun tryLogin() {
        viewModel.login(binding.inputPassword.text?.toString().orEmpty())
    }

    private fun render(session: Session?) {
        val logged = session != null
        binding.loginGroup.isVisible = !logged
        binding.staffGroup.isVisible = logged
        binding.btnLogout.isVisible = logged
        binding.txtRole.isVisible = logged

        if (session == null) {
            binding.inputPassword.text?.clear()
            childFragmentManager.findFragmentById(R.id.staffContainer)?.let { child ->
                childFragmentManager.commit { remove(child) }
            }
            return
        }

        binding.txtRole.setText(
            when {
                session.isAdmin -> R.string.staff_subtitle_admin
                session.isCajero -> R.string.staff_subtitle_cajero
                else -> R.string.staff_subtitle_cocina
            }
        )
        setupTabs(session)
    }

    /**
     * Cocina: Pedidos. Cajero: Jornada (abrir/cerrar caja), Mesas (cuentas y facturar) y Pedidos.
     * Administrador: Jornadas (historial), Productos, Historial y Mesas (crear/editar).
     */
    private fun setupTabs(session: Session) {
        val tabs = buildList {
            when {
                session.isAdmin -> {
                    add(TAG_JORNADA to R.string.staff_tab_jornadas)
                    add(TAG_PRODUCTS to R.string.staff_tab_products)
                    add(TAG_HISTORY to R.string.staff_tab_history)
                    add(TAG_TABLES to R.string.staff_tab_tables)
                    add(TAG_USERS to R.string.staff_tab_users)
                }
                session.isCajero -> {
                    add(TAG_JORNADA to R.string.staff_tab_jornada)
                    add(TAG_TABLES to R.string.staff_tab_tables)
                    add(TAG_ORDERS to R.string.staff_tab_orders)
                }
                else -> add(TAG_ORDERS to R.string.staff_tab_orders)
            }
        }
        // Si ya hay una pestaña abierta (por ejemplo al girar la pantalla) se conserva.
        val currentTag = childFragmentManager.findFragmentById(R.id.staffContainer)?.tag
        val index = tabs.indexOfFirst { it.first == currentTag }.coerceAtLeast(0)

        binding.tabs.clearOnTabSelectedListeners()
        binding.tabs.removeAllTabs()
        tabs.forEach { (tag, label) ->
            binding.tabs.addTab(binding.tabs.newTab().setText(label).setTag(tag), false)
        }
        binding.tabs.getTabAt(index)?.select()
        showChild(tabs[index].first)

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.tag as? String)?.let { showChild(it) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun showChild(tag: String) {
        if (childFragmentManager.findFragmentById(R.id.staffContainer)?.tag == tag) return
        val fragment: Fragment = when (tag) {
            TAG_TABLES -> TablesFragment()
            TAG_JORNADA -> JornadaFragment()
            TAG_USERS -> StaffUsersFragment()
            TAG_PRODUCTS -> AdminProductsFragment()
            TAG_HISTORY -> HistoryFragment()
            else -> KitchenFragment()
        }
        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.staffContainer, fragment, tag)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG_ORDERS = "orders"
        const val TAG_TABLES = "tables"
        const val TAG_JORNADA = "jornada"
        const val TAG_USERS = "users"
        const val TAG_PRODUCTS = "products"
        const val TAG_HISTORY = "history"
    }
}
