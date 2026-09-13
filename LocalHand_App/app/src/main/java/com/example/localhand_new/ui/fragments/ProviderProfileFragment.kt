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
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localhand_new.R
import com.example.localhand_new.databinding.FragmentProviderProfileBinding
import com.example.localhand_new.ui.adapters.ListingRowAdapter
import com.example.localhand_new.ui.adapters.ListingRowItem
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.example.localhand_new.ui.vm.ProviderProfileViewModel
import kotlinx.coroutines.launch

/** Another user's public profile, reached by tapping a listing's provider row. */
class ProviderProfileFragment : Fragment() {

    private var _binding: FragmentProviderProfileBinding? = null
    private val binding get() = _binding!!

    private val vm: ProviderProfileViewModel by viewModels { LocalHandViewModelFactory }
    private val args: ProviderProfileFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProviderProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.statListings.tileLabel.text = "Listings"
        binding.statRating.tileLabel.text = "Rating"

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        val listingsAdapter = ListingRowAdapter(
            showLocality = true,
            onClick = { navigateToListing(it.id) },
            onToggleFavourite = { vm.toggleFavourite(it) },
        )
        binding.rvListings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvListings.adapter = listingsAdapter

        vm.load(args.userId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    state.profile?.let { profile ->
                        binding.tvAvatarInitial.text = profile.name.firstOrNull()?.uppercase() ?: "?"
                        binding.tvName.text = profile.name
                        binding.tvLocalitySince.text =
                            "${profile.locality} · member since ${profile.memberSince}"
                        binding.statRating.tileValue.text = profile.rating.toString()
                    }

                    val list = state.listings
                    binding.statListings.tileValue.text = list.size.toString()
                    binding.tvNoListings.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    listingsAdapter.submitList(list.map { ListingRowItem(it) })
                }
            }
        }
    }

    private fun navigateToListing(id: String) {
        findNavController().navigate(
            R.id.action_providerProfile_to_listingDetail,
            bundleOf("id" to id),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
