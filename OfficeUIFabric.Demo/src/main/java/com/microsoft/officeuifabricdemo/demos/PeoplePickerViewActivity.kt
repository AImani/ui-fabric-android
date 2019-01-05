/**
 * Copyright © 2018 Microsoft Corporation. All rights reserved.
 */

package com.microsoft.officeuifabricdemo.demos

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import com.microsoft.officeuifabric.peoplepicker.PeoplePickerPersonaChipClickStyle
import com.microsoft.officeuifabric.peoplepicker.PeoplePickerView
import com.microsoft.officeuifabric.persona.IPersona
import com.microsoft.officeuifabricdemo.DemoActivity
import com.microsoft.officeuifabricdemo.R
import com.microsoft.officeuifabricdemo.util.createPersonaList
import kotlinx.android.synthetic.main.activity_people_picker_view.*
import java.util.*
import kotlin.collections.ArrayList

class PeoplePickerViewActivity : DemoActivity() {
    override val contentLayoutId: Int
        get() = R.layout.activity_people_picker_view

    private lateinit var samplePersonas: ArrayList<IPersona>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        samplePersonas = createPersonaList(this)

        // Use attributes to set personaChipClickStyle and label

        people_picker_select.availablePersonas = samplePersonas
        val selectPickedPersonas = arrayListOf(
            samplePersonas[0],
            samplePersonas[1],
            samplePersonas[4],
            samplePersonas[5]
        )
        val selectSearchDirectoryPersonas = arrayListOf(
            samplePersonas[14],
            samplePersonas[7],
            samplePersonas[8],
            samplePersonas[9]
        )
        people_picker_select.pickedPersonas = selectPickedPersonas
        people_picker_select.showSearchDirectoryButton = true
        people_picker_select.searchDirectorySuggestionsListener = createPersonaSuggestionsListener(selectSearchDirectoryPersonas)

        people_picker_select_deselect.availablePersonas = samplePersonas
        val selectDeselectPickedPersonas = arrayListOf(samplePersonas[2])
        people_picker_select_deselect.pickedPersonas = selectDeselectPickedPersonas

        // Use code to set personaChipClickStyle and label

        setupPeoplePickerView(
            getString(R.string.people_picker_none_example),
            samplePersonas,
            PeoplePickerPersonaChipClickStyle.None
        )
        setupPeoplePickerView(
            getString(R.string.people_picker_delete_example),
            samplePersonas,
            PeoplePickerPersonaChipClickStyle.Delete
        )
        setupPeoplePickerView(
            getString(R.string.people_picker_picked_personas_listener),
            samplePersonas,
            pickedPersonasChangeListener = createPickedPersonasChangeListener()
        )
        setupPeoplePickerView(
            getString(R.string.people_picker_suggestions_listener),
            personaSuggestionsListener = createPersonaSuggestionsListener(samplePersonas)
        )
    }

    private fun setupPeoplePickerView(
        labelText: String,
        availablePersonas: ArrayList<IPersona> = ArrayList(),
        personaChipClickStyle: PeoplePickerPersonaChipClickStyle = PeoplePickerPersonaChipClickStyle.Select,
        personaSuggestionsListener: PeoplePickerView.PersonaSuggestionsListener? = null,
        pickedPersonasChangeListener: PeoplePickerView.PickedPersonasChangeListener? = null
    ) {
        val peoplePickerView = PeoplePickerView(this)
        peoplePickerView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        with(peoplePickerView) {
            label = labelText
            this.availablePersonas = availablePersonas
            this.personaChipClickStyle = personaChipClickStyle
            this.personaSuggestionsListener = personaSuggestionsListener
            this.pickedPersonasChangeListener = pickedPersonasChangeListener
        }
        people_picker_layout.addView(peoplePickerView)
    }

    private fun createPickedPersonasChangeListener(): PeoplePickerView.PickedPersonasChangeListener {
        return object : PeoplePickerView.PickedPersonasChangeListener {
            override fun onPersonaAdded(persona: IPersona) {
                showPickedPersonaDialog(getString(R.string.people_picker_dialog_title_added), persona)
            }

            override fun onPersonaRemoved(persona: IPersona) {
                showPickedPersonaDialog(getString(R.string.people_picker_dialog_title_removed), persona)
            }
        }
    }

    private fun showPickedPersonaDialog(title: String, persona: IPersona) {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle(title)
        dialog.setMessage(if (!persona.name.isEmpty()) persona.name else persona.email)
        dialog.show()
    }

    private fun createPersonaSuggestionsListener(personas: ArrayList<IPersona>): PeoplePickerView.PersonaSuggestionsListener {
        return object : PeoplePickerView.PersonaSuggestionsListener {
            override fun onGetSuggestedPersonas(
                searchConstraint: CharSequence?,
                availablePersonas: ArrayList<IPersona>?,
                pickedPersonas: ArrayList<IPersona>,
                completion: (suggestedPersonas: ArrayList<IPersona>) -> Unit
            ) {
                // Simulating async filtering with Timer
                Timer().schedule(
                    object : TimerTask() {
                        override fun run() {
                            completion(filterPersonas(searchConstraint, personas, pickedPersonas))
                        }
                    },
                    500
                )
            }
        }
    }

    // Basic custom filtering example
    private fun filterPersonas(
        searchConstraint: CharSequence?,
        availablePersonas: ArrayList<IPersona>,
        pickedPersonas: ArrayList<IPersona>
    ): ArrayList<IPersona> {
        if (searchConstraint == null)
            return availablePersonas
        val constraint = searchConstraint.toString().toLowerCase()
        val filteredResults = availablePersonas.filter {
            it.name.toLowerCase().contains(constraint) && !pickedPersonas.contains(it)
        }
        return ArrayList(filteredResults)
    }
}