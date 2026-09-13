package com.example.localhand_new.ui.fragments

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.localhand_new.R
import com.example.localhand_new.data.location.LatLng
import com.example.localhand_new.databinding.FragmentSignupBinding
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.example.localhand_new.ui.vm.SignUpViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SignUpFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    private val vm: SignUpViewModel by activityViewModels { LocalHandViewModelFactory }

    /** Where the map opens before a fix arrives — Malabe, where the sample listings are. */
    private val defaultPin = LatLng(6.9061, 79.9701)

    private var fieldErrors: Map<String, String> = emptyMap()
    private var lastShownSubmitError: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) vm.locateMe() else vm.permissionDenied()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.screenHeader.headerTitle.text = ""
        binding.screenHeader.headerBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnCreateAccount.backgroundTintList =
            LhColorStateLists.buttonPrimaryBackground(requireContext())
        listOf(binding.tilName, binding.tilEmail, binding.tilPhone, binding.tilPassword, binding.tilArea).forEach {
            it.setBoxStrokeColorStateList(LhColorStateLists.fieldStroke(requireContext(), false))
        }

        binding.mapPinPicker.onPinMoved = { vm.movePin(it) }
        binding.mapPinPicker.onUseMyLocation = { requestLocation() }

        binding.etArea.setText(vm.state.value.areaName)
        binding.etArea.addTextChangedListener { vm.setAreaName(it?.toString().orEmpty()) }

        binding.btnLogIn.setOnClickListener { findNavController().popBackStack() }

        binding.btnCreateAccount.setOnClickListener { submit() }

        // Ask once on arrival: the pin drives every "near you" ranking, so
        // getting it set with no effort is worth the up-front prompt.
        if (savedInstanceState == null) requestLocation()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    binding.mapPinPicker.center = state.pin ?: defaultPin
                    binding.mapPinPicker.isLocating = state.isLocating

                    // Don't clobber the user's in-progress typing.
                    if (binding.etArea.text?.toString() != state.areaName) {
                        binding.etArea.setText(state.areaName)
                    }

                    val pinMessage = state.locationError ?: fieldErrors["pin"]
                    binding.tvPinError.text = pinMessage
                    binding.tvPinError.visibility = if (pinMessage != null) View.VISIBLE else View.GONE

                    binding.btnCreateAccount.isEnabled = !state.isSubmitting
                    if (state.submitError != null && state.submitError != lastShownSubmitError) {
                        lastShownSubmitError = state.submitError
                        Snackbar.make(
                            requireActivity().findViewById(android.R.id.content),
                            state.submitError,
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun requestLocation() {
        if (vm.hasLocationPermission()) {
            vm.locateMe()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    private fun submit() {
        val name = binding.etName.text?.toString().orEmpty()
        val email = binding.etEmail.text?.toString().orEmpty()
        val phone = binding.etPhone.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val state = vm.state.value

        val errors = buildMap {
            if (name.isBlank()) put("name", "Enter your full name")
            if (email.isBlank()) put("email", "Enter your email address")
            if (phone.isBlank()) put("phone", "Enter your phone number")
            if (password.isBlank()) put("password", "Choose a password")
            if (state.pin == null) put("pin", "Drop a pin to set your area")
            if (state.areaName.isBlank()) put("area", "Name your area")
        }
        fieldErrors = errors

        binding.tilName.error = errors["name"]
        binding.tilEmail.error = errors["email"]
        binding.tilPhone.error = errors["phone"]
        binding.tilPassword.error = errors["password"]
        binding.tilArea.error = errors["area"]
        val pinMessage = state.locationError ?: errors["pin"]
        binding.tvPinError.text = pinMessage
        binding.tvPinError.visibility = if (pinMessage != null) View.VISIBLE else View.GONE

        if (errors.isEmpty()) {
            vm.signUp(name.trim(), email.trim(), password, phone.trim()) {
                findNavController().navigate(R.id.action_signup_to_home)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapPinPicker?.onResume()
    }

    override fun onPause() {
        _binding?.mapPinPicker?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.mapPinPicker.onDetach()
        super.onDestroyView()
        _binding = null
    }
}
