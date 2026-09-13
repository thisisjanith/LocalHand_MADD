package com.example.localhand_new.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.localhand_new.R
import com.example.localhand_new.databinding.FragmentLoginBinding
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.AuthViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.example.localhand_new.ui.vm.LoginUiState
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val vm: AuthViewModel by activityViewModels { LocalHandViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.backgroundTintList = LhColorStateLists.buttonPrimaryBackground(requireContext())
        listOf(binding.tilEmail, binding.tilPassword).forEach {
            it.setBoxStrokeColorStateList(LhColorStateLists.fieldStroke(requireContext(), false))
        }

        binding.etEmail.addTextChangedListener { binding.tvError.visibility = View.GONE }
        binding.etPassword.addTextChangedListener { binding.tvError.visibility = View.GONE }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()

            val error = when {
                email.isBlank() && password.isBlank() ->
                    "Enter your email and password to continue"
                email.isBlank() -> "Enter your email address"
                password.isBlank() -> "Enter your password"
                else -> null
            }

            if (error != null) {
                binding.tvError.text = error
                binding.tvError.visibility = View.VISIBLE
            } else {
                binding.tvError.visibility = View.GONE
                vm.logIn(email.trim(), password) {
                    findNavController().navigate(R.id.action_login_to_home)
                }
            }
        }

        binding.btnSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signup)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.loginState.collect { state ->
                    binding.btnLogin.isEnabled = state !is LoginUiState.Loading
                    if (state is LoginUiState.Error) {
                        binding.tvError.text = state.message
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
