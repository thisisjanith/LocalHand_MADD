package com.example.localhand_new.ui.util

import androidx.annotation.ColorRes
import com.example.localhand_new.R
import com.example.localhand_new.data.model.Category

/** Solid icon tint for a category's tile/badge. */
@ColorRes
fun categoryColorRes(category: Category): Int = when (category) {
    Category.REPAIRS -> R.color.lh_cat_repairs
    Category.TUTORING -> R.color.lh_cat_tutoring
    Category.CLEANING -> R.color.lh_cat_cleaning
    Category.GARDEN -> R.color.lh_cat_garden
    Category.ERRANDS -> R.color.lh_cat_errands
    Category.ELECTRONICS -> R.color.lh_cat_electronics
    Category.FURNITURE -> R.color.lh_cat_furniture
    Category.OTHER -> R.color.lh_cat_other
}

/** Faint wash background a category's icon sits on. */
@ColorRes
fun categoryWashColorRes(category: Category): Int = when (category) {
    Category.REPAIRS -> R.color.lh_cat_repairs_wash
    Category.TUTORING -> R.color.lh_cat_tutoring_wash
    Category.CLEANING -> R.color.lh_cat_cleaning_wash
    Category.GARDEN -> R.color.lh_cat_garden_wash
    Category.ERRANDS -> R.color.lh_cat_errands_wash
    Category.ELECTRONICS -> R.color.lh_cat_electronics_wash
    Category.FURNITURE -> R.color.lh_cat_furniture_wash
    Category.OTHER -> R.color.lh_cat_other_wash
}
