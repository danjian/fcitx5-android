/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.setup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.databinding.ActivitySetupBinding
import org.fcitx.fcitx5.android.ui.setup.SetupPage.Companion.firstUndonePage
import org.fcitx.fcitx5.android.ui.setup.SetupPage.Companion.isLastPage
import org.fcitx.fcitx5.android.utils.notificationManager

class SetupActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2

    private val viewModel: SetupViewModel by viewModels()

    private lateinit var skipButton: Button
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先检查是否需要显示设置页面
        if (!shouldShowUp()) {
            finish()
            return
        }

        enableEdgeToEdge()
        val binding = ActivitySetupBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            windowInsets
        }
        setContentView(binding.root)

        skipButton = binding.skipButton.apply {
            text = getString(R.string.skip)
            setOnClickListener { finish() }
        }
        prevButton = binding.prevButton.apply {
            text = getString(R.string.prev)
            setOnClickListener { viewPager.currentItem = viewPager.currentItem - 1 }
        }
        nextButton = binding.nextButton.apply {
            setOnClickListener {
                val currentItem = viewPager.currentItem
                val isLastPage = currentItem.isLastPage()

                if (!isLastPage) {
                    viewPager.currentItem = currentItem + 1
                } else {
                    // 如果是最后一页，检查是否所有设置都已完成
                    if (viewModel.isAllDone.value == true) {
                        finish()
                    } else {
                        // 如果还有未完成的设置，提示用户或直接完成
                        finish()
                    }
                }
            }
        }

        viewPager = binding.viewpager.apply {
            adapter = Adapter()
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateButtonState(position)
                }
            })
        }

        viewModel.isAllDone.observe(this) { allDone ->
            updateButtonState(viewPager.currentItem, allDone)
        }

        // 延迟初始化页面，确保 UI 完全加载
        Handler(Looper.getMainLooper()).post {
            val undonePage = firstUndonePage()
            if (undonePage != null) {
                viewPager.setCurrentItem(undonePage.ordinal, false)
            }
            // 初始化按钮状态
            updateButtonState(viewPager.currentItem, viewModel.isAllDone.value)
        }

        shown = true
        createNotificationChannel()
    }

    private fun updateButtonState(position: Int, allDone: Boolean? = null) {
        val isDone = allDone ?: viewModel.isAllDone.value ?: false
        val isLast = position.isLastPage()

        // 更新上一个按钮
        prevButton.visibility = if (position != 0) View.VISIBLE else View.GONE

        // 更新跳过按钮
        skipButton.visibility = if (isDone) View.GONE else View.VISIBLE

        // 更新下一个/完成按钮
        nextButton.apply {
            visibility = View.VISIBLE
            text = getString(
                when {
                    isLast && isDone -> R.string.done
                    isLast -> R.string.done // 即使未完成，最后一页也显示完成
                    else -> R.string.next
                }
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            supportFragmentManager.fragments.forEach {
                if (it.isVisible) (it as? SetupFragment)?.sync()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getText(R.string.setup_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = CHANNEL_ID }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onPause() {
        if (SetupPage.hasUndonePage()) {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_keyboard_24)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.setup_keyboard))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, javaClass).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                .setAutoCancel(true)
                .build()
                .let { notificationManager.notify(NOTIFY_ID, it) }
        }
        super.onPause()
    }

    override fun onResume() {
        notificationManager.cancel(NOTIFY_ID)
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 只在正常完成时才将 shown 设为 true
        // 如果是配置变化导致的销毁，不要重置 shown
        if (!isChangingConfigurations) {
            shown = false
        }
    }

    private inner class Adapter : FragmentStateAdapter(this) {
        override fun getItemCount(): Int = SetupPage.entries.size

        override fun createFragment(position: Int): Fragment =
            SetupFragment().apply {
                arguments = bundleOf(SetupFragment.PAGE to SetupPage.valueOf(position))
            }
    }

    companion object {
        private var shown = false
        private const val CHANNEL_ID = "setup"
        private const val NOTIFY_ID = 233

        fun shouldShowUp(): Boolean {
            // 添加调试日志
            val hasUndonePage = SetupPage.hasUndonePage()
            val shouldShow = !shown && hasUndonePage

            // 如果不需要显示，重置 shown 状态以便下次检查
            if (!shouldShow) {
                shown = false
            }

            return shouldShow
        }

        // 提供重置方法，供外部调用
        fun resetShownState() {
            shown = false
        }
    }
}