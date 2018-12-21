/**
 * Copyright © 2018 Microsoft Corporation. All rights reserved.
 */

package com.microsoft.officeuifabricdemo.demos

import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import com.microsoft.officeuifabric.persona.AvatarSize
import com.microsoft.officeuifabric.persona.AvatarView
import com.microsoft.officeuifabricdemo.DemoActivity
import com.microsoft.officeuifabricdemo.R
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_avatar_view.*

class AvatarViewActivity : DemoActivity() {
    override val contentLayoutId: Int
        get() = R.layout.activity_avatar_view

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Avatar drawables with bitmap
        loadBitmapFromPicasso(avatar_example_picasso)
        loadBitmapFromGlide(avatar_example_glide)
        avatar_example_local.avatarImageResourceId = R.drawable.avatar_erik_nason

        // Avatar drawable with initials
        avatar_example_initials.name = getString(R.string.persona_name_kat_larsson)
        avatar_example_initials.email = getString(R.string.persona_email_kat_larsson)

        // Add AvatarView with code
        createNewAvatarFromCode()
    }

    private fun loadBitmapFromPicasso(imageView: ImageView) {
        Picasso.get()
            .load(R.drawable.avatar_celeste_burton)
            .into(imageView)
    }

    private fun loadBitmapFromGlide(imageView: ImageView) {
        Glide.with(this)
            .load(R.drawable.avatar_isaac_fielder)
            .into(imageView)
    }

    private fun createNewAvatarFromCode() {
        val avatarView = AvatarView(this)
        avatarView.avatarSize = AvatarSize.XXLARGE
        avatarView.name = getString(R.string.persona_name_mauricio_august)
        avatarView.email = getString(R.string.persona_email_mauricio_august)
        avatarView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        avatarView.id = R.id.avatar_example_code
        avatar_layout.addView(avatarView)
    }
}