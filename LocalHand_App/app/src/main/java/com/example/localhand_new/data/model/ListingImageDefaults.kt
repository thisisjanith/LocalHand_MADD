package com.example.localhand_new.data.model

import com.example.localhand_new.R

/**
 * Fallback artwork for a listing with no uploaded photo, keyed by category.
 * Relocated from the now-deleted data/local/SeedData.kt (which existed only
 * to seed the local Room database — that role is gone now that listings come
 * from the backend, but this drawable mapping is still needed client-side).
 */
fun defaultImageFor(category: Category): Int = when (category) {
    Category.REPAIRS -> R.drawable.listing_electrical
    Category.TUTORING -> R.drawable.listing_tuition
    Category.CLEANING -> R.drawable.listing_cleaning
    Category.GARDEN -> R.drawable.listing_garden
    Category.ERRANDS -> R.drawable.listing_errands
    Category.ELECTRONICS -> R.drawable.listing_fridge
    Category.FURNITURE -> R.drawable.listing_sofa
    Category.OTHER -> R.drawable.listing_books
}
