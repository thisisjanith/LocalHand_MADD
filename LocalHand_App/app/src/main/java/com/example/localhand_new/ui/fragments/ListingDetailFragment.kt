package com.example.localhand_new.ui.fragments

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.example.localhand_new.R
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.data.model.label
import com.example.localhand_new.databinding.FragmentListingDetailBinding
import com.example.localhand_new.ui.adapters.DetailPhotoPagerAdapter
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.ListingDetailViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import kotlinx.coroutines.launch

class ListingDetailFragment : Fragment() {

    private var _binding: FragmentListingDetailBinding? = null
    private val binding get() = _binding!!

    private val vm: ListingDetailViewModel by viewModels { LocalHandViewModelFactory }
    private val args: ListingDetailFragmentArgs by navArgs()

    private val photoAdapter = DetailPhotoPagerAdapter()
    private val photoDots = mutableListOf<View>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentListingDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.load(args.id)

        binding.btnWhatsapp.backgroundTintList =
            LhColorStateLists.buttonPrimaryBackground(requireContext())

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.photoPager.adapter = photoAdapter
        binding.photoPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updatePhotoDots(position)
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    state.listing?.let { bind(it) }
                }
            }
        }
    }

    private fun bind(listing: Listing) {
        photoAdapter.submit(listing.imageUrls, listing.imageRes)
        buildPhotoDots(listing.imageUrls.size.coerceAtLeast(1))
        binding.tvTypeBadge.text = listing.type.label.uppercase()
        binding.tvTypeBadge.setBackgroundResource(
            if (listing.type == com.example.localhand_new.data.model.ListingType.SERVICE) {
                R.drawable.bg_badge_accent
            } else {
                R.drawable.bg_badge_secondary
            },
        )
        binding.tvTypeBadge.setTextColor(
            requireContext().getColor(
                if (listing.type == com.example.localhand_new.data.model.ListingType.SERVICE) {
                    R.color.lh_accent
                } else {
                    R.color.lh_secondary
                },
            ),
        )
        binding.tvCategory.text = listing.category.label
        binding.tvTitle.text = listing.title
        binding.tvProviderInitial.text = listing.providerName.firstOrNull()?.uppercase() ?: "?"
        binding.tvProviderName.text = listing.providerName
        binding.tvRating.text = listing.rating.toString()
        binding.tvPrice.text = listing.price
        binding.tvLocality.text = listing.locality
        binding.tvDescription.text = listing.description

        if (listing.condition != null) {
            binding.tvCondition.visibility = View.VISIBLE
            binding.tvCondition.text = listing.condition.label
        } else {
            binding.tvCondition.visibility = View.GONE
        }

        binding.btnFavourite.setImageResource(
            if (listing.isFavourite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
        )
        binding.btnFavourite.setColorFilter(
            requireContext().getColor(if (listing.isFavourite) R.color.lh_danger else R.color.lh_faint),
        )
        binding.btnFavourite.setOnClickListener { vm.toggleFavourite(listing) }

        binding.btnCall.setOnClickListener { requireContext().dial(listing.providerPhone) }
        binding.btnWhatsapp.setOnClickListener {
            requireContext().openWhatsApp(listing.providerPhone, listing.title)
        }

        binding.rowProvider.setOnClickListener {
            findNavController().navigate(
                R.id.action_listingDetail_to_providerProfile,
                androidx.core.os.bundleOf("userId" to listing.providerId),
            )
        }
    }

    /** Dots only make sense with more than one photo; a single photo shows none. */
    private fun buildPhotoDots(count: Int) {
        binding.photoDotIndicator.removeAllViews()
        photoDots.clear()
        if (count <= 1) return

        val dotSize = resources.getDimensionPixelSize(R.dimen.lh_dot_size)
        val margin = resources.getDimensionPixelSize(R.dimen.lh_gap_tiny)
        repeat(count) {
            val dot = View(requireContext())
            val params = ViewGroup.MarginLayoutParams(dotSize, dotSize).apply {
                marginStart = margin
                marginEnd = margin
            }
            binding.photoDotIndicator.addView(dot, params)
            photoDots += dot
        }
        updatePhotoDots(0)
    }

    /** Active page is a 20dp accent bar; the rest are 6dp dots — matches onboarding. */
    private fun updatePhotoDots(current: Int) {
        val dotSize = resources.getDimensionPixelSize(R.dimen.lh_dot_size)
        val activeWidth = resources.getDimensionPixelSize(R.dimen.lh_dot_active_width)
        val accent = ContextCompat.getColor(requireContext(), R.color.lh_accent)
        val surface = ContextCompat.getColor(requireContext(), R.color.lh_surface)
        photoDots.forEachIndexed { index, dot ->
            val active = index == current
            val params = dot.layoutParams
            params.width = if (active) activeWidth else dotSize
            dot.layoutParams = params
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dotSize.toFloat()
                setColor(if (active) accent else surface)
            }
        }
        binding.photoDotIndicator.requestLayout()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private fun Context.dial(phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No dialler available", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.openWhatsApp(phone: String, subject: String) {
    // wa.me expects the number without a leading "+" or separators.
    val number = phone.filter { it.isDigit() }
    val text = Uri.encode("Hi, I saw your \"$subject\" listing on LocalHand.")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$text"))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
    }
}
