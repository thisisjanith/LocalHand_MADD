package com.example.localhand_new.ui.util

import android.widget.ImageView
import coil.load
import com.example.localhand_new.data.model.Listing

/**
 * Loads the poster's uploaded photo ([Listing.imageUrl], a remote R2 URL) when
 * present, otherwise falls back to the category artwork ([Listing.imageRes]).
 */
fun ImageView.setListingImage(listing: Listing) {
    if (listing.imageUrl != null) {
        load(listing.imageUrl) {
            placeholder(listing.imageRes)
            error(listing.imageRes)
        }
    } else {
        setImageResource(listing.imageRes)
    }
}
