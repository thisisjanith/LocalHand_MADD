package com.example.localhand_new.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.localhand_new.R
import com.example.localhand_new.data.location.formatDistance
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.data.model.label
import com.example.localhand_new.databinding.ItemListingRowBinding
import com.example.localhand_new.ui.util.setListingImage

/** A row plus the distance from the user, if known. */
data class ListingRowItem(
    val listing: Listing,
    val metres: Double? = null,
)

/**
 * Generic listing row used by Home/Search/Favourites/Profile: thumbnail,
 * type badge, category, title, price and a heart that saves in place — or,
 * in [TrailingMode.EDIT_DELETE] mode (Profile's "My Listings"), edit/delete
 * icons instead of the heart.
 */
class ListingRowAdapter(
    private val showLocality: Boolean = false,
    private val trailingMode: TrailingMode = TrailingMode.HEART,
    private val onClick: (Listing) -> Unit,
    private val onToggleFavourite: (Listing) -> Unit = {},
    private val onEdit: (Listing) -> Unit = {},
    private val onDelete: (Listing) -> Unit = {},
) : ListAdapter<ListingRowItem, ListingRowAdapter.ViewHolder>(Diff) {

    enum class TrailingMode { HEART, EDIT_DELETE }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListingRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), showLocality, trailingMode, onClick, onToggleFavourite, onEdit, onDelete)
    }

    class ViewHolder(private val binding: ItemListingRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ListingRowItem,
            showLocality: Boolean,
            trailingMode: TrailingMode,
            onClick: (Listing) -> Unit,
            onToggleFavourite: (Listing) -> Unit,
            onEdit: (Listing) -> Unit,
            onDelete: (Listing) -> Unit,
        ) {
            val listing = item.listing
            val context = binding.root.context

            binding.rowImage.setListingImage(listing)
            binding.rowTypeBadge.text = listing.type.label.uppercase()
            binding.rowTypeBadge.setBackgroundResource(
                if (listing.type == com.example.localhand_new.data.model.ListingType.SERVICE) {
                    R.drawable.bg_badge_accent
                } else {
                    R.drawable.bg_badge_secondary
                },
            )
            binding.rowTypeBadge.setTextColor(
                context.getColor(
                    if (listing.type == com.example.localhand_new.data.model.ListingType.SERVICE) {
                        R.color.lh_accent
                    } else {
                        R.color.lh_secondary
                    },
                ),
            )
            binding.rowCategory.text = listing.category.label
            binding.rowTitle.text = listing.title
            binding.rowPrice.text = listing.price
            binding.rowRating.text = listing.rating.toString()

            if (showLocality) {
                binding.rowLocalityGroup.visibility = View.VISIBLE
                binding.rowLocality.text = listing.locality
                if (item.metres != null) {
                    binding.rowDistance.visibility = View.VISIBLE
                    binding.rowDistance.text = formatDistance(item.metres)
                } else {
                    binding.rowDistance.visibility = View.GONE
                }
            } else {
                binding.rowLocalityGroup.visibility = View.GONE
                binding.rowDistance.visibility = View.GONE
            }

            when (trailingMode) {
                TrailingMode.HEART -> {
                    binding.rowTrailingHeartFrame.visibility = View.VISIBLE
                    binding.rowTrailingEditDelete.visibility = View.GONE
                    binding.rowFavouriteHeart.setImageResource(
                        if (listing.isFavourite) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    )
                    binding.rowFavouriteHeart.setColorFilter(
                        context.getColor(if (listing.isFavourite) R.color.lh_danger else R.color.lh_faint),
                    )
                    binding.rowFavouriteHeart.contentDescription =
                        if (listing.isFavourite) "Remove from favourites" else "Save to favourites"
                    binding.rowFavouriteHeart.setOnClickListener { onToggleFavourite(listing) }
                }
                TrailingMode.EDIT_DELETE -> {
                    binding.rowTrailingHeartFrame.visibility = View.GONE
                    binding.rowTrailingEditDelete.visibility = View.VISIBLE
                    binding.rowEdit.setOnClickListener { onEdit(listing) }
                    binding.rowDelete.setOnClickListener { onDelete(listing) }
                }
            }

            binding.root.setOnClickListener { onClick(listing) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ListingRowItem>() {
        override fun areItemsTheSame(oldItem: ListingRowItem, newItem: ListingRowItem) =
            oldItem.listing.id == newItem.listing.id

        override fun areContentsTheSame(oldItem: ListingRowItem, newItem: ListingRowItem) =
            oldItem == newItem
    }
}
