package com.example.localhand_new.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localhand_new.R
import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.label
import com.example.localhand_new.databinding.FragmentHomeBinding
import com.example.localhand_new.databinding.ViewCategoryIconsBinding
import com.example.localhand_new.ui.adapters.ListingRowAdapter
import com.example.localhand_new.ui.adapters.ListingRowItem
import com.example.localhand_new.ui.adapters.NearYouAdapter
import com.example.localhand_new.ui.util.categoryColorRes
import com.example.localhand_new.ui.util.categoryIconRes
import com.example.localhand_new.ui.util.categoryWashColorRes
import com.example.localhand_new.ui.vm.HomeUiState
import com.example.localhand_new.ui.vm.HomeViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by viewModels { LocalHandViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nearYouAdapter = NearYouAdapter { nearby ->
            navigateToListing(nearby.listing.id)
        }
        binding.rvNearYou.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvNearYou.adapter = nearYouAdapter

        val recentAdapter = ListingRowAdapter(
            onClick = { navigateToListing(it.id) },
            onToggleFavourite = { vm.toggleFavourite(it) },
        )
        binding.rvRecent.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecent.adapter = recentAdapter

        binding.searchAffordance.setOnClickListener { navigateToSearch(null) }
        binding.avatarFrame.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        buildCategoryGrid()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state -> render(state, nearYouAdapter, recentAdapter) }
            }
        }
    }

    private fun render(
        state: HomeUiState,
        nearYouAdapter: NearYouAdapter,
        recentAdapter: ListingRowAdapter,
    ) {
        state.user?.let { user ->
            binding.tvLocality.text = user.locality
            binding.tvAvatarInitial.text = user.name.firstOrNull()?.uppercase() ?: "?"
        }
        nearYouAdapter.submitList(state.nearYou)
        recentAdapter.submitList(state.recent.map { ListingRowItem(it) })

        if (state.error != null) {
            Snackbar.make(binding.root, state.error, Snackbar.LENGTH_LONG).show()
            vm.errorShown()
        }
    }

    private fun buildCategoryGrid() {
        binding.categoryGrid.removeAllViews()
        Category.entries.chunked(4).forEach { row ->
            val rowLayout = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.lh_gap_large)
                }
            }
            row.forEach { category ->
                val tileBinding = ViewCategoryIconsBinding.inflate(
                    layoutInflater, rowLayout, false,
                )
                tileBinding.tileIcon.setImageResource(categoryIconRes(category))
                tileBinding.tileIcon.setColorFilter(
                    ContextCompat.getColor(requireContext(), categoryColorRes(category)),
                )
                tileBinding.tileIconFrame.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = resources.getDimension(R.dimen.lh_radius_thumb)
                    setColor(ContextCompat.getColor(requireContext(), categoryWashColorRes(category)))
                }
                tileBinding.tileLabel.text = category.label
                tileBinding.root.setOnClickListener { navigateToSearch(category) }
                rowLayout.addView(tileBinding.root)
            }
            binding.categoryGrid.addView(rowLayout)
        }
    }

    private fun navigateToListing(id: String) {
        findNavController().navigate(
            R.id.action_home_to_listingDetail,
            bundleOf("id" to id),
        )
    }

    private fun navigateToSearch(category: Category?) {
        findNavController().navigate(
            R.id.action_home_to_search,
            bundleOf("category" to category?.name),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
