/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.officeuifabric.persona

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import com.microsoft.officeuifabric.R
import java.util.*

/**
 * This is a custom [RecyclerView] with a set adapter and layoutManager. It provides an interface for the list data and onItemClickedListener and
 * adds a custom [DividerItemDecoration] to each row.
 */
class PersonaListView : RecyclerView {
    /**
     * [personas] contains the collection of Personas that the adapter binds to the ViewHolder.
     */
    var personas = ArrayList<IPersona>()
        set(value) {
            field = value
            personaListAdapter.personas = value
        }

    /**
     * This onItemClickedListener is called when a [PersonaView] cell is clicked.
     */
    var onItemClickedListener: OnItemClickedListener? = null
        set(value) {
            field = value
            personaListAdapter.onItemClickedListener = value
        }

    private val personaListAdapter = PersonaListAdapter(context)

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        adapter = personaListAdapter
        layoutManager = LinearLayoutManager(context)
        addCustomDivider((layoutManager as LinearLayoutManager).orientation)
    }

    private fun addCustomDivider(orientation: Int) {
        val dividerItemDecoration = DividerItemDecoration(context, orientation)
        val spacing = PersonaView.getSpacing(context, AvatarSize.LARGE)
        val insetDrawable = InsetDrawable(ContextCompat.getDrawable(context, R.drawable.ms_row_divider), spacing.insetLeft, 0, spacing.cellPadding, 0)
        dividerItemDecoration.setDrawable(insetDrawable)
        addItemDecoration(dividerItemDecoration)
    }

    interface OnItemClickedListener {
        fun onItemClicked(persona: IPersona)
    }
}