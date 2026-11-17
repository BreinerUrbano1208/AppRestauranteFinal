package com.bremnercamilourbano.apprestaurante

import android.graphics.Color
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.databinding.ActivityMainBinding
import com.bremnercamilourbano.apprestaurante.push.Push
import com.bremnercamilourbano.apprestaurante.ui.CartFragment
import com.bremnercamilourbano.apprestaurante.ui.DrawerDialogs
import com.bremnercamilourbano.apprestaurante.ui.EditProfileFragment
import com.bremnercamilourbano.apprestaurante.util.Toasts
import com.bremnercamilourbano.apprestaurante.util.loadAvatar
import com.bremnercamilourbano.apprestaurante.ui.OrderHistoryFragment
import com.bremnercamilourbano.apprestaurante.ui.DetailFragment
import com.bremnercamilourbano.apprestaurante.ui.MenuFragment
import com.bremnercamilourbano.apprestaurante.ui.MyOrderFragment
import com.bremnercamilourbano.apprestaurante.ui.ProfileFragment
import com.bremnercamilourbano.apprestaurante.util.bounce
import com.bremnercamilourbano.apprestaurante.util.popIn
import android.widget.TextView
import com.bremnercamilourbano.apprestaurante.ui.StaffFragment
import com.bremnercamilourbano.apprestaurante.data.ClientStore
import com.bremnercamilourbano.apprestaurante.data.SessionStore
import com.bremnercamilourbano.apprestaurante.viewmodel.AccountViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.AdminViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.OrdersViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.bremnercamilourbano.apprestaurante.viewmodel.UiMessage
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RestaurantViewModel by viewModels()
    private val ordersViewModel: OrdersViewModel by viewModels()
    private val adminViewModel: AdminViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { Push.register(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClientStore.init(this)
        SessionStore.init(this)
        Push.createChannels(this)
        // Los íconos de las barras de estado y de navegación se adaptan al modo claro u oscuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, 0, bars.right, 0)
            insets
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener {
            supportFragmentManager.popBackStack()
        }
        setupDrawer()

        // Si la pantalla se recrea (rotar), Android ya restauró los fragmentos: no se vuelve a empezar.
        if (savedInstanceState != null) {
            mode = currentMode()
            binding.bottomNav.isVisible = mode == Mode.CLIENT
            binding.drawerLayout.setDrawerLockMode(
                if (mode == Mode.CLIENT) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.cartCount.collect { count ->
                        if (count > 0) {
                            binding.bottomNav.getOrCreateBadge(R.id.nav_cart).number = count
                            binding.bottomNav.findViewById<View>(R.id.nav_cart)?.bounce()
                        } else {
                            binding.bottomNav.removeBadge(R.id.nav_cart)
                        }
                    }
                }
                // El login decide qué pantalla ve cada persona: login, tienda (cliente) o trabajo (personal).
                launch {
                    combine(SessionStore.session, ClientStore.session) { staff, client -> staff to client }
                        .collect {
                            applyMode()
                            updateDrawerHeader()
                            Push.onSessionChanged(this@MainActivity)
                            askNotificationPermission()
                        }
                }
                launch { viewModel.messages.collect { showMessage(it) } }
                launch { ordersViewModel.messages.collect { showMessage(it) } }
                launch { adminViewModel.messages.collect { showMessage(it) } }
                launch { accountViewModel.messages.collect { showMessage(it) } }
            }
        }
    }

    private enum class Mode { LOGIN, CLIENT, STAFF }

    private var mode: Mode? = null

    private fun currentMode() = when {
        SessionStore.session.value != null -> Mode.STAFF
        ClientStore.session.value != null -> Mode.CLIENT
        else -> Mode.LOGIN
    }

    private fun applyMode() {
        val newMode = currentMode()
        if (newMode == mode) return
        mode = newMode
        binding.bottomNav.isVisible = newMode == Mode.CLIENT
        binding.drawerLayout.setDrawerLockMode(
            if (newMode == Mode.CLIENT) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        )
        binding.drawerLayout.closeDrawers()
        when (newMode) {
            Mode.STAFF -> showRoot(StaffFragment())
            Mode.CLIENT -> {
                binding.bottomNav.menu.findItem(R.id.nav_menu).isChecked = true
                showTab(R.id.nav_menu)
            }
            Mode.LOGIN -> {
                ordersViewModel.clearMyOrder() // el siguiente que entre no ve el pedido del anterior
                showRoot(ProfileFragment())
            }
        }
    }

    private fun showRoot(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            replace(R.id.fragmentContainer, fragment)
        }
    }

    private fun showTab(itemId: Int) {
        val fragment = when (itemId) {
            R.id.nav_cart -> CartFragment()
            R.id.nav_my_order -> MyOrderFragment()
            else -> MenuFragment()
        }
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(R.anim.tab_enter, R.anim.tab_exit)
            replace(R.id.fragmentContainer, fragment)
        }
    }

    fun openDetail(productId: Int) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(R.anim.slide_in_right, R.anim.fade_out, R.anim.fade_in, R.anim.slide_out_right)
            replace(R.id.fragmentContainer, DetailFragment.newInstance(productId))
            addToBackStack(null)
        }
    }

    fun goToMenu() {
        binding.bottomNav.selectedItemId = R.id.nav_menu
    }

    fun goToProfile() = openDrawer()

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun openEditProfile() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(R.anim.slide_in_right, R.anim.fade_out, R.anim.fade_in, R.anim.slide_out_right)
            replace(R.id.fragmentContainer, EditProfileFragment())
            addToBackStack(null)
        }
    }

    fun openOrderHistory() {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            setCustomAnimations(R.anim.slide_in_right, R.anim.fade_out, R.anim.fade_in, R.anim.slide_out_right)
            replace(R.id.fragmentContainer, OrderHistoryFragment())
            addToBackStack(null)
        }
    }

    private fun setupDrawer() {
        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.drawer_orders -> openOrderHistory()
                R.id.drawer_edit_profile -> openEditProfile()
                R.id.drawer_restaurant -> DrawerDialogs.restaurantInfo(this)
                R.id.drawer_settings -> DrawerDialogs.settings(this)
                R.id.drawer_logout -> DrawerDialogs.confirmLogout(this) { accountViewModel.logout() }
            }
            false
        }
        // El avatar aparece con un pequeño rebote cada vez que se abre el menú.
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                binding.navView.getHeaderView(0)?.findViewById<View>(R.id.txtAvatar)?.popIn(duration = 450L)
            }
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun updateDrawerHeader() {
        val client = ClientStore.session.value ?: return
        val header = binding.navView.getHeaderView(0) ?: return
        header.findViewById<TextView>(R.id.txtAvatar).text =
            client.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val photoView = header.findViewById<android.widget.ImageView>(R.id.imgAvatar)
        photoView.isVisible = client.photo != null
        photoView.loadAvatar(client.photo)
        header.findViewById<TextView>(R.id.txtDrawerName).text =
            listOfNotNull(client.name, client.lastName).joinToString(" ")
        header.findViewById<TextView>(R.id.txtDrawerEmail).text = client.email
    }

    /** Android 13+ pide permiso para mostrar notificaciones. Se pide una vez al tener sesión. */
    private fun askNotificationPermission() {
        val loggedIn = SessionStore.session.value != null || ClientStore.session.value != null
        if (loggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Push.hasPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun goToMyOrder() {
        binding.bottomNav.selectedItemId = R.id.nav_my_order
    }

    private fun showMessage(message: UiMessage) {
        Toasts.show(binding.main, message.res, message.args, anchor = binding.bottomNav)
    }
}
