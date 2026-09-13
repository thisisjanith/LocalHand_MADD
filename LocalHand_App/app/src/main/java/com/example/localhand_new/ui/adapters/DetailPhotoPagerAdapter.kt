package com.example.localhand_new.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.localhand_new.databinding.ItemDetailPhotoBinding

/**
 * Backs the listing detail screen's photo carousel. [imageUrls] is the
 * listing's real photos; when empty, [fallbackRes] renders as the sole page
 * so the carousel always shows at least one image (no photo means the
 * category artwork, same as everywhere else in the app).
 */
class DetailPhotoPagerAdapter(
    private var imageUrls: List<String> = emptyList(),
    private var fallbackRes: Int = 0,
) : RecyclerView.Adapter<DetailPhotoPagerAdapter.ViewHolder>() {

    fun submit(imageUrls: List<String>, fallbackRes: Int) {
        this.imageUrls = imageUrls
        this.fallbackRes = fallbackRes
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = if (imageUrls.isEmpty()) 1 else imageUrls.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetailPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (imageUrls.isEmpty()) {
            holder.binding.ivPhoto.setImageResource(fallbackRes)
        } else {
            holder.binding.ivPhoto.load(imageUrls[position]) {
                placeholder(fallbackRes)
                error(fallbackRes)
            }
        }
    }

    class ViewHolder(val binding: ItemDetailPhotoBinding) : RecyclerView.ViewHolder(binding.root)
}
