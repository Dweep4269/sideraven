/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.ui.autocomplete.InlineAutocompleteEditText

/**
 * The pop-up's field as the upstream toolbar's own control: the completion tail is a span over
 * real letters, and the span-fitting routines carry the IME's composing span across the
 * programmatic text change that adds it, so the keyboard neither restarts nor flips between
 * layouts while the tail fills. Typing over the tail re-completes, the first backspace drops
 * only the tail, and moving the cursor over it (a tap ahead, a Gboard spacebar swipe) accepts
 * it at the cursor's landing point — the toolbar's own mechanics, not ours.
 */
open class SidebarInlineField(context: Context) : InlineAutocompleteEditText(context, null, 0) {
    /** The pop-up's own keys (Escape, the list's arrows, Enter); handled before the control and the IME. */
    var keyHook: ((Int) -> Boolean)? = null

    /** Enter/Go over the IME action, called through the freshened slot. */
    internal var commitHook: (() -> Unit)? = null

    internal var focusHook: ((Boolean) -> Unit)? = null

    /** A keyboard asked for before this view (or its window) was ready: it rises as soon as they are. */
    private var wantsKeyboard = false

    /**
     * Focuses the field and raises the keyboard. Asked for at the pop-up's opening, the view may
     * not be in a window yet — the request then waits for it instead of being lost.
     */
    fun raiseKeyboard() {
        wantsKeyboard = true
        focusAndShowKeyboard()
    }

    private fun focusAndShowKeyboard() {
        if (!wantsKeyboard || !isAttachedToWindow || !hasWindowFocus()) return
        wantsKeyboard = false
        requestFocus()
        showKeyboard()
        // The first request can be dropped while the window settles; one more, a moment later, lands.
        postDelayed({ if (hasFocus() && hasWindowFocus()) showKeyboard() }, IME_RETRY_MS)
    }

    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showSoftInput(this, 0)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { focusAndShowKeyboard() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) post { focusAndShowKeyboard() }
    }

    /** The pre-IME dispatch runs with a keyboard up; without one the normal dispatch does. */
    override fun dispatchKeyEventPreIme(event: KeyEvent?): Boolean =
        when {
            event != null && event.action == KeyEvent.ACTION_DOWN && keyHook?.invoke(event.keyCode) == true -> true
            else -> super.dispatchKeyEventPreIme(event)
        }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean =
        when {
            event != null && event.action == KeyEvent.ACTION_DOWN && keyHook?.invoke(event.keyCode) == true -> true
            else -> super.dispatchKeyEvent(event)
        }

    public override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        focusHook?.invoke(gainFocus)
    }
}

/**
 * The popup-side handle on the field: programmatic sets (its chips, the scanner, clearing), the
 * list-highlight preview, focus, and the lookup/commit hooks the control's listeners freshen
 * against.
 */
class FieldController internal constructor() {
    internal var edit: SidebarInlineField? = null

    /** A keyboard asked for before the field existed: it rises once the field does. */
    private var wantsKeyboard = false

    /** True while the view is being set from here: the text mirror then stays quiet. */
    internal var suppressingMirror: Boolean = false

    // The hooks the field's listeners read on each event; freshened by the wrapper's update.
    internal var autocomplete: (suspend (String) -> String?)? = null
    internal var commit: (() -> Unit)? = null
    internal var onKey: ((Int) -> Boolean)? = null
    internal var onTextMirror: ((String) -> Unit)? = null

    /** The field's current text: the query, plus any completion tail it carries. */
    val text: String?
        get() = edit?.text?.toString()

    /** The on-screen field (its selection is the pop-up's own business; tests reach it here). */
    val view: View?
        get() = edit

    /**
     * Puts [text] in the field from the pop-up's side (its text state is already the result, so
     * the mirror stays quiet); [selectAll] then keeps it all selected, else the cursor waits at
     * the end. Each set resets the control's autocomplete states, as the toolbar's own sets do.
     */
    fun apply(text: String, selectAll: Boolean = false) {
        val field = edit ?: return
        suppressingMirror = true
        try {
            field.setText(text, false)
            if (selectAll && text.isNotEmpty()) {
                field.setSelection(0, text.length)
            } else {
                field.setSelection(text.length)
            }
        } finally {
            suppressingMirror = false
        }
    }

    /**
     * Shows [text] in the field while a suggestion is highlighted down the list, and [restore]
     * (the query, cursor at its end) once the highlight leaves. The control's autocomplete
     * states reset with the set, as they must — the preview is ours, not the tail's.
     */
    fun preview(text: String?, restore: String) {
        val field = edit ?: return
        suppressingMirror = true
        try {
            val shown = text ?: restore
            field.setText(shown, false)
            field.setSelection(shown.length)
        } finally {
            suppressingMirror = false
        }
    }

    /** Focuses the field and raises the keyboard; a call made before the field exists still lands. */
    fun raiseKeyboard() {
        wantsKeyboard = true
        edit?.raiseKeyboard()
    }

    /** The field just appeared: finish a keyboard request made while it was being built. */
    internal fun attach(field: SidebarInlineField) {
        edit = field
        if (wantsKeyboard) {
            wantsKeyboard = false
            field.raiseKeyboard()
        }
    }
}

