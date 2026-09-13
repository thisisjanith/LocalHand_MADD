package com.example.localhand_new.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.localhand_new.R
import com.example.localhand_new.databinding.FragmentAssistantBinding
import com.example.localhand_new.databinding.ItemSuggestionChipBinding
import com.example.localhand_new.ui.adapters.ChatAdapter
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.AssistantViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory
import com.example.localhand_new.ui.vm.SuggestedPrompts
import kotlinx.coroutines.launch

class AssistantFragment : Fragment() {

    private var _binding: FragmentAssistantBinding? = null
    private val binding get() = _binding!!

    private val vm: AssistantViewModel by viewModels { LocalHandViewModelFactory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAssistantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.tilInput.setBoxStrokeColorStateList(
            LhColorStateLists.fieldStroke(requireContext(), false),
        )

        val adapter = ChatAdapter(
            onListingClick = { listingId ->
                findNavController().navigate(
                    R.id.action_assistant_to_listingDetail,
                    bundleOf("id" to listingId),
                )
            },
        )
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter

        buildSuggestionRow()

        fun send() {
            val text = binding.etInput.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                vm.send(text)
                binding.etInput.setText("")
            }
        }
        binding.btnSend.setOnClickListener { send() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.messages.collect { messages ->
                        adapter.submitList(messages) {
                            // Keep the newest message in view as the conversation grows.
                            if (messages.isNotEmpty()) {
                                binding.rvMessages.scrollToPosition(messages.lastIndex)
                            }
                        }
                    }
                }
                launch {
                    vm.isSending.collect { sending ->
                        binding.btnSend.isEnabled = !sending
                        binding.etInput.isEnabled = !sending
                    }
                }
            }
        }
    }

    private fun buildSuggestionRow() {
        binding.suggestionRow.removeAllViews()
        SuggestedPrompts.forEach { prompt ->
            val chipBinding = ItemSuggestionChipBinding.inflate(
                layoutInflater, binding.suggestionRow, false,
            )
            chipBinding.chipText.text = prompt
            chipBinding.chipText.setOnClickListener { vm.send(prompt) }
            binding.suggestionRow.addView(chipBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
