/**
 * Copyright © 2018 Microsoft Corporation. All rights reserved.
 */

package com.microsoft.officeuifabric.peoplepicker

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewCompat
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.support.v4.widget.ExploreByTouchHelper
import android.text.InputFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.method.MovementMethod
import android.text.style.TextAppearanceSpan
import android.text.util.Rfc822Token
import android.text.util.Rfc822Tokenizer
import android.util.AttributeSet
import android.util.Patterns
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import com.microsoft.officeuifabric.R
import com.microsoft.officeuifabric.persona.IPersona
import com.microsoft.officeuifabric.persona.PersonaChipView
import com.microsoft.officeuifabric.persona.setPersona
import com.tokenautocomplete.CountSpan
import com.tokenautocomplete.TokenCompleteTextView

/**
 * [PeoplePickerTextView] provides all of the functionality needed to add [PersonaChipView]s as [tokens]
 * into an [EditText] view.
 *
 * Functionality we add in addition to [TokenCompleteTextView]'s functionality includes:
 * - Hiding the cursor when a token is selected
 * - Styling the [CountSpan]
 * - Drag and drop option
 * - Accessibility
 *
 * TODO Known issues:
 * - Using backspace to delete a selected token does not work if other text is entered in the input;
 * [TokenCompleteTextView] overrides [onCreateInputConnection] which blocks our ability to control this functionality.
 * - If you long press and select all the tokens, then press "Cut" to put them on the clipboard, the fragment crashes with an IndexOutOfBoundsException;
 * This same bug happens in Outlook.
 * - In [performCollapse] when the first token takes up the whole width of the text view, the CountSpan is in danger of being cut off.
 * Instead of shortening the token (PersonaChip in our implementation), [TokenCompleteTextView] removes it and adds it to the count in the CountSpan.
 * Shortening the PersonaChip would be more ideal.
 * -setTokenLimit is not working as intended. Need to debug this and add the public property back into the api.
 *
 * TODO Future work:
 * - Limit what appears in the long click context menu.
 * - Baseline align chips with other text.
 * - Improve vertical spacing for chips.
 * - Add click api for persona chip and relevant accessibility events, including handling deselection
 * - Announce already selected persona chips, may be related to this ^
 */
internal class PeoplePickerTextView : TokenCompleteTextView<IPersona> {
    companion object {
        // Max number of personas the screen reader will announce on focus.
        private const val MAX_PERSONAS_TO_READ = 3

        // Removes constraints to the input field
        private val noFilters = arrayOfNulls<InputFilter>(0)
        // Constrains changes that can be made to the input field to none
        private val blockInputFilters = arrayOf(InputFilter { _, _, _, _, _, _ -> "" })
    }

    /**
     * Defines what happens when a user clicks on a [personaChip].
     */
    var personaChipClickStyle: PeoplePickerPersonaChipClickStyle = PeoplePickerPersonaChipClickStyle.Select
        set(value) {
            field = value
            setTokenClickStyle(value)
        }
    /**
     * Flag for enabling Drag and Drop persona chips.
     */
    var allowPersonaChipDragAndDrop: Boolean = false
    /**
     * Store the hint so that we can control when it is announced for accessibility
     */
    var valueHint: CharSequence = ""
        set(value) {
            field = value
            hint = value
        }

    lateinit var onCreatePersona: (name: String, email: String) -> IPersona

    val countSpanStart: Int
        get() = text.indexOfFirst { it == '+' }
    private val countSpanEnd: Int
        get() = text.length

