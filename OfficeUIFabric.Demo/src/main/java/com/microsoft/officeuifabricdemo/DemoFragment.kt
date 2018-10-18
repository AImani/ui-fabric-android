package com.microsoft.officeuifabricdemo

import android.os.Bundle
import android.support.v4.app.Fragment
import kotlinx.android.synthetic.main.activity_demo_detail.*
import java.util.*

open class DemoFragment : Fragment() {
    companion object {
        const val DEMO_ID = "demo_id"
    }

    private var demo: Demo? = null

    open fun needsScrollableContainer(): Boolean = true

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        arguments?.let {
            if (it.containsKey(DEMO_ID)) {
                val demoID = it.getSerializable(DEMO_ID) as UUID
                demo = DEMOS.find { it.id == demoID }
                activity?.title = demo?.title
            }
        }
    }
}
