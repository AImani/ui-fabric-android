/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.officeuifabric.snackbar

import android.support.annotation.DrawableRes
import android.support.design.widget.BaseTransientBottomBar
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.microsoft.officeuifabric.R
import com.microsoft.officeuifabric.R.id.*
import kotlinx.android.synthetic.main.view_snackbar.view.*

/**
 * Snackbars provide lightweight feedback about an operation by showing a brief message at the bottom of the screen.
 * [Snackbar] can contain a custom action or use a style geared towards making special announcements to your users
 * in addition to custom text and duration.
 * To use a snackbar with a FAB, it is recommended that your parent layout be a CoordinatorLayout.
 */
class Snackbar : BaseTransientBottomBar<Snackbar> {
    companion object {
        const val LENGTH_INDEFINITE: Int = BaseTransientBottomBar.LENGTH_INDEFINITE
        const val LENGTH_SHORT: Int = BaseTransientBottomBar.LENGTH_SHORT
        const val LENGTH_LONG: Int = BaseTransientBottomBar.LENGTH_LONG

        fun make(parent: ViewGroup, text: CharSequence, duration: Int, style: Style = Style.REGULAR): Snackbar {
            val content = LayoutInflater.from(parent.context).inflate(R.layout.view_snackbar, parent, false)
            val snackbar = Snackbar(parent, content, ContentViewCallback(content))
            snackbar.duration = duration
            snackbar.style = style
            snackbar.setText(text)
            return snackbar
        }
    }

    /**
     * Defines which style is applied to the Snackbar.
     * Includes background color, text color, and action button placement.
     */
    enum class Style {
        REGULAR, ANNOUNCEMENT
    }

    var style: Style = Style.REGULAR
        set(value) {
            if (field == value)
                return
            field = value
            updateStyle()
        }

    private val textView: TextView = view.snackbar_text
    private val actionButtonView: AppCompatButton = view.snackbar_action
    private val iconImageView: ImageView = view.snackbar_icon

    private constructor(parent: ViewGroup, content: View, contentViewCallback: ContentViewCallback) : super(parent, content, contentViewCallback) {
        updateBackground()
    }

    fun setText(text: CharSequence): Snackbar {
        textView.text = text
        updateStyle()
        return this
    }

    fun setAction(text: CharSequence, listener: View.OnClickListener): Snackbar {
        actionButtonView.text = text
        actionButtonView.visibility = View.VISIBLE
        actionButtonView.setOnClickListener { view ->
            listener.onClick(view)
            dismiss()
        }

        updateStyle()

        return this
    }

    fun setIcon(@DrawableRes iconResourceId: Int): Snackbar {
        iconImageView.setImageDrawable(ContextCompat.getDrawable(context, iconResourceId))
        iconImageView.visibility = View.VISIBLE
        return this
    }

    private fun updateBackground() {
        when (style) {
            Style.REGULAR -> view.background = ContextCompat.getDrawable(context, R.drawable.snackbar_background)
            Style.ANNOUNCEMENT -> view.background = ContextCompat.getDrawable(context, R.drawable.snackbar_background_announcement)
        }
    }

    private fun updateStyle() {
        updateBackground()
        layoutTextAndActionButton()

        when (style) {
            Style.REGULAR -> {
                actionButtonView.setTextColor(ContextCompat.getColor(context, R.color.uifabric_snackbar_action_text))
            }
            Style.ANNOUNCEMENT -> {
                actionButtonView.setTextColor(ContextCompat.getColor(context, R.color.uifabric_snackbar_action_text_announcement))
            }
        }
    }

    private fun layoutTextAndActionButton() {
        val inset = context.resources.getDimension(R.dimen.uifabric_snackbar_content_inset).toInt()
        val textLayoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        val buttonLayoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)

        val textWidth = actionButtonView.paint.measureText(actionButtonView.text.toString())
        if (textWidth > context.resources.getDimension(R.dimen.uifabric_snackbar_action_text_wrapping_width) || style == Style.ANNOUNCEMENT) {
            // Action button moves to the bottom of the root view
            textLayoutParams.removeRule(RelativeLayout.START_OF)
            textLayoutParams.marginEnd = inset
            buttonLayoutParams.addRule(RelativeLayout.BELOW, snackbar_text)
            actionButtonView.setPaddingRelative(inset, inset, inset, inset)
        } else {
            // Action button moves to the end of the text view
            textLayoutParams.addRule(RelativeLayout.START_OF, snackbar_action)
            textLayoutParams.bottomMargin = inset
            if (actionButtonView.text.isNullOrEmpty())
                textLayoutParams.marginEnd = inset
            buttonLayoutParams.removeRule(RelativeLayout.BELOW)
            actionButtonView.setPaddingRelative(context.resources.getDimension(R.dimen.uifabric_snackbar_action_spacing).toInt(), inset, inset, inset)
        }

        textLayoutParams.addRule(RelativeLayout.END_OF, snackbar_icon)
        textLayoutParams.alignWithParent = true
        textLayoutParams.marginStart = inset
        textLayoutParams.topMargin = inset
        textView.layoutParams = textLayoutParams

        buttonLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
        buttonLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_END)
        actionButtonView.layoutParams = buttonLayoutParams
    }

    private class ContentViewCallback(private val content: View) : BaseTransientBottomBar.ContentViewCallback {
        override fun animateContentIn(delay: Int, duration: Int) {
            // These animations are from the Android snackbar
            content.snackbar_text.alpha = 0f
            content.snackbar_text.animate().alpha(1f).setDuration(duration.toLong()).setStartDelay(delay.toLong()).start()

            if (content.snackbar_action.visibility == View.VISIBLE) {
                content.snackbar_action.alpha = 0f
                content.snackbar_action.animate().alpha(1f).setDuration(duration.toLong()).setStartDelay(delay.toLong()).start()
            }
        }

        override fun animateContentOut(delay: Int, duration: Int) {
            // These animations are from the Android snackbar
            content.snackbar_text.alpha = 1f
            content.snackbar_text.animate().alpha(0f).setDuration(duration.toLong()).setStartDelay(delay.toLong()).start()

            if (content.snackbar_action.visibility == View.VISIBLE) {
                content.snackbar_action.alpha = 1f
                content.snackbar_action.animate().alpha(0f).setDuration(duration.toLong()).setStartDelay(delay.toLong()).start()
            }
        }
    }
}