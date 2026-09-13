package com.example.localhand_new.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.localhand_new.R
import com.example.localhand_new.databinding.FragmentOnboardingBinding
import com.example.localhand_new.ui.adapters.OnboardingPagerAdapter
import com.example.localhand_new.ui.adapters.OnboardingPages
import com.example.localhand_new.ui.util.LhColorStateLists
import com.example.localhand_new.ui.vm.AuthViewModel
import com.example.localhand_new.ui.vm.LocalHandViewModelFactory

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val vm: AuthViewModel by activityViewModels { LocalHandViewModelFactory }

    private val dots = mutableListOf<View>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnNext.backgroundTintList = LhColorStateLists.buttonPrimaryBackground(requireContext())

        val adapter = OnboardingPagerAdapter()
        binding.pager.adapter = adapter

        buildDots()
        updateDots(0)
        updateControls(0)

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateControls(position)
            }
        })

        binding.btnSkip.setOnClickListener { finish() }
        binding.btnHaveAccount.setOnClickListener { finish() }
        binding.btnNext.setOnClickListener {
            val isLast = binding.pager.currentItem == OnboardingPages.lastIndex
            if (isLast) {
                finish()
            } else {
                binding.pager.currentItem += 1
            }
        }
    }

    private fun finish() {
        vm.completeOnboarding()
        findNavController().navigate(R.id.action_onboarding_to_login)
    }

    private fun buildDots() {
        val dotSize = resources.getDimensionPixelSize(R.dimen.lh_dot_size)
        val activeWidth = resources.getDimensionPixelSize(R.dimen.lh_dot_active_width)
        val margin = resources.getDimensionPixelSize(R.dimen.lh_gap_tiny)
        binding.dotIndicator.removeAllViews()
        dots.clear()
        OnboardingPages.forEachIndexed { index, _ ->
            val dot = View(requireContext()).apply {
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_pill_background)
            }
            val params = ViewGroup.MarginLayoutParams(dotSize, dotSize).apply {
                marginStart = margin
                marginEnd = margin
            }
            binding.dotIndicator.addView(dot, params)
            dots += dot
        }
    }

    /** Active page is a 20dp accent bar; the rest are 6dp dots. */
    private fun updateDots(current: Int) {
        val dotSize = resources.getDimensionPixelSize(R.dimen.lh_dot_size)
        val activeWidth = resources.getDimensionPixelSize(R.dimen.lh_dot_active_width)
        val accent = ContextCompat.getColor(requireContext(), R.color.lh_accent)
        val border = ContextCompat.getColor(requireContext(), R.color.lh_border)
        dots.forEachIndexed { index, dot ->
            val active = index == current
            val params = dot.layoutParams
            params.width = if (active) activeWidth else dotSize
            dot.layoutParams = params
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dotSize.toFloat()
                setColor(if (active) accent else border)
            }
        }
        binding.dotIndicator.requestLayout()
    }

    private fun updateControls(current: Int) {
        val isLast = current == OnboardingPages.lastIndex
        binding.btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
        binding.btnNext.text = if (isLast) "Get Started" else "Next"
        binding.btnHaveAccount.isEnabled = isLast
        binding.btnHaveAccount.alpha = if (isLast) 1f else 0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