    private val accessibilityTouchHelper = AccessibilityTouchHelper(this)
    private var blockedMovementMethod: MovementMethod? = null
    // Keep track of persona selection for accessibility events
    private var selectedPersona: IPersona? = null
        set(value) {
            field = value
            if (value != null)
                blockInput()
            else
                unblockInput()
        }
    private var shouldAnnouncePersonaAddition: Boolean = false
    private var shouldAnnouncePersonaRemoval: Boolean = true
    private var searchConstraint: CharSequence = ""

    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityTouchHelper)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        super.setTokenListener(TokenListener(this))
    }

    // @JvmOverloads does not work in this scenario due to parameter defaults
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    override fun getViewForObject(`object`: IPersona): View {
        val view = PersonaChipView(context)
        view.showCloseIconWhenSelected = personaChipClickStyle == PeoplePickerPersonaChipClickStyle.Select
        view.listener = object : PersonaChipView.Listener {
            override fun onClicked() {
                // no op
            }

            override fun onSelected(selected: Boolean) {
                if (selected)
                    selectedPersona = `object`
                else
                    selectedPersona = null
            }
        }
        view.setPersona(`object`)
        return view
    }

    override fun defaultObject(completionText: String): IPersona? {
        if (completionText.isEmpty() || !isEmailValid(completionText))
            return null

        return onCreatePersona("", completionText)
    }

    override fun performCollapse(hasFocus: Boolean) {
        if (getOriginalCountSpan() == null)
            removeCountSpanText()

        super.performCollapse(hasFocus)

        // Remove viewPadding for single line to fix jittery virtual view bounds in ExploreByTouch.
        if (!hasFocus())
            setPadding(0, 0, 0, 0)
        else
            setPadding(0, resources.getDimension(R.dimen.uifabric_people_picker_text_view_padding).toInt(), 0, resources.getDimension(R.dimen.uifabric_people_picker_text_view_padding).toInt())

        updateCountSpanStyle()
    }

    override fun onFocusChanged(hasFocus: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(hasFocus, direction, previous)

        val inputMethodManager: InputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Soft keyboard does not always show up without this
        if (hasFocus)
            post {
                inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        else
            inputMethodManager.hideSoftInputFromWindow(windowToken, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        // super.onSelectionChanged is buggy, but we still need the accessibility event from the super super call.
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED)
        // This fixes buggy cursor position in accessibility mode.
        setSelection(text.length)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        selectedPersona = null

        if (lengthAfter > lengthBefore || lengthAfter < lengthBefore && !text.isNullOrEmpty())
            setupSearchConstraint(text)
    }

    override fun replaceText(text: CharSequence?) {
        shouldAnnouncePersonaAddition = true
        super.replaceText(text)
    }

    override fun canDeleteSelection(beforeLength: Int): Boolean {
        // This method is called from keyboard events so any token removed would be coming from the user.
        shouldAnnouncePersonaRemoval = true
        return super.canDeleteSelection(beforeLength)
    }

    override fun removeObject(`object`: IPersona?) {
        shouldAnnouncePersonaRemoval = false
        super.removeObject(`object`)
    }

    internal fun removeObjects(personas: List<IPersona>?) {
        if (personas == null)
            return

        var i = 0
        while (i < personas.size) {
            removeObject(personas[i])
            i++
        }

        removeCountSpanText()
    }

    private fun setupSearchConstraint(text: CharSequence?) {
        accessibilityTouchHelper.invalidateRoot()
        val personaSpanEnd = text?.indexOfLast { it == ',' }?.plus(1) ?: -1
        searchConstraint = when {
            // Ignore the count span
            countSpanStart != -1 -> ""
            // If we have personas, we'll also have comma tokenizers to remove from the text
            personaSpanEnd > 0 -> text?.removeRange(text.indexOfFirst { it == ',' }, personaSpanEnd)?.trim() ?: ""
            // Any other characters will be used as the search constraint to perform filtering.
            else -> text ?: ""
        }
        // This keeps the entered text accessibility focused as the user types, which makes the suggested personas list the next focusable view.
        accessibilityTouchHelper.sendEventForVirtualView(objects.size, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
    }

    private fun updateCountSpanStyle() {
        val originalCountSpan = getOriginalCountSpan() ?: return

        text.removeSpan(originalCountSpan)
        val replacementCountSpan = SpannableString(originalCountSpan.text)
        replacementCountSpan.setSpan(
            TextAppearanceSpan(context, R.style.TextAppearance_UIFabric_PeoplePickerCountSpan),
            0,
            replacementCountSpan.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        text.replace(countSpanStart, countSpanEnd, replacementCountSpan)
    }

    private fun removeCountSpanText() {
        val countSpanStart = countSpanStart
        if (countSpanStart > -1)
            text.delete(countSpanStart, countSpanEnd)
    }

    private fun isEmailValid(email: CharSequence): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun blockInput() {
        isCursorVisible = false
        filters = blockInputFilters

        // Prevents other input from being selected when a persona chip is selected
        blockedMovementMethod = movementMethod
        movementMethod = null
    }

    private fun unblockInput() {
        isCursorVisible = true
        filters = noFilters

        // Restores original MovementMethod we blocked during selection
        if (blockedMovementMethod != null)
            movementMethod = blockedMovementMethod
    }

    private fun getPersonaSpans(start: Int = 0, end: Int = text.length): Array<TokenCompleteTextView<IPersona>.TokenImageSpan> =
        text.getSpans(start, end, TokenImageSpan::class.java) as Array<TokenCompleteTextView<IPersona>.TokenImageSpan>

    private fun getOriginalCountSpan(): CountSpan? =
        text.getSpans(0, text.length, CountSpan::class.java).firstOrNull()

    private fun getSpanForPersona(persona: Any): TokenImageSpan? =
        getPersonaSpans().firstOrNull { it.token === persona }

    // Token listener

    private var tokenListener: TokenCompleteTextView.TokenListener<IPersona>? = null

    override fun setTokenListener(l: TokenCompleteTextView.TokenListener<IPersona>?) {
        tokenListener = l
    }

    private class TokenListener(val view: PeoplePickerTextView) : TokenCompleteTextView.TokenListener<IPersona> {
        override fun onTokenAdded(token: IPersona) {
            view.tokenListener?.onTokenAdded(token)
            view.announcePersonaAdded(token)
        }

        override fun onTokenRemoved(token: IPersona) {
            view.tokenListener?.onTokenRemoved(token)
            view.announcePersonaRemoved(token)
        }
    }

    // Drag and drop

    private var isDraggingPersonaChip: Boolean = false
    private var firstTouchX: Float = 0f
    private var firstTouchY: Float = 0f
    private var initialTouchedPersonaSpan: TokenImageSpan? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false

        if (personaChipClickStyle == PeoplePickerPersonaChipClickStyle.None)
            handled = super.onTouchEvent(event)

        val touchedPersonaSpan = getPersonaSpanAt(event.x, event.y)

        if (touchedPersonaSpan != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (allowPersonaChipDragAndDrop) {
                        initialTouchedPersonaSpan = touchedPersonaSpan
                        firstTouchX = event.x
                        firstTouchY = event.y
                        parent.requestDisallowInterceptTouchEvent(true)
                        handled = true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (allowPersonaChipDragAndDrop && !isDraggingPersonaChip) {
                        val deltaX = Math.ceil(Math.abs(firstTouchX - event.x).toDouble()).toInt()
                        val deltaY = Math.ceil(Math.abs(firstTouchY - event.y).toDouble()).toInt()
                        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                        if (deltaX >= touchSlop || deltaY >= touchSlop)
                            startPersonaDragAndDrop(touchedPersonaSpan.token)
                        handled = true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isFocused && text != null && initialTouchedPersonaSpan == touchedPersonaSpan)
                        touchedPersonaSpan.onClick()
                    if (!isFocused)
                        requestFocus()
                    initialTouchedPersonaSpan = null
                    handled = true
                }

                MotionEvent.ACTION_CANCEL -> {
                    initialTouchedPersonaSpan = null
                }
            }
        }

        if (!handled && personaChipClickStyle != PeoplePickerPersonaChipClickStyle.None)
            handled = super.onTouchEvent(event)

        return handled
    }

    override fun onDragEvent(event: DragEvent): Boolean {
        if (!allowPersonaChipDragAndDrop)
            return false

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)

            DragEvent.ACTION_DRAG_ENTERED -> requestFocus()

            DragEvent.ACTION_DROP -> return addPersonaFromDragEvent(event)

            DragEvent.ACTION_DRAG_ENDED -> {
                if (!event.result && isDraggingPersonaChip)
                    addPersonaFromDragEvent(event)
                isDraggingPersonaChip = false
            }
        }
        return false
    }

    private fun getClipDataForPersona(persona: IPersona): ClipData? {
        val name = persona.name
        val email = persona.email
        val rfcToken = Rfc822Token(name, email, null)
        return ClipData.newPlainText(if (TextUtils.isEmpty(name)) email else name, rfcToken.toString())
    }

    private fun getPersonaForClipData(clipData: ClipData): IPersona? {
        if (!clipData.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || clipData.itemCount != 1)
            return null

        val clipDataItem = clipData.getItemAt(0) ?: return null

        val data = clipDataItem.text
        if (TextUtils.isEmpty(data))
            return null

        val rfcTokens = Rfc822Tokenizer.tokenize(data)
        if (rfcTokens == null || rfcTokens.isEmpty())
            return null

        val rfcToken = rfcTokens[0]
        return onCreatePersona(rfcToken.name ?: "", rfcToken.address ?: "")
    }

    private fun startPersonaDragAndDrop(persona: IPersona) {
        val clipData = getClipDataForPersona(persona) ?: return

        // Layout a copy of the persona chip to use as the drag shadow
        val personaChipView = getViewForObject(persona)
        personaChipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        personaChipView.layout(0, 0, personaChipView.measuredWidth, personaChipView.measuredHeight)
        personaChipView.background = ContextCompat.getDrawable(context, R.color.uifabric_people_picker_persona_chip_drag_background)

        // We pass the persona object as LocalState so we can restore it when dropping
        // [startDrag] is deprecated, but the new [startDragAndDrop] requires a higher api than our min
        isDraggingPersonaChip = startDrag(clipData, View.DragShadowBuilder(personaChipView), persona, 0)
        if (isDraggingPersonaChip)
            removeObject(persona)
    }

    private fun getPersonaSpanAt(x: Float, y: Float): TokenImageSpan? {
        if (text.isEmpty())
            return null

        val offset = getOffsetForPosition(x, y)
        if (offset == -1)
            return null

        return getPersonaSpans(offset, offset).firstOrNull()
    }

    private fun addPersonaFromDragEvent(event: DragEvent): Boolean {
        var persona = event.localState as? IPersona

        // If it looks like the drag & drop is not coming from us, try to extract a persona object from the clipData
        if (persona == null && event.clipData != null)
            persona = getPersonaForClipData(event.clipData)

        if (persona == null)
            return false

        addObject(persona)

        return true
    }

    // Accessibility

    private var customAccessibilityTextProvider: PeoplePickerAccessibilityTextProvider? = null
    private val defaultAccessibilityTextProvider = PeoplePickerAccessibilityTextProvider(resources)
    val accessibilityTextProvider: PeoplePickerAccessibilityTextProvider
        get() = customAccessibilityTextProvider ?: defaultAccessibilityTextProvider

    fun setAccessibilityTextProvider(accessibilityTextProvider: PeoplePickerAccessibilityTextProvider?) {
        customAccessibilityTextProvider = accessibilityTextProvider
    }

    override fun dispatchHoverEvent(motionEvent: MotionEvent): Boolean {
        // Accessibility first
        return if (accessibilityTouchHelper.dispatchHoverEvent(motionEvent))
            true
        else
            super.dispatchHoverEvent(motionEvent)
    }

    private fun announcePersonaAdded(persona: IPersona) {
        accessibilityTouchHelper.invalidateRoot()

        val replacedText = if (searchConstraint.isNotEmpty())
            "${resources.getString(R.string.people_picker_accessibility_replaced, searchConstraint)} "
        else
            ""

        // We only want to announce when a persona was added by a user.
        // If text has been replaced in the text editor and a token was added, the user added a token.
        if (shouldAnnouncePersonaAddition) {
            announceForAccessibility("$replacedText ${getAnnouncementText(
                persona,
                R.string.people_picker_accessibility_persona_added
            )}")
        }
    }

    private fun announcePersonaRemoved(persona: IPersona) {
        accessibilityTouchHelper.invalidateRoot()

        // We only want to announce when a persona was removed by a user.
        if (shouldAnnouncePersonaRemoval) {
            announceForAccessibility(getAnnouncementText(
                persona,
                R.string.people_picker_accessibility_persona_removed
            ))
        }
    }

    private fun getAnnouncementText(persona: IPersona, stringResourceId: Int): CharSequence =
        resources.getString(stringResourceId, accessibilityTextProvider.getPersonaDescription(persona))

    private fun positionIsInsidePersonaBounds(x: Float, y: Float, personaSpan: TokenImageSpan?): Boolean =
        getBoundsForPersonaSpan(personaSpan).contains(x.toInt(), y.toInt())

    private fun positionIsInsideSearchConstraintBounds(x: Float, y: Float): Boolean {
        if (searchConstraint.isNotEmpty())
            return getBoundsForSearchConstraint().contains(x.toInt(), y.toInt())
        return false
    }

    private fun getBoundsForSearchConstraint(): Rect {
        val start = text.indexOf(searchConstraint[0])
        val end = text.length
        return calculateBounds(start, end, resources.getDimension(R.dimen.uifabric_people_picker_accessibility_search_constraint_extra_space).toInt())
    }

    private fun getBoundsForPersonaSpan(personaSpan: TokenImageSpan? = null): Rect {
        val start = text.getSpanStart(personaSpan)
        val end = text.getSpanEnd(personaSpan)
        return calculateBounds(start, end)
    }

    private fun calculateBounds(start: Int, end: Int, extraSpaceForLegibility: Int = 0): Rect {
        val line = layout.getLineForOffset(end)
        // Persona spans increase line height. Without them, we need to make the virtual view bound bottom lower.
        val bounds = Rect(
            layout.getPrimaryHorizontal(start).toInt() - extraSpaceForLegibility,
            layout.getLineTop(line),
            layout.getPrimaryHorizontal(end).toInt() + extraSpaceForLegibility,
            if (getPersonaSpans().isEmpty()) bottom else layout.getLineBottom(line)
        )
        bounds.offset(paddingLeft, paddingTop)
        return bounds
    }

    private fun setHint() {
        if (!isFocused)
        // If the edit box is not focused, there is no event that requires a hint.
            hint = ""
        else
            hint = valueHint
    }

    private inner class AccessibilityTouchHelper(host: View) : ExploreByTouchHelper(host) {
        // Host

        val peoplePickerTextViewBounds = Rect(0, 0, width, height)

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            setHint()
            setInfoText(info)
        }

        override fun onPopulateAccessibilityEvent(host: View?, event: AccessibilityEvent?) {
            super.onPopulateAccessibilityEvent(host, event)
            /**
             * The CommaTokenizer is confusing in the screen reader.
             * This overrides announcements that include the CommaTokenizer.
             * We handle cases for replaced text and persona spans added / removed through callbacks.
             */
            if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
                event.text.clear()
        }

        private fun setInfoText(info: AccessibilityNodeInfoCompat) {
            val personas = objects
            if (personas == null || personas.isEmpty())
                return

            var infoText = ""
            // Read all of the personas if the list of personas in the field is short
            // Otherwise, read how many personas are in the field
            if (personas.size <= MAX_PERSONAS_TO_READ)
                infoText += personas.map { accessibilityTextProvider.getPersonaDescription(it) }.joinToString { it }
            else
                infoText = accessibilityTextProvider.getPersonaQuantityText(personas as ArrayList<IPersona>)

            info.text = infoText +
                // Also ready any entered text in the field
                if (searchConstraint.isNotEmpty())
                    ", $searchConstraint"
                else
                    ""
        }

        // Virtual views

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            if (objects == null || objects.size == 0)
                return ExploreByTouchHelper.INVALID_ID

            val offset = getOffsetForPosition(x, y)
            if (offset != -1) {
                val personaSpan = getPersonaSpans(offset, offset).firstOrNull()
                if (personaSpan != null && positionIsInsidePersonaBounds(x, y, personaSpan) && isFocused)
                    return objects.indexOf(personaSpan.token)
                else if (searchConstraint.isNotEmpty() && positionIsInsideSearchConstraintBounds(x, y))
                    return objects.size
                else if (peoplePickerTextViewBounds.contains(x.toInt(), y.toInt())) {
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    return ExploreByTouchHelper.HOST_ID
                }
            }

            return ExploreByTouchHelper.INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.clear()

            if (objects == null || objects.size == 0 || !isFocused)
                return

            for (i in objects.indices)
                virtualViewIds.add(i)

            if (searchConstraint.isNotEmpty())
                virtualViewIds.add(objects.size)
        }

        override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
            if (objects == null || virtualViewId >= objects.size) {
                // The content description is mandatory.
                event.contentDescription = ""
                return
            }

            if (!isFocused) {
                // Only respond to events for persona chips if the edit box is focused.
                // Without this the user still gets haptic feedback when hovering over a persona chip.
                event.recycle()
                event.contentDescription = ""
                return
            }

            if (virtualViewId == objects.size) {
                event.contentDescription = searchConstraint
                return
            }

            val persona = objects[virtualViewId]
            val personaSpan = getSpanForPersona(persona)
            if (personaSpan != null)
                event.contentDescription = accessibilityTextProvider.getPersonaDescription(persona)

            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED)
                event.contentDescription = String.format(
                    resources.getString(R.string.people_picker_accessibility_selected_persona),
                    event.contentDescription
                )
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            if (objects == null || virtualViewId > objects.size) {
                // the content description & the bounds are mandatory.
                node.contentDescription = ""
                node.setBoundsInParent(peoplePickerTextViewBounds)
                return
            }

            if (!isFocused) {
                // Only populate nodes for persona chips if the edit box is focused.
                node.recycle()
                node.contentDescription = ""
                node.setBoundsInParent(peoplePickerTextViewBounds)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val clickAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_CLICK,
                    resources.getString(R.string.people_picker_accessibility_select_persona)
                )
                node.addAction(clickAction)
            } else {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            }

            if (virtualViewId == objects.size) {
                if (searchConstraint.isNotEmpty()){
                    node.contentDescription = searchConstraint
                    node.setBoundsInParent(getBoundsForSearchConstraint())
                } else {
                    node.contentDescription = ""
                    node.setBoundsInParent(peoplePickerTextViewBounds)
                }
                return
            }

            val persona = objects[virtualViewId]
            val personaSpan = getSpanForPersona(persona)
            if (personaSpan != null) {
                if (node.isAccessibilityFocused)
                    node.contentDescription = accessibilityTextProvider.getPersonaDescription(persona)
                else
                    node.contentDescription = ""
                node.setBoundsInParent(getBoundsForPersonaSpan(personaSpan))
            }
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (objects == null || virtualViewId >= objects.size)
                return false

            if (AccessibilityNodeInfo.ACTION_CLICK == action) {
                val persona = objects[virtualViewId]
                val personaSpan = getSpanForPersona(persona)
                if (personaSpan != null) {
                    personaSpan.onClick()
                    onPersonaSpanAccessibilityClick(personaSpan)
                    shouldAnnouncePersonaRemoval = true
                    return true
                }
            }

            return false
        }

        private fun onPersonaSpanAccessibilityClick(personaSpan: TokenImageSpan) {
            val personaSpanIndex = getPersonaSpans().indexOf(personaSpan)
            when (personaChipClickStyle) {
                PeoplePickerPersonaChipClickStyle.Select, PeoplePickerPersonaChipClickStyle.SelectDeselect -> {
                    if (selectedPersona != null && selectedPersona == personaSpan.token) {
                        invalidateVirtualView(personaSpanIndex)
                        sendEventForVirtualView(personaSpanIndex, AccessibilityEvent.TYPE_VIEW_CLICKED)
                        sendEventForVirtualView(personaSpanIndex, AccessibilityEvent.TYPE_VIEW_SELECTED)
                    } else {
                        sendEventForVirtualView(personaSpanIndex, AccessibilityEvent.TYPE_VIEW_CLICKED)
                        if (personaChipClickStyle == PeoplePickerPersonaChipClickStyle.Select && personaSpanIndex == -1)
                            invalidateRoot()
                    }
                }
                PeoplePickerPersonaChipClickStyle.Delete -> {
                    sendEventForVirtualView(personaSpanIndex, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    sendEventForVirtualView(personaSpanIndex, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                }
            }
        }
    }
}