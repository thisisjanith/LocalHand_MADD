package com.example.localhand_new.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localhand_new.R
import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.Condition
import com.example.localhand_new.data.model.ListingType
import com.example.localhand_new.data.model.label
import com.example.localhand_new.databinding.FragmentCreateListingBinding
import com.example.localhand_new.ui.adapters.PhotoStripAdapter
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.CreateListingState
import com.example.localhand_new.ui.vm.CreateListingViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.example.localhand_new.ui.vm.MAX_LISTING_PHOTOS
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Shared by "New Listing" (from the FAB) and "Edit Listing" (from Profile's
 * My Listings, which calls [CreateListingViewModel.startEditing] before
 * navigating here) — both routes reuse the same form and ViewModel instance,
 * scoped to the Activity like the rest of the shared ViewModels.
 */
class CreateListingFragment : Fragment() {

    private var _binding: FragmentCreateListingBinding? = null
    private val binding get() = _binding!!

    private val vm: CreateListingViewModel by activityViewModels { LocalHandViewModelFactory }

    private var suppressLocalityWatcher = false
    private var suppressPriceWatcher = false
    private var lastShownSubmitError: String? = null

    private lateinit var photoAdapter: PhotoStripAdapter

    private val pickPhotosLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_LISTING_PHOTOS),
    ) { uris -> if (uris.isNotEmpty()) copyPhotosToAppStorage(uris) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreateListingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.screenHeader.headerTitle.text = "New Listing"
        binding.screenHeader.headerBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnPostListing.backgroundTintList =
            LhColorStateLists.buttonPrimaryBackground(requireContext())
        listOf(
            binding.tilTitle, binding.tilCategory, binding.tilPrice,
            binding.tilCondition, binding.tilLocality, binding.tilDescription,
        ).forEach { it.setBoxStrokeColorStateList(LhColorStateLists.fieldStroke(requireContext(), false)) }

        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            Category.entries.map { it.label },
        )
        binding.actvCategory.setAdapter(categoryAdapter)
        binding.actvCategory.setOnItemClickListener { _, _, position, _ ->
            vm.setCategory(Category.entries[position])
        }

        val conditionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            Condition.entries.map { it.label },
        )
        binding.actvCondition.setAdapter(conditionAdapter)
        binding.actvCondition.setOnItemClickListener { _, _, position, _ ->
            vm.setCondition(Condition.entries[position])
        }

        binding.toggleType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.btn_type_service) {
                ListingType.SERVICE
            } else {
                ListingType.MARKETPLACE
            }
            vm.setType(type)
        }

        binding.etTitle.addTextChangedListener { vm.setTitle(it?.toString().orEmpty()) }
        binding.etPrice.addTextChangedListener {
            if (!suppressPriceWatcher) vm.setPrice(it?.toString().orEmpty())
        }
        binding.etLocality.addTextChangedListener {
            if (!suppressLocalityWatcher) vm.setLocality(it?.toString().orEmpty())
        }
        binding.etDescription.addTextChangedListener { vm.setDescription(it?.toString().orEmpty()) }

        binding.mapPinPicker.onPinMoved = { vm.setPin(it.latitude, it.longitude) }

        photoAdapter = PhotoStripAdapter(
            onAddClick = { launchPhotoPicker() },
            onRemoveClick = { index -> vm.removePhotoAt(index) },
        )
        binding.rvPhotos.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPhotos.adapter = photoAdapter

        binding.btnPostListing.setOnClickListener {
            vm.submit {
                val title = vm.state.value.title.trim()
                vm.reset()
                findNavController().navigate(R.id.action_createListing_to_home)
                Snackbar.make(requireActivity().findViewById(android.R.id.content), "\"$title\" posted", Snackbar.LENGTH_LONG).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state -> render(state) }
            }
        }
    }

    private fun launchPhotoPicker() {
        val remaining = MAX_LISTING_PHOTOS - vm.state.value.photos.size
        if (remaining <= 0) return
        pickPhotosLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    private fun render(state: CreateListingState) {
        val serviceId = if (state.type == ListingType.SERVICE) {
            R.id.btn_type_service
        } else {
            R.id.btn_type_marketplace
        }
        if (binding.toggleType.checkedButtonId != serviceId) {
            binding.toggleType.check(serviceId)
        }

        if (binding.etTitle.text?.toString() != state.title) {
            binding.etTitle.setText(state.title)
        }

        binding.tilCategory.hint = "Category"
        val categoryText = state.category?.label ?: ""
        if (binding.actvCategory.text?.toString() != categoryText) {
            binding.actvCategory.setText(categoryText, false)
        }

        binding.tilPrice.hint = state.priceLabel
        binding.etPrice.hint = state.pricePlaceholder
        if (binding.etPrice.text?.toString() != state.price) {
            suppressPriceWatcher = true
            binding.etPrice.setText(state.price)
            suppressPriceWatcher = false
        }

        binding.tilCondition.visibility = if (state.showCondition) View.VISIBLE else View.GONE
        val conditionText = state.condition?.label ?: ""
        if (binding.actvCondition.text?.toString() != conditionText) {
            binding.actvCondition.setText(conditionText, false)
        }

        if (binding.etLocality.text?.toString() != state.locality) {
            suppressLocalityWatcher = true
            binding.etLocality.setText(state.locality)
            suppressLocalityWatcher = false
        }

        binding.mapPinPicker.center = state.pin()

        if (binding.etDescription.text?.toString() != state.description) {
            binding.etDescription.setText(state.description)
        }

        binding.tilTitle.error = state.errors[CreateListingState.Field.TITLE]
        binding.tilCategory.error = state.errors[CreateListingState.Field.CATEGORY]
        binding.tilPrice.error = state.errors[CreateListingState.Field.PRICE]
        binding.tilCondition.error = state.errors[CreateListingState.Field.CONDITION]
        binding.tilLocality.error = state.errors[CreateListingState.Field.LOCALITY]
        binding.tilDescription.error = state.errors[CreateListingState.Field.DESCRIPTION]

        photoAdapter.submit(state.photos, state.canAddMorePhotos)

        binding.btnPostListing.isEnabled = !state.isSubmitting
        if (state.submitError != null && state.submitError != lastShownSubmitError) {
            lastShownSubmitError = state.submitError
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                state.submitError,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * Copies every picked photo into the app's own storage so the listing
     * keeps working even if the originals are later deleted or their access
     * grant (implicit with the Photo Picker) is gone.
     */
    private fun copyPhotosToAppStorage(sources: List<Uri>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val destinations = withContext(Dispatchers.IO) {
                val dir = File(requireContext().filesDir, "listing_photos").apply { mkdirs() }
                sources.mapNotNull { source ->
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    requireContext().contentResolver.openInputStream(source)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file.takeIf { it.exists() }
                }
            }
            if (destinations.isNotEmpty()) vm.addPhotos(destinations.map { it.absolutePath })
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
