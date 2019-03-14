/**
 * Copyright © 2018 Microsoft Corporation. All rights reserved.
 */

package com.microsoft.officeuifabric.peoplepicker

import android.content.Context
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.Filter
import android.widget.TextView
import com.microsoft.officeuifabric.R
import com.microsoft.officeuifabric.persona.IPersona
import com.microsoft.officeuifabric.persona.Persona
import com.microsoft.officeuifabric.persona.PersonaView
import com.microsoft.officeuifabric.view.TemplateView
import com.tokenautocomplete.TokenCompleteTextView
import kotlinx.android.synthetic.main.view_people_picker.view.*

typealias PeoplePickerPersonaChipClickStyle = TokenCompleteTextView.TokenClickStyle

/**
 * [PeoplePickerView] is a customizable view comprised of a label and [PeoplePickerTextView].
 *
 */
class PeoplePickerView : TemplateView {
    /**
     * Label describing the [PeoplePickerTextView] field.
     */
    var label: String = ""
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * [valueHint] is important for accessibility but will not be displayed.
     */
    var valueHint: String = context.getString(R.string.people_picker_accessibility_default_hint)
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * The list of personas that are available to be filtered and supplied to the dropdown
     * containing suggestions for the [PeoplePickerTextView].
     */
    var availablePersonas: ArrayList<IPersona>? = null
        set(value) {
            field = value
            peoplePickerTextViewAdapter = PeoplePickerTextViewAdapter(
                context,
                PersonaView.layoutId,
                value ?: ArrayList(),
                PersonaFilter(this)
            )
        }
    /**
     * Tracks personas that have been added as PersonaChips to the [PeoplePickerTextView].
     */
    var pickedPersonas = ArrayList<IPersona>()
        set(value) {
            field = value
            updatePersonaChips()
        }
    /**
     * The number of characters required to be entered before showing the dropdown of filtered suggestions.
     */
    var characterThreshold: Int = 1
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * This will automatically remove persona chips from your text field, but you will need to do extra
     * filtering work to ensure duplicates don't end up in your dropdown list.
     */
    var allowDuplicatePersonaChips: Boolean = false
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * Limits the total number of persona chips that can be added to the field.
     */
    var personaChipLimit: Int = -1
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * Defines what happens when a user clicks on a persona chip.
     * To use your own onClick callback, set this property to [PeoplePickerPersonaChipClickStyle.SelectDeselect]
     * and set the [personaChipClickListener]'s onClick callback.
     */
    var personaChipClickStyle: PeoplePickerPersonaChipClickStyle = PeoplePickerPersonaChipClickStyle.Select
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * Collapse the [PeoplePickerTextView] to a single line when it loses focus.
     */
    var allowCollapse: Boolean = true
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * Add a button to the bottom of the list of suggested personas that triggers a
     * new search when using [searchDirectorySuggestionsListener].
     */
    var showSearchDirectoryButton: Boolean = false
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * Flag for enabling Drag and Drop persona chips.
     */
    var allowPersonaChipDragAndDrop: Boolean = false
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * Customizes text announced by the screen reader.
     * If there is no custom accessibility text, we use default text.
     */
    var accessibilityTextProvider: PeoplePickerAccessibilityTextProvider? = null
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }

    /**
     * Callback to use your own [IPersona] object in place of our default [Persona].
     */
    var onCreatePersona: ((name: String, email: String) -> IPersona)? = null
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }
    /**
     * Callbacks for when a persona chip is added or removed from the [PeoplePickerTextView].
     */
    var pickedPersonasChangeListener: PickedPersonasChangeListener? = null
    /**
     * Callbacks for customized filtering. Supports async.
     */
    var personaSuggestionsListener: PersonaSuggestionsListener? = null
    /**
     * Callbacks for additional customized filtering when using the [showSearchDirectoryButton].
     */
    var searchDirectorySuggestionsListener: PersonaSuggestionsListener? = null
    /**
     * When a persona chip with a [PeoplePickerPersonaChipClickStyle] of SelectDeselect is selected,
     * the next touch will fire [PersonaChipClickListener.onClick].
     */
    var personaChipClickListener: PersonaChipClickListener? = null
        set(value) {
            if (field == value)
                return
            field = value
            updateViews()
        }

    private var peoplePickerTextViewAdapter: PeoplePickerTextViewAdapter? = null
        set(value) {
            field = value
            value?.onSearchDirectoryButtonClicked = onSearchDirectoryButtonClicked
            updateViews()
        }
    private var searchConstraint: CharSequence? = null

    private val onSearchDirectoryButtonClicked = OnClickListener {
        val searchDirectorySuggestionsListener = searchDirectorySuggestionsListener
        if (searchDirectorySuggestionsListener != null) {
            peoplePickerTextViewAdapter?.isSearchingDirectory = true
            searchDirectorySuggestionsListener.onGetSuggestedPersonas(searchConstraint, availablePersonas, pickedPersonas) {
                post {
                    peoplePickerTextViewAdapter?.personas = it
                    peoplePickerTextViewAdapter?.isSearchingDirectory = false
                }
            }
        }
    }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
        val styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.PeoplePickerView)
        label = styledAttrs.getString(R.styleable.PeoplePickerView_label) ?: ""
        valueHint = styledAttrs.getString(R.styleable.PeoplePickerView_valueHint) ?: context.getString(R.string.people_picker_accessibility_default_hint)
        val personaChipClickStyleOrdinal = styledAttrs.getInt(R.styleable.PeoplePickerView_personaChipClickStyle, PeoplePickerPersonaChipClickStyle.Select.ordinal)
        personaChipClickStyle = PeoplePickerPersonaChipClickStyle.values()[personaChipClickStyleOrdinal]

        styledAttrs.recycle()
    }

    // Template

    override val templateId: Int = R.layout.view_people_picker
    private var labelTextView: TextView? = null
    private var peoplePickerTextView: PeoplePickerTextView? = null

    override fun onTemplateLoaded() {
        labelTextView = people_picker_label
        peoplePickerTextView = people_picker_text_view

        // Fixed properties for TokenCompleteTextView.
        peoplePickerTextView?.apply {
            dropDownWidth = ViewGroup.LayoutParams.MATCH_PARENT
            allowCollapse(true)
            isLongClickable = true
            setTokenListener(TokenListener(this@PeoplePickerView))
            performBestGuess(false)
        }

        updatePersonaChips()
        updateViews()
        addLabelClickListenerForAccessibility()

        super.onTemplateLoaded()
    }

    private fun updatePersonaChips() {
        peoplePickerTextView?.let {
            it.removeObjects(it.objects)
            it.addObjects(pickedPersonas)
        }
    }

    private fun updateViews() {
        labelTextView?.text = label
        peoplePickerTextView?.apply {
            valueHint = this@PeoplePickerView.valueHint
            allowCollapse(allowCollapse)
            allowDuplicatePersonaChips = this@PeoplePickerView.allowDuplicatePersonaChips
            threshold = characterThreshold
            personaChipLimit = this@PeoplePickerView.personaChipLimit
            setAdapter(peoplePickerTextViewAdapter)
            personaChipClickStyle = this@PeoplePickerView.personaChipClickStyle
            allowPersonaChipDragAndDrop = this@PeoplePickerView.allowPersonaChipDragAndDrop
            onCreatePersona = ::createPersona
            setAccessibilityTextProvider(this@PeoplePickerView.accessibilityTextProvider)
            personaChipClickListener = this@PeoplePickerView.personaChipClickListener
        }
        peoplePickerTextViewAdapter?.showSearchDirectoryButton = showSearchDirectoryButton
    }

    private fun addLabelClickListenerForAccessibility() {
        labelTextView?.setOnClickListener {
            val accessibilityNodeInfo = it?.createAccessibilityNodeInfo()
            if (accessibilityNodeInfo?.isAccessibilityFocused == true) {
                peoplePickerTextView?.requestFocus()
                peoplePickerTextView?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
            }
        }
    }

    private fun createPersona(name: String, email: String): IPersona =
        onCreatePersona?.invoke(name, email) ?: Persona(name, email)

    // Filter

    private class PersonaFilter(val view: PeoplePickerView) : Filter() {
        override fun performFiltering(constraint: CharSequence?): Filter.FilterResults {
            view.searchConstraint = constraint
            if (view.personaSuggestionsListener != null) {
                // Show the previous results until we get new ones.
                // This code allows us to keep dropdown open and not hidden on each key stroke.
                val suggestedPersonas = view.peoplePickerTextViewAdapter?.personas
                return FilterResults().apply {
                    values = suggestedPersonas
                    count = suggestedPersonas?.size ?: 0
                }
            }

            val availablePersonas = view.availablePersonas
            val suggestedPersonas = when {
                availablePersonas == null -> ArrayList()
                constraint != null -> {
                    val searchTerm = constraint.toString().toLowerCase()
                    val filteredResults = availablePersonas.filter {
                        it.name.toLowerCase().contains(searchTerm) && !view.pickedPersonas.contains(it)
                    }
                    ArrayList(filteredResults)
                }
                else -> availablePersonas
            }
            return FilterResults().apply {
                values = suggestedPersonas
                count = suggestedPersonas.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: Filter.FilterResults) {
            val listener = view.personaSuggestionsListener
            val accessibilityTextProvider = view.peoplePickerTextView?.accessibilityTextProvider
            val countSpanStart = view.peoplePickerTextView?.countSpanStart
            if (listener != null) {
                listener.onGetSuggestedPersonas(constraint, view.availablePersonas, view.pickedPersonas) {
                    view.post {
                        view.peoplePickerTextViewAdapter?.personas = it
                        if (constraint != null && countSpanStart == -1)
                            view.announceForAccessibility(accessibilityTextProvider?.getPersonaSuggestionsOpenedText(it))
                    }
                }
            } else {
                val personas = results.values as ArrayList<IPersona>
                view.peoplePickerTextViewAdapter?.personas = personas
                if (constraint != null && countSpanStart == -1)
                    view.announceForAccessibility(accessibilityTextProvider?.getPersonaSuggestionsOpenedText(personas))
            }
        }
    }

    // Listeners

    /**
     * Callbacks for when a persona is added or removed from the [PeoplePickerTextView]
     */
    interface PickedPersonasChangeListener {
        fun onPersonaAdded(persona: IPersona)
        fun onPersonaRemoved(persona: IPersona)
    }

    /**
     * Callbacks for updating suggestions in the dropdown list of personas.
     */
    interface PersonaSuggestionsListener {
        fun onGetSuggestedPersonas(
            searchConstraint: CharSequence?,
            availablePersonas: ArrayList<IPersona>?,
            pickedPersonas: ArrayList<IPersona>,
            completion: (suggestedPersonas: ArrayList<IPersona>) -> Unit
        )
    }

    interface PersonaChipClickListener {
        fun onClick(persona: IPersona)
    }

    private class TokenListener(val view: PeoplePickerView) : TokenCompleteTextView.TokenListener<IPersona> {
        override fun onTokenAdded(token: IPersona) {
            view.pickedPersonas.add(token)
            view.pickedPersonasChangeListener?.onPersonaAdded(token)
        }

        override fun onTokenRemoved(token: IPersona) {
            view.pickedPersonas.remove(token)
            view.pickedPersonasChangeListener?.onPersonaRemoved(token)
        }
    }
}