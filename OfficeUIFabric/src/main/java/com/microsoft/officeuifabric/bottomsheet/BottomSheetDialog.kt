/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.officeuifabric.bottomsheet

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.widget.LinearLayout
import com.microsoft.officeuifabric.R
import com.microsoft.officeuifabric.drawer.DrawerDialog

/**
 * [BottomSheetDialog] is used to display a list of menu items in a modal dialog.
 */
class BottomSheetDialog : DrawerDialog, OnBottomSheetItemClickListener {
    var onItemClickListener: OnBottomSheetItemClickListener? = null

    private var clickedItemId: Int = -1

    constructor(context: Context, items: ArrayList<BottomSheetItem>) : super(context) {
        val adapter = BottomSheetAdapter(context, items)
        adapter.onBottomSheetItemClickListener = this

        val recyclerView = createRecyclerView(context)
        recyclerView.adapter = adapter
        setContentView(recyclerView)
    }

    private fun createRecyclerView(context: Context): RecyclerView {
        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayout.VERTICAL, false)
        recyclerView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val bottomPadding = context.resources.getDimension(R.dimen.uifabric_bottom_sheet_bottom_padding).toInt()
        recyclerView.setPadding(0, 0, 0, bottomPadding)

        return recyclerView
    }

    override fun onBottomSheetItemClick(id: Int) {
        clickedItemId = id
        collapse()
    }

    override fun dismiss() {
        if (clickedItemId > -1) {
            onItemClickListener?.onBottomSheetItemClick(clickedItemId)
            clickedItemId = -1
        }
        super.dismiss()
    }
}

interface OnBottomSheetItemClickListener {
    fun onBottomSheetItemClick(id: Int)
}