/** The tail is painted with the field's selection colour, like the M3 field it replaces. */
private const val TAIL_ALPHA = 0.40f

/** A second keyboard request, this long after the first: the window's own focus settles first. */
private const val IME_RETRY_MS = 120L

/**
 * The Search Pop-up's field: the upstream control behind this pop-up's own outline. The pop-up
 * composes [query] and the highlighted [preview]; the control owns everything in between
 * (typing, the tail, acceptance) and reports back through the controller's hooks.
 *
 * @param onTextMirror Every text change the field sees: the pop-up mirrors it (and clears a
 *   list highlight, as its own edits did).
 * @param onCommit Go: Enter through the IME action.
 * @param onKey The pop-up's own keys (Escape, the arrows, hardware Enter); handled-return.
 */
@Composable
internal fun InlineCompletingField(
    query: String,
    preview: String?,
    placeholder: String,
    autocomplete: suspend (String) -> String?,
    onTextMirror: (String) -> Unit,
    onCommit: () -> Unit,
    onKey: (Int) -> Boolean,
    controller: FieldController,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val lookupScope = rememberCoroutineScope()
    var lookupJob by remember { mutableStateOf<Job?>(null) }
    var shownPreview by remember { mutableStateOf<String?>(null) }
    // The field's text inset and its full strip height, in the view's own pixels (the factory
    // runs outside composition).
    val sidePadding = with(LocalDensity.current) { 8.dp.roundToPx() }
    val stripHeight = with(LocalDensity.current) { 56.dp.roundToPx() }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .border(
                    1.dp,
                    if (focused) colors.primary else MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
            }
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty() && preview == null) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AndroidView(
                    factory = { context ->
                        SidebarInlineField(context).apply {
                            // The toolbar's field, per its layout XML: the URI keyboard with a Go
                            // action, no extract UI — the layout clears: keeps on IP addresses.
                            setSingleLine(true)
                            background = null
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(sidePadding, 0, sidePadding, 0)
                            // The whole outlined strip is the field's touch area, not just the text.
                            minimumHeight = stripHeight
                            setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
                            imeOptions =
                                EditorInfo.IME_ACTION_GO or
                                    EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                                    EditorInfo.IME_FLAG_NO_FULLSCREEN
                            setTextColor(colors.onSurface.toArgb())
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                            // The tail, in the selection colour the M3 field's tail used.
                            autoCompleteBackgroundColor = colors.primary.copy(alpha = TAIL_ALPHA).toArgb()

                            // Enter over the IME's Go action: the pop-up's commit.
                            setOnEditorActionListener { _, actionId, event ->
                                val isGo =
                                    actionId == EditorInfo.IME_ACTION_GO ||
                                        (event != null &&
                                            event.action == KeyEvent.ACTION_DOWN &&
                                            (event.keyCode == KeyEvent.KEYCODE_ENTER ||
                                                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER))
                                if (isGo) commitHook?.invoke()
                                isGo
                            }

                            // Text in and out (typing, pastes, tail changes): the pop-up mirrors it.
                            addTextChangedListener(
                                object : TextWatcher {
                                    override fun beforeTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        count: Int,
                                        after: Int,
                                    ) = Unit

                                    override fun onTextChanged(
                                        s: CharSequence?,
                                        start: Int,
                                        before: Int,
                                        count: Int,
                                    ) = Unit

                                    override fun afterTextChanged(editable: Editable?) {
                                        editable?.let {
                                            if (!controller.suppressingMirror) controller.onTextMirror?.invoke(it.toString())
                                        }
                                    }
                                },
                            )

                            // Each growing query asks for a completion; a newer query cancels the
                            // pending lookup, and an answer that arrives too late leaves the field.
                            setOnFilterListener { asked ->
                                lookupJob?.cancel()
                                if (asked.isBlank()) return@setOnFilterListener
                                lookupJob =
                                    lookupScope.launch {
                                        val impl = controller.autocomplete ?: return@launch
                                        val found = impl(asked)
                                        val typed = originalText
                                        if (found != null && found.length > asked.length && typed == asked) {
                                            applyAutocompleteResult(
                                                InlineAutocompleteEditText.AutocompleteResult(
                                                    text = found,
                                                    source = "sideraven-sidebar",
                                                    totalItems = 1,
                                                ),
                                            )
                                        } else {
                                            noAutocompleteResult()
                                        }
                                    }
                            }

                            // The starting text: the pop-up's pre-filled URL, all selected.
                            setText(query, false)
                            setSelection(0, query.length)

                            controller.attach(this)

                            // Set after the input type: setRawInputType resets it. Built without
                            // the platform's EditText style, the control is otherwise focusable in
                            // code only, so neither a tap nor the pop-up's opening could focus it
                            // and the keyboard never came.
                            isFocusableInTouchMode = true
                        }
                    },
                    update = { field ->
                        // The handlers freshen on every recomposition; the factory runs once.
                        field.commitHook = onCommit
                        field.keyHook = onKey
                        field.focusHook = { focused = it }
                        controller.commit = onCommit
                        controller.onKey = onKey
                        controller.onTextMirror = onTextMirror
                        controller.autocomplete = autocomplete
                        if (shownPreview != preview) {
                            controller.preview(preview, query)
                            shownPreview = preview
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag(SidebarTestTag.SEARCH_FIELD),
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}
