package com.example.localhand_new.ui.util

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.example.localhand_new.R

/**
 * Built in code rather than as res/color XML selectors: on this project's
 * target emulator image (API 37 preview), the platform's ColorStateList XML
 * inflater fails on every selector color resource regardless of content,
 * resolving to Android's "unresolved resource" magenta sentinel at runtime
 * even though the compiled resource is verified correct at every build
 * stage. Constructing the ColorStateList directly sidesteps that XML-parsing
 * path entirely.
 */
object LhColorStateLists {

    private fun color(context: Context, resId: Int) = ContextCompat.getColor(context, resId)

    /** Primary button fill: accent, dimmed to 40% alpha when disabled. */
    fun buttonPrimaryBackground(context: Context): ColorStateList {
        val accent = color(context, R.color.lh_accent)
        val disabled = (accent and 0x00FFFFFF) or (((0xFF * 0.4).toInt()) shl 24)
        return ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(),
            ),
            intArrayOf(disabled, accent),
        )
    }

    /** Bottom-nav icon/label tint: accent when checked, muted otherwise. */
    fun navIconTint(context: Context): ColorStateList {
        val accent = color(context, R.color.lh_accent)
        val muted = color(context, R.color.lh_muted)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(accent, muted),
        )
    }

    /** Filter chip background: solid accent fill when checked, surface otherwise. */
    fun chipBackground(context: Context): ColorStateList {
        val checked = color(context, R.color.lh_accent)
        val unchecked = color(context, R.color.lh_surface)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(checked, unchecked),
        )
    }

    /** Filter chip stroke: accent when checked, border otherwise. */
    fun chipStroke(context: Context): ColorStateList {
        val checked = color(context, R.color.lh_accent)
        val unchecked = color(context, R.color.lh_border)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(checked, unchecked),
        )
    }

    /** Filter chip text: white on the solid accent fill when checked, muted otherwise. */
    fun chipText(context: Context): ColorStateList {
        val checked = color(context, R.color.lh_on_accent)
        val unchecked = color(context, R.color.lh_muted)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(checked, unchecked),
        )
    }

    /** Switch thumb: white when checked, surface otherwise. */
    fun switchThumb(context: Context): ColorStateList {
        val checked = color(context, R.color.lh_on_accent)
        val unchecked = color(context, R.color.lh_surface)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(checked, unchecked),
        )
    }

    /** Switch track: accent when checked, border otherwise. */
    fun switchTrack(context: Context): ColorStateList {
        val checked = color(context, R.color.lh_accent)
        val unchecked = color(context, R.color.lh_border)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(checked, unchecked),
        )
    }

    /** Outlined text field box stroke: danger/accent/border, in priority order. */
    fun fieldStroke(context: Context, isError: Boolean): ColorStateList {
        val danger = color(context, R.color.lh_danger)
        val accent = color(context, R.color.lh_accent)
        val border = color(context, R.color.lh_border)
        return if (isError) {
            ColorStateList.valueOf(danger)
        } else {
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf(),
                ),
                intArrayOf(accent, border),
            )
        }
    }
}
