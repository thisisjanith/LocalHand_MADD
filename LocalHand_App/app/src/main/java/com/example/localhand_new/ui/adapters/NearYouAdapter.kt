package com.example.localhand_new.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.localhand_new.R
import com.example.localhand_new.data.location.formatDistance
import com.example.localhand_new.data.repo.NearbyListing
import com.example.localhand_new.databinding.ItemNearYouCardBinding
import com.example.localhand_new.ui.util.setListingImage

/** Home's "Near You" horizontal band: image, two-line title, locality/distance, price, rating. */
class NearYouAdapter(
    private val onClick: (NearbyListing) -> Unit,
) : ListAdapter<NearbyListing, NearYouAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNearYouCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(private val binding: ItemNearYouCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NearbyListing, onClick: (NearbyListing) -> Unit) {
            val listing = item.listing
            binding.cardImage.setListingImage(listing)
            binding.cardTitle.text = listing.title
            binding.cardLocality.text = item.metres?.let(::formatDistance) ?: listing.locality
            binding.cardPrice.text = listing.price
            binding.cardRating.text = listing.rating.toString()
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<NearbyListing>() {
        override fun areItemsTheSame(oldItem: NearbyListing, newItem: NearbyListing) =
            oldItem.listing.id == newItem.listing.id

        override fun areContentsTheSame(oldItem: NearbyListing, newItem: NearbyListing) =
            oldItem == newItem
    }
}
