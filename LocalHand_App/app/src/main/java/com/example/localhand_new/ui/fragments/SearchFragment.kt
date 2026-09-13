package com.example.localhand_new.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localhand_new.R
import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.ListingType
import com.example.localhand_new.data.model.label
import com.example.localhand_new.databinding.FragmentSearchBinding
import com.example.localhand_new.ui.adapters.ListingRowAdapter
import com.example.localhand_new.ui.adapters.ListingRowItem
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.example.localhand_new.ui.vm.SearchViewModel
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val vm: SearchViewModel by viewModels { LocalHandViewModelFactory }
    private val args: SearchFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val resultsAdapter = ListingRowAdapter(
            showLocality = true,
            onClick = { navigateToListing(it.id) },
            onToggleFavourite = { vm.toggleFavourite(it) },
        )
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = resultsAdapter

        binding.tilQuery.setBoxStrokeColorStateList(
            LhColorStateLists.fieldStroke(requireContext(), false),
        )

        binding.etQuery.addTextChangedListener { vm.setQuery(it?.toString().orEmpty()) }

        buildTypeChips()
        buildCategoryChips()

        // A category tapped on Home carries into Search.
        val category = args.category?.let { runCatching { Category.valueOf(it) }.getOrNull() }
        vm.applyIncomingCategory(category)

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_search_off)
        binding.emptyState.emptyTitle.text = "Nothing matches yet"
        binding.emptyState.emptyMessage.text = "Try a different keyword, or widen your filters " +
            "by setting type or category back to All."

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    val filters = state.filters
                    syncChipSelection(binding.chipGroupType, filters.type?.name)
                    syncChipSelection(binding.chipGroupCategory, filters.category?.name)
                    if (binding.etQuery.text?.toString() != filters.query) {
                        binding.etQuery.setText(filters.query)
                    }

                    val results = state.results
                    binding.tvResultCount.text = when (results.size) {
                        1 -> "1 result"
                        else -> "${results.size} results"
                    }
                    resultsAdapter.submitList(
                        results.map { ListingRowItem(it.listing, it.metres) },
                    )
                    binding.emptyState.root.visibility =
                        if (results.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvResults.visibility =
                        if (results.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun buildTypeChips() {
        binding.chipGroupType.removeAllViews()
        addChip(binding.chipGroupType, "All", null) { vm.setType(null) }
        ListingType.entries.forEach { type ->
            addChip(binding.chipGroupType, type.label, type.name) { vm.setType(type) }
        }
    }

    private fun buildCategoryChips() {
        binding.chipGroupCategory.removeAllViews()
        addChip(binding.chipGroupCategory, "All", null) { vm.setCategory(null) }
        Category.entries.forEach { category ->
            addChip(binding.chipGroupCategory, category.label, category.name) {
                vm.setCategory(category)
            }
        }
    }

    private fun addChip(
        group: com.google.android.material.chip.ChipGroup,
        label: String,
        tag: String?,
        onSelect: () -> Unit,
    ) {
        val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
            text = label
            isCheckable = true
            this.tag = tag
            chipBackgroundColor = LhColorStateLists.chipBackground(requireContext())
            chipStrokeColor = LhColorStateLists.chipStroke(requireContext())
            chipStrokeWidth = resources.getDimension(R.dimen.lh_hairline)
            // setTextAppearance carries its own static textColor (Lh.Text.Label's
            // lh_muted), so it must run before setTextColor or it clobbers the
            // checked/unchecked ColorStateList applied below.
            setTextAppearance(R.style.Lh_Text_Label)
            setTextColor(LhColorStateLists.chipText(requireContext()))
            setOnCheckedChangeListener { button, isChecked ->
                if (isChecked) onSelect()
            }
        }
        group.addView(chip)
    }

    /** Reflects the ViewModel's filter state back onto the chip that matches it. */
    private fun syncChipSelection(group: com.google.android.material.chip.ChipGroup, tag: String?) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            val shouldBeChecked = chip.tag == tag
            if (chip.isChecked != shouldBeChecked) chip.isChecked = shouldBeChecked
        }
    }

    private fun navigateToListing(id: String) {
        findNavController().navigate(
            R.id.action_search_to_listingDetail,
            androidx.core.os.bundleOf("id" to id),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
