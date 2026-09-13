package com.example.localhand_new.ui.util

import androidx.annotation.DrawableRes
import com.example.localhand_new.R
import com.example.localhand_new.data.model.Category

/**
 * Category icons are literal to their subject: wrench, graduation cap, spray
 * bottle, plant, cart, monitor, sofa, ellipsis. Ports ui/components/CategoryIcons.kt.
 */
@DrawableRes
fun categoryIconRes(category: Category): Int = when (category) {
    Category.REPAIRS -> R.drawable.ic_build
    Category.TUTORING -> R.drawable.ic_school
    Category.CLEANING -> R.drawable.ic_cleaning
    Category.GARDEN -> R.drawable.ic_local_florist
    Category.ERRANDS -> R.drawable.ic_shopping_cart
    Category.ELECTRONICS -> R.drawable.ic_monitor
    Category.FURNITURE -> R.drawable.ic_chair
    Category.OTHER -> R.drawable.ic_more_horiz
}
