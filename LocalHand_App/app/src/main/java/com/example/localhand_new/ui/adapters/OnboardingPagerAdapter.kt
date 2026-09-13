package com.example.localhand_new.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.localhand_new.R
import com.example.localhand_new.databinding.ItemOnboardingPageBinding

data class OnboardingPage(
    val iconRes: Int,
    val headline: String,
    val body: String,
)

val OnboardingPages = listOf(
    OnboardingPage(
        iconRes = R.drawable.ic_home,
        headline = "Everything you need,\nright in your area",
        body = "LocalHand connects you with trusted neighbours offering services " +
            "and second-hand goods — all within walking distance.",
    ),
    OnboardingPage(
        iconRes = R.drawable.ic_add_business,
        headline = "Hire help or\nsell what you don't need",
        body = "Book an electrician, a tutor or a cleaner in a few taps — or list " +
            "your own service and spare items for the neighbourhood.",
    ),
    OnboardingPage(
        iconRes = R.drawable.ic_contact_support,
        headline = "Ask the assistant\nanything, anytime",
        body = "Not sure who to hire or how to post? The built-in AI assistant " +
            "finds the right neighbour and walks you through it.",
    ),
)

class OnboardingPagerAdapter : RecyclerView.Adapter<OnboardingPagerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = OnboardingPages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(OnboardingPages[position])
    }

    class ViewHolder(private val binding: ItemOnboardingPageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(page: OnboardingPage) {
            binding.pageIcon.setImageResource(page.iconRes)
            binding.pageHeadline.text = page.headline
            binding.pageBody.text = page.body
        }
    }
}
