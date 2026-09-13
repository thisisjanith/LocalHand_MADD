package com.example.localhand_new.ui.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.localhand_new.R
import com.example.localhand_new.data.model.ChatMessage
import com.example.localhand_new.databinding.ItemMessageBubbleBinding

/** How far each dot rises before falling back, and how long one bounce takes. */
private const val TYPING_DOT_RISE_DP = 6f
private const val TYPING_DOT_DURATION_MS = 600L
private const val TYPING_DOT_STAGGER_MS = 150L

/**
 * Chat bubbles for the assistant. A single view type; gravity, background
 * shape and text colour all toggle on [ChatMessage.fromUser] rather than
 * using two view types, since the only differences are cosmetic. A typing
 * placeholder ([ChatMessage.isTyping]) swaps the text for three bouncing
 * dots in the same bubble shape instead of adding a second view type.
 */
class ChatAdapter(
    private val onListingClick: (String) -> Unit = {},
) : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBubbleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding, onListingClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.stopTypingAnimation()
    }

    class ViewHolder(
        private val binding: ItemMessageBubbleBinding,
        private val onListingClick: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var typingAnimator: AnimatorSet? = null

        fun bind(message: ChatMessage) {
            binding.bubbleRow.gravity = if (message.fromUser) Gravity.END else Gravity.START

            if (message.isTyping) {
                binding.bubbleText.visibility = View.GONE
                binding.bubbleTyping.visibility = View.VISIBLE
                binding.bubbleListingLink.visibility = View.GONE
                binding.bubbleListingLink.setOnClickListener(null)
                startTypingAnimation()
                return
            }

            stopTypingAnimation()
            binding.bubbleTyping.visibility = View.GONE
            binding.bubbleText.visibility = View.VISIBLE
            binding.bubbleText.text = message.text
            binding.bubbleText.setBackgroundResource(
                if (message.fromUser) R.drawable.bubble_user else R.drawable.bubble_assistant,
            )
            val context = binding.root.context
            binding.bubbleText.setTextColor(
                context.getColor(if (message.fromUser) R.color.lh_on_accent else R.color.lh_text),
            )

            val listingId = message.listingId
            if (listingId != null) {
                binding.bubbleListingLink.visibility = View.VISIBLE
                binding.bubbleListingLink.setOnClickListener { onListingClick(listingId) }
            } else {
                binding.bubbleListingLink.visibility = View.GONE
                binding.bubbleListingLink.setOnClickListener(null)
            }
        }

        private fun startTypingAnimation() {
            if (typingAnimator?.isRunning == true) return
            val density = binding.root.resources.displayMetrics.density
            val dots = listOf(binding.typingDot1, binding.typingDot2, binding.typingDot3)
            val bounces = dots.mapIndexed { index, dot ->
                ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, 0f, -TYPING_DOT_RISE_DP * density, 0f).apply {
                    duration = TYPING_DOT_DURATION_MS
                    startDelay = index * TYPING_DOT_STAGGER_MS
                    repeatCount = ValueAnimator.INFINITE
                }
            }
            typingAnimator = AnimatorSet().apply {
                playTogether(bounces)
                start()
            }
        }

        fun stopTypingAnimation() {
            typingAnimator?.cancel()
            typingAnimator = null
            listOf(binding.typingDot1, binding.typingDot2, binding.typingDot3).forEach { it.translationY = 0f }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
            oldItem == newItem
    }
}
