package com.example.localhand_new.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localhand_new.R
import com.example.localhand_new.databinding.FragmentFavouritesBinding
import com.example.localhand_new.ui.adapters.ListingRowAdapter
import com.example.localhand_new.ui.adapters.ListingRowItem
import com.example.localhand_new.ui.vm.FavouritesViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import kotlinx.coroutines.launch

/**
 * Reached from Profile with a back arrow, not a bottom-nav tab. The heart
 * stays active on each row so saved items can be removed in place.
 */
class FavouritesFragment : Fragment() {

    private var _binding: FragmentFavouritesBinding? = null
    private val binding get() = _binding!!

    private val vm: FavouritesViewModel by viewModels { LocalHandViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFavouritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.screenHeader.headerTitle.text = "Favourites"
        binding.screenHeader.headerBack.setOnClickListener { findNavController().popBackStack() }

        val adapter = ListingRowAdapter(
            showLocality = true,
            onClick = { navigateToListing(it.id) },
            onToggleFavourite = { vm.toggleFavourite(it) },
        )
        binding.rvFavourites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavourites.adapter = adapter

        binding.emptyState.emptyIcon.setImageResource(R.drawable.ic_favorite_border)
        binding.emptyState.emptyTitle.text = "Nothing saved yet"
        binding.emptyState.emptyMessage.text =
            "Tap the heart on any listing to keep it here for later."

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    val list = state.favourites
                    adapter.submitList(list.map { ListingRowItem(it) })
                    binding.emptyState.root.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvFavourites.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun navigateToListing(id: String) {
        findNavController().navigate(
            R.id.action_favourites_to_listingDetail,
            bundleOf("id" to id),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
