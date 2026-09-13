package com.example.localhand_new.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.localhand_new.databinding.ItemAddPhotoTileBinding
import com.example.localhand_new.databinding.ItemListingPhotoBinding
import com.example.localhand_new.ui.vm.ListingPhoto
import java.io.File

private const val VIEW_TYPE_PHOTO = 0
private const val VIEW_TYPE_ADD_TILE = 1

/**
 * Horizontal strip for Create/Edit Listing: one thumbnail per staged photo
 * (local or already-hosted), each removable, plus a trailing "add" tile
 * while under the cap. Not a ListAdapter — [photos] and [showAddTile]
 * change together on every edit, so a plain notifyDataSetChanged is simpler
 * than diffing two conceptually different item kinds.
 */
class PhotoStripAdapter(
    private val onAddClick: () -> Unit,
    private val onRemoveClick: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var photos: List<ListingPhoto> = emptyList()
    private var showAddTile: Boolean = true

    fun submit(photos: List<ListingPhoto>, showAddTile: Boolean) {
        this.photos = photos
        this.showAddTile = showAddTile
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = photos.size + if (showAddTile) 1 else 0

    override fun getItemViewType(position: Int): Int =
        if (position < photos.size) VIEW_TYPE_PHOTO else VIEW_TYPE_ADD_TILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_PHOTO) {
            PhotoViewHolder(ItemListingPhotoBinding.inflate(inflater, parent, false))
        } else {
            AddTileViewHolder(ItemAddPhotoTileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PhotoViewHolder -> holder.bind(photos[position], position)
            is AddTileViewHolder -> holder.bind()
        }
    }

    inner class PhotoViewHolder(private val binding: ItemListingPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: ListingPhoto, position: Int) {
            val source: Any = when (photo) {
                is ListingPhoto.Local -> File(photo.path).toUri()
                is ListingPhoto.Remote -> photo.url
            }
            binding.ivThumb.load(source)
            binding.btnRemove.setOnClickListener { onRemoveClick(position) }
        }
    }

    inner class AddTileViewHolder(private val binding: ItemAddPhotoTileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.tileRoot.setOnClickListener { onAddClick() }
        }
    }
}
