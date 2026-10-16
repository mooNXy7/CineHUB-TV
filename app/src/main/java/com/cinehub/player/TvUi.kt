package com.cinehub.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText

/**
 * Consistent high-contrast focus states for Android TV remotes and keyboards.
 * Avoids the platform-default blue highlight and keeps controls readable at a distance.
 */
object TvUi {
    private const val RED = 0xFFE50914.toInt()
    private const val DARK = 0xFF242424.toInt()
    private const val WHITE = Color.WHITE

    fun style(root: View) {
        when (root) {
            is Button -> styleButton(root)
            is EditText -> styleEditText(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) style(root.getChildAt(index))
        }
    }

    private fun styleButton(button: Button) {
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(RED)
            setStroke(3, WHITE)
        }
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(0xFFB80710.toInt())
            setStroke(2, RED)
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(DARK)
            setStroke(2, 0xFF555555.toInt())
        }
        button.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        button.setTextColor(WHITE)
        button.isAllCaps = false
        button.minHeight = maxOf(button.minHeight, (48 * button.resources.displayMetrics.density).toInt())
        button.setPadding(
            (16 * button.resources.displayMetrics.density).toInt(),
            button.paddingTop,
            (16 * button.resources.displayMetrics.density).toInt(),
            button.paddingBottom
        )
        button.stateListAnimator = null
    }

    private fun styleEditText(editText: EditText) {
        val density = editText.resources.displayMetrics.density
        editText.setTextColor(WHITE)
        editText.setHintTextColor(0xFFAAAAAA.toInt())
        editText.backgroundTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf()
            ),
            intArrayOf(RED, 0xFF777777.toInt())
        )
        editText.minHeight = maxOf(editText.minHeight, (48 * density).toInt())
    }
}
