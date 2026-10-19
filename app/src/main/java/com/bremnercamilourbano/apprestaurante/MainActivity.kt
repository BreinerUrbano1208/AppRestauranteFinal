package com.bremnercamilourbano.apprestaurante

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bremnercamilourbano.apprestaurante.databinding.ActivityMainBinding
import com.bremnercamilourbano.apprestaurante.ui.AdminFragment
import com.bremnercamilourbano.apprestaurante.ui.CartFragment
import com.bremnercamilourbano.apprestaurante.ui.DetailFragment
import com.bremnercamilourbano.apprestaurante.ui.MenuFragment
import com.bremnercamilourbano.apprestaurante.viewmodel.RestaurantViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RestaurantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

        if (savedInstanceState == null) showTab(R.id.nav_menu)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.cartCount.collect { count ->
                        if (count > 0) {
                            binding.bottomNav.getOrCreateBadge(R.id.nav_cart).number = count
                        } else {
                            binding.bottomNav.removeBadge(R.id.nav_cart)
                        }
                    }
                }
                launch {
                    viewModel.messages.collect { message ->
                        Snackbar.make(
                            binding.main,
                            getString(message.res, *message.args.toTypedArray()),
                            Snackbar.LENGTH_SHORT
                        ).setAnchorView(binding.bottomNav).show()
                    }
                }
            }
        }
    }

    private fun showTab(itemId: Int) {
        val fragment = when (itemId) {
            R.id.nav_cart -> CartFragment()
            R.id.nav_admin -> AdminFragment()
            else -> MenuFragment()
        }
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, fragment)
        }
    }

    fun openDetail(productId: Int) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragmentContainer, DetailFragment.newInstance(productId))
            addToBackStack(null)
        }
    }

    fun goToMenu() {
        binding.bottomNav.selectedItemId = R.id.nav_menu
    }
}
