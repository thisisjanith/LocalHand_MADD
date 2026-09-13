package com.example.localhand_new.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localhand_new.LocalHandApp
import com.example.localhand_new.R
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.databinding.FragmentProfileBinding
import com.example.localhand_new.ui.adapters.ListingRowAdapter
import com.example.localhand_new.ui.adapters.ListingRowItem
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.CreateListingViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.example.localhand_new.ui.vm.ProfileViewModel
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val vm: ProfileViewModel by viewModels { LocalHandViewModelFactory }
    private val createVm: CreateListingViewModel by activityViewModels { LocalHandViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.statListings.tileLabel.text = "Listings"
        binding.statFavourites.tileLabel.text = "Favourites"
        binding.statRating.tileLabel.text = "Rating"

        val myListingsAdapter = ListingRowAdapter(
            trailingMode = ListingRowAdapter.TrailingMode.EDIT_DELETE,
            onClick = { navigateToListing(it.id) },
            onEdit = { listing ->
                createVm.startEditing(listing)
                findNavController().navigate(R.id.action_profile_to_createListing)
            },
            onDelete = { vm.delete(it) },
        )
        binding.rvMyListings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMyListings.adapter = myListingsAdapter

        binding.cardFavourites.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_favourites)
        }

        binding.cardSignOut.setOnClickListener {
            vm.signOut()
            findNavController().navigate(R.id.action_profile_to_login)
        }

        binding.switchDarkMode.thumbTintList = LhColorStateLists.switchThumb(requireContext())
        binding.switchDarkMode.trackTintList = LhColorStateLists.switchTrack(requireContext())

        binding.switchDarkMode.setOnCheckedChangeListener { switchView, isChecked ->
            if (!switchView.isPressed) return@setOnCheckedChangeListener
            vm.setDarkMode(isChecked)
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    state.user?.let { user ->
                        binding.tvAvatarInitial.text = user.name.firstOrNull()?.uppercase() ?: "?"
                        binding.tvName.text = user.name
                        binding.tvLocalitySince.text = "${user.locality} · member since ${user.memberSince}"
                        binding.statRating.tileValue.text = user.rating.toString()
                    }

                    val list = state.myListings
                    binding.statListings.tileValue.text = list.size.toString()
                    binding.tvNoListings.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    myListingsAdapter.submitList(list.map { ListingRowItem(it) })

                    binding.statFavourites.tileValue.text = state.favouriteCount.toString()
                    binding.tvFavouriteCount.text = "${state.favouriteCount} saved"

                    val isDark = state.darkMode ?: resources.configuration.isNightModeActive
                    if (binding.switchDarkMode.isChecked != isDark) {
                        binding.switchDarkMode.isChecked = isDark
                    }
                }
            }
        }
    }

    private fun navigateToListing(id: String) {
        findNavController().navigate(
            R.id.action_profile_to_listingDetail,
            bundleOf("id" to id),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
