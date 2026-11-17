package com.bremnercamilourbano.apprestaurante.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bremnercamilourbano.apprestaurante.R
import com.bremnercamilourbano.apprestaurante.data.Roles
import com.bremnercamilourbano.apprestaurante.data.StaffUserDto
import com.bremnercamilourbano.apprestaurante.databinding.DialogStaffFormBinding
import com.bremnercamilourbano.apprestaurante.databinding.FragmentStaffUsersBinding
import com.bremnercamilourbano.apprestaurante.databinding.ItemStaffUserBinding
import com.bremnercamilourbano.apprestaurante.viewmodel.AdminViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Administrador: crea las cuentas del personal (cajero, cocina, administrador) y las activa o desactiva. */
class StaffUsersFragment : Fragment(R.layout.fragment_staff_users) {

    private val viewModel: AdminViewModel by activityViewModels()
    private var _binding: FragmentStaffUsersBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStaffUsersBinding.bind(view)

        val adapter = StaffUserAdapter { askToggle(it) }
        binding.recyclerStaff.adapter = adapter
        binding.btnAddStaff.setOnClickListener { showForm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.staff.collect {
                        adapter.submitList(it)
                        binding.txtEmpty.isVisible = it.isEmpty()
                    }
                }
                launch { viewModel.refreshStaff() }
            }
        }
    }

    private fun askToggle(user: StaffUserDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(user.name)
            .setMessage(user.email)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (user.active) R.string.staff_users_deactivate else R.string.staff_users_activate) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch { viewModel.setStaffActive(user, !user.active) }
            }
            .apply {
                // Cocina y caja se pueden eliminar; las cuentas de administrador solo se desactivan.
                if (user.role != Roles.ADMIN) {
                    setNeutralButton(R.string.staff_users_delete) { _, _ ->
                        AuthDialogs.confirmDeleteStaff(requireActivity() as androidx.appcompat.app.AppCompatActivity, user.name) {
                            viewLifecycleOwner.lifecycleScope.launch { viewModel.deleteStaff(user) }
                        }
                    }
                }
            }
            .show()
    }

    private fun showForm() {
        val form = DialogStaffFormBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.staff_users_form_title)
            .setView(form.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = form.inputName.text?.toString().orEmpty().trim()
                val email = form.inputEmail.text?.toString().orEmpty().trim()
                val password = form.inputPassword.text?.toString().orEmpty()
                val role = when (form.radioRole.checkedRadioButtonId) {
                    R.id.radioCocina -> Roles.COCINA
                    R.id.radioAdmin -> Roles.ADMIN
                    else -> Roles.CAJERO
                }
                val nameOk = name.length >= 2
                val emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches()
                val passwordOk = password.length >= 8
                form.layoutName.error = if (nameOk) null else getString(R.string.profile_error_name)
                form.layoutEmail.error = if (emailOk) null else getString(R.string.profile_error_email)
                form.layoutPassword.error = if (passwordOk) null else getString(R.string.profile_error_password)
                if (nameOk && emailOk && passwordOk) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (viewModel.createStaff(name, email, password, role)) dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        binding.recyclerStaff.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

private object StaffUserDiff : DiffUtil.ItemCallback<StaffUserDto>() {
    override fun areItemsTheSame(oldItem: StaffUserDto, newItem: StaffUserDto) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: StaffUserDto, newItem: StaffUserDto) = oldItem == newItem
}

private class StaffUserAdapter(
    private val onClick: (StaffUserDto) -> Unit
) : ListAdapter<StaffUserDto, StaffUserAdapter.ViewHolder>(StaffUserDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemStaffUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemStaffUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: StaffUserDto) {
            val context = binding.root.context
            val roleLabel = context.getString(
                when (user.role) {
                    Roles.ADMIN -> R.string.staff_users_role_admin
                    Roles.COCINA -> R.string.staff_users_role_cocina
                    else -> R.string.staff_users_role_cajero
                }
            )
            binding.txtName.text = user.name
            binding.txtEmail.text = context.getString(R.string.staff_users_line, user.email, roleLabel)
            binding.txtState.setText(if (user.active) R.string.staff_users_active else R.string.staff_users_inactive)
            val color = if (user.active) R.color.status_finalizado else R.color.table_inactive
            binding.txtState.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, color))
            binding.root.setOnClickListener { onClick(user) }
        }
    }
}
