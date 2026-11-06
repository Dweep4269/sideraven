/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.view.KeyEvent

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import mozilla.components.browser.state.search.SearchEngine
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import mozilla.components.compose.browser.awesomebar.AwesomeBarDefaults
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import mozilla.components.compose.browser.awesomebar.AwesomeBar
import mozilla.components.concept.awesomebar.AwesomeBar as AwesomeBarConcept
import org.mozilla.fenix.R
import mozilla.components.ui.icons.R as iconsR

/**
 * What the Search Pop-up is for: navigating the current tab, or opening a new tab once something
 * is chosen.
 *
 * @property initialText The field's starting text (the current URL, selected; empty for a new tab).
 */
data class SearchRequest(
    val newTab: Boolean,
    val initialText: String = "",
    val placement: SearchPlacement = SearchPlacement.Centred,
)

/**
 * The field's starting text for the current tab: its search words on a search results page (so
 * the search can be refined), otherwise its URL; empty for Fenix's own pages.
 */
fun searchStartingText(
    url: String?,
    searchTerms: String?,
): String =
    when {
        !searchTerms.isNullOrBlank() -> searchTerms
        url == null || url.startsWith("about:") -> ""
        else -> url
    }

/**
 * What the pop-up can search with besides the default engine's suggestions (all optional, so a
 * bare pop-up still works).
 *
 * @property engines Fenix's engine shortcuts: search engines, then its Bookmarks, History and Tabs
 *   scopes (type APPLICATION), as Fenix's own engine menu lists them.
 * @property providersFor Fenix's suggestion sources for a picked engine or scope.
 * @property onSubmitWith Enter after picking another search engine: search with it, this time only.
 */
class SearchOptions(
    val engines: List<SearchEngine> = emptyList(),
    val defaultEngine: SearchEngine? = null,
    val providersFor: (SearchEngine) -> List<AwesomeBarConcept.SuggestionProvider> = { emptyList() },
    val onSubmitWith: (text: String, engine: SearchEngine) -> Unit = { _, _ -> },
    val onSearchSettings: () -> Unit = {},
    val clipboardLink: String? = null,
    val autocomplete: suspend (text: String, engine: SearchEngine?) -> String? = { _, _ -> null },
    val onRemoveSuggestion: (AwesomeBarConcept.GroupedSuggestion) -> Unit = {},
    val closeOnKeyboardHide: Boolean = false,
    val closeOnTapOutside: Boolean = true,
    val extras: SearchExtras? = null,
    val privatePrompt: PrivateSuggestionsPrompt? = null,
)

/**
 * Fenix's one-time question, in private browsing, whether to show search suggestions there
 * ([needed] under Fenix's own conditions; the answer is written to Fenix's settings).
 */
interface PrivateSuggestionsPrompt {
    val needed: Boolean

    fun answer(allow: Boolean)

    fun learnMore()
}

/**
 * The pop-up's QR scanner and voice search (Fenix's own in the app). Each answers once, maybe
 * after another screen (the camera, the voice prompt) has been and gone.
 */
interface SearchExtras {
    /** Whether there is a camera to scan with. */
    val canScanQr: Boolean

    /** Whether voice search is on in Fenix's settings and speech recognition is available. */
    val canUseVoice: Boolean

    fun scanQr(onResult: (String) -> Unit)

    fun voice(onResult: (String) -> Unit)

    /** Stops waiting for a result (the pop-up closed). */
    fun cancel() = Unit
}

/** A 40dp icon button inside the pop-up's field. */
@Composable
private fun FieldButton(
    icon: Int,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** Where the Search Pop-up opens. */
sealed interface SearchPlacement {
    /** Centred over the dimmed page: from the Sidebar, or for a new tab. */
    data object Centred : SearchPlacement

    /**
     * Over Fenix's toolbar ([toolbar], window pixels), as if its address field expanded: opening
     * downward from a top toolbar, upward from a bottom one (and above the keyboard).
     */
    data class Anchored(
        val toolbar: Rect,
        val toolbarAtTop: Boolean,
    ) : SearchPlacement
}

/**
 * The Search Pop-up: a card centred over the dimmed page with a field and Fenix's own suggestions.
 *
 * @param providers Fenix's suggestion sources, already pointed at the right target.
 * @param onSubmit Enter: load [text] as an address, or search for it.
 * @param onSuggestion A suggestion was chosen (it performs its own action).
 * @param onOpenHome "Open home page", shown for new tabs.
 */
@Composable
fun SearchPopup(
    request: SearchRequest,
    providers: List<AwesomeBarConcept.SuggestionProvider>,
    onSubmit: (text: String) -> Unit,
    onSuggestion: (AwesomeBarConcept.SuggestionItem) -> Unit,
    onDismiss: () -> Unit,
    onOpenHome: (() -> Unit)? = null,
    options: SearchOptions = SearchOptions(),
    fieldController: (FieldController) -> Unit = {},
) {
    // Enter: the card grows out of its anchor (or centred) while the dim layer fades in; closing
    // fades both out first. Reduced: fades only; Off: instant.
    val motion = LocalSidebarMotion.current
    val shown = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                shown.animateTo(0f, motion.fade(EXIT_MS))
                currentOnDismiss()
            }
        }
    }
    LaunchedEffect(Unit) { shown.animateTo(1f, motion.fade(ENTER_MS)) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The dimmed layer: the pop-up sits above the page and the Sidebar; a tap on it closes the pop-up.
        val placement = request.placement
        val origin =
            when {
                placement is SearchPlacement.Anchored && placement.toolbarAtTop -> TransformOrigin(0.5f, 0f)
                placement is SearchPlacement.Anchored -> TransformOrigin(0.5f, 1f)
                else -> TransformOrigin.Center
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = SCRIM_ALPHA * shown.value)) }
                    .clickable(interactionSource = null, indication = null, onClickLabel = null, role = null) { if (options.closeOnTapOutside) close() }
                    .testTag(SidebarTestTag.SEARCH_SCRIM)
                    .then(if (placement is SearchPlacement.Anchored) Modifier.imePadding() else Modifier.systemBarsPadding().imePadding().padding(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnchoredOrCentred(placement) {
            Surface(
                modifier =
                    (if (placement is SearchPlacement.Anchored) Modifier else Modifier.widthIn(max = 600.dp).fillMaxWidth())
                        .graphicsLayer {
                            alpha = shown.value
                            val scale = if (motion.moves) ENTER_SCALE + (1f - ENTER_SCALE) * shown.value else 1f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = origin
                        }
                        .testTag(SidebarTestTag.SEARCH_POPUP),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
            ) {
                SearchPopupContent(request, providers, onSubmit, onSuggestion, close, onOpenHome, options, fieldController)
            }
            }
        }
    }
}

@Composable
private fun SearchPopupContent(
    request: SearchRequest,
    providers: List<AwesomeBarConcept.SuggestionProvider>,
    onSubmit: (String) -> Unit,
    onSuggestion: (AwesomeBarConcept.SuggestionItem) -> Unit,
    onDismiss: () -> Unit,
    onOpenHome: (() -> Unit)?,
    options: SearchOptions,
    fieldController: (FieldController) -> Unit,
) {
    // Another engine or a scope, for this search only (null: the default engine).
    var chosen by remember(request) { mutableStateOf<SearchEngine?>(null) }
    val activeProviders = remember(chosen, providers) { chosen?.let(options.providersFor) ?: providers }
    // The handle on the on-screen field, handed to whoever asked (tests drive it).
    val controller = remember { FieldController() }
    SideEffect { fieldController(controller) }
    // A scope (Bookmarks, History, Tabs) narrows the suggestions; Enter still goes through the default.
    val submit: (String) -> Unit = { text ->
        val engine = chosen?.takeUnless { it.type == SearchEngine.Type.APPLICATION }
        if (engine != null) options.onSubmitWith(text, engine) else onSubmit(text)
    }
    // The typed text, as the control reports it (the completion's tail included when it stands).
    var field by remember(request) { mutableStateOf(request.initialText) }
    // Suggestions shown, top to bottom, for Up/Down; the highlighted one is previewed in the field.
    var visible by remember { mutableStateOf(emptyList<AwesomeBarConcept.SuggestionItem>()) }
    var highlighted by remember(request) { mutableIntStateOf(-1) }
    var hidden by remember(request) { mutableStateOf(emptySet<AwesomeBarConcept.GroupedSuggestion>()) }
    val preview = (visible.getOrNull(highlighted) as? AwesomeBarConcept.Suggestion)?.let { it.editSuggestion ?: it.title ?: it.description }
    // The on-screen field is the toolbar's control; when a suggestion is highlighted down the
    // list it shows the suggestion instead, and the tail mechanics belong to the control itself.
    val setField: (String, Boolean) -> Unit = { text, selectAll ->
        highlighted = -1
        field = text
        controller.apply(text, selectAll)
    }
    // Enter: the highlighted suggestion, else the field with any completion tail.
    val go: () -> Unit = {
        val item = visible.getOrNull(highlighted)
        val text = controller.text
        when {
            item != null -> onSuggestion(item)
            !text.isNullOrBlank() -> submit(text.trim())
        }
    }
    LaunchedEffect(Unit) { controller.raiseKeyboard() }
    // The pop-up's own keys; with a keyboard up they arrive through the pre-IME dispatch,
    // without one through the plain one, so one hook serves both.
    val onKey: (Int) -> Boolean = { keyCode ->
        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> {
                onDismiss()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN ->
                if (visible.isNotEmpty()) {
                    highlighted = (highlighted + 1).coerceAtMost(visible.size - 1)
                    true
                } else {
                    false
                }
            KeyEvent.KEYCODE_DPAD_UP ->
                if (highlighted >= 0) {
                    highlighted -= 1
                    true
                } else {
                    false
                }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (highlighted >= 0 || !controller.text.isNullOrBlank()) {
                    go()
                    true
                } else {
                    false
                }
            else -> false
        }
    }
    // Hiding the keyboard closes the pop-up while nothing has been typed; with a query, the
    // suggestions stay to be read.
    val imeShown = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var imeWasShown by remember { mutableStateOf(false) }
    val untouched = field.isBlank() || field == request.initialText
    var promptAnswered by remember(request) { mutableStateOf(false) }
    // The camera or the voice prompt hides the keyboard: that isn't the user closing the pop-up.
    var awaitingExtra by remember(request) { mutableStateOf(false) }
    DisposableEffect(options.extras) { onDispose { options.extras?.cancel() } }
    LaunchedEffect(imeShown) {
        if (imeShown) {
            imeWasShown = true
            // Back from the camera or voice prompt without a result: the keyboard rules again.
            awaitingExtra = false
        } else if (imeWasShown && untouched && options.closeOnKeyboardHide && !awaitingExtra) {
            onDismiss()
        }
    }
    // Rising from a bottom toolbar, the field sits at the bottom, nearest the toolbar and the thumb.
    val fieldAtBottom = (request.placement as? SearchPlacement.Anchored)?.toolbarAtTop == false
    StackColumn(reversed = fieldAtBottom, modifier = Modifier.padding(12.dp)) {
        InlineCompletingField(
            query = field,
            preview = preview,
            placeholder = stringResource(R.string.sideraven_address_placeholder),
            autocomplete = { text -> options.autocomplete(text, chosen) },
            onTextMirror = {
                field = it
                highlighted = -1
            },
            onCommit = go,
            onKey = onKey,
            controller = controller,
            leading = {
                if (options.engines.isEmpty()) {
                    Icon(painter = painterResource(iconsR.drawable.mozac_ic_search_24), contentDescription = null)
                } else {
                    EngineChip(
                        shown = chosen ?: options.defaultEngine,
                        engines = options.engines,
                        onPick = { chosen = it.takeUnless { engine -> engine == options.defaultEngine } },
                        onSettings = options.onSearchSettings,
                    )
                }
            },
            trailing = {
                val extras = options.extras
                if (field.isEmpty() && extras != null && (extras.canScanQr || extras.canUseVoice)) {
                    Row {
                        if (extras.canScanQr) {
                            FieldButton(iconsR.drawable.mozac_ic_qr_code_24, stringResource(R.string.sideraven_search_qr), SidebarTestTag.SEARCH_QR) {
                                awaitingExtra = true
                                extras.scanQr { scanned ->
                                    awaitingExtra = false
                                    // (selectAll = the second flag.) In the field, all selected:
                                    // seen before it is opened.
                                    setField(scanned, true)
                                }
                            }
                        }
                        if (extras.canUseVoice) {
                            FieldButton(iconsR.drawable.mozac_ic_microphone_24, stringResource(R.string.sideraven_search_voice), SidebarTestTag.SEARCH_VOICE) {
                                awaitingExtra = true
                                extras.voice { said ->
                                    awaitingExtra = false
                                    submit(said.trim())
                                }
                            }
                        }
                    }
                }
                if (field.isNotEmpty()) {
                    val clearLabel = stringResource(R.string.sideraven_search_clear)
                    Box(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) { setField("", false) }
                                .semantics { contentDescription = clearLabel }
                                .testTag(SidebarTestTag.SEARCH_CLEAR),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(iconsR.drawable.mozac_ic_cross_circle_fill_24), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            },
        )
        options.clipboardLink?.let { link ->
            ClipboardChip(link, onPaste = { setField(link, false) }, onGo = { onSubmit(link) })
        }
        // Asked once something is typed, as Fenix does; answered, it doesn't come back.
        val prompt = options.privatePrompt
        if (prompt != null && prompt.needed && !promptAnswered && field.isNotBlank() && field != request.initialText) {
            org.mozilla.fenix.search.awesomebar.PrivateSuggestionsCard(
                onSearchSuggestionsInPrivateModeAllowed = {
                    promptAnswered = true
                    prompt.answer(true)
                },
                onSearchSuggestionsInPrivateModeBlocked = {
                    promptAnswered = true
                    prompt.answer(false)
                },
                onLearnMoreClick = prompt::learnMore,
            )
        }
        if (onOpenHome != null) {
            Text(
                text = stringResource(R.string.sideraven_search_open_home),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onOpenHome)
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                        .testTag(SidebarTestTag.SEARCH_OPEN_HOME),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            AwesomeBar(
                text = field,
                colors =
                    AwesomeBarDefaults.colors(
                        background = Color.Transparent,
                        title = MaterialTheme.colorScheme.onSurface,
                    ),
                providers = activeProviders,
                onSuggestionClicked = { item -> onSuggestion(item) },
                // Autocomplete fills the field with the suggestion's text.
                onAutoComplete = { suggestion -> suggestion.editSuggestion?.let { setField(it, false) } },
                hiddenSuggestions = hidden,
                // Removing a history suggestion hides it here and deletes it from history.
                onRemoveClicked = { item ->
                    hidden = hidden + item
                    options.onRemoveSuggestion(item)
                },
                onVisibilityStateUpdated = { state -> visible = state.visibleProviderGroups.values.flatten() },
            )
        }
    }
}

/** The pop-up's entrance and exit (a popover, opened many times a day: short). */
private const val ENTER_MS = 180
private const val EXIT_MS = 120

/** The card grows from this scale at Full motion. */
private const val ENTER_SCALE = 0.97f

/**
 * Centred: the card as laid out by its parent. Anchored: the card over the toolbar, at least
 * [ANCHORED_MIN_WIDTH] wide, its top at the toolbar's top (top toolbar) or its bottom at the
 * toolbar's bottom, never below the keyboard (bottom toolbar).
 */
@Composable
private fun AnchoredOrCentred(
    placement: SearchPlacement,
    content: @Composable () -> Unit,
) {
    if (placement !is SearchPlacement.Anchored) {
        content()
        return
    }
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val toolbar = placement.toolbar
        val width = maxOf(toolbar.width.roundToInt(), ANCHORED_MIN_WIDTH.roundToPx()).coerceAtMost(constraints.maxWidth)
        val maxHeight =
            if (placement.toolbarAtTop) {
                (constraints.maxHeight - toolbar.top.roundToInt()).coerceAtLeast(0)
            } else {
                minOf(toolbar.bottom.roundToInt(), constraints.maxHeight)
            }
        val card = measurables.first().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = toolbar.left.roundToInt().coerceIn(0, (constraints.maxWidth - width).coerceAtLeast(0))
            val y =
                if (placement.toolbarAtTop) {
                    toolbar.top.roundToInt()
                } else {
                    minOf(toolbar.bottom.roundToInt(), constraints.maxHeight) - card.height
                }
            card.place(x, y.coerceAtLeast(0))
        }
    }
}

private val ANCHORED_MIN_WIDTH = 360.dp

/** A column whose children can be stacked bottom-up ([reversed]), each taking the height left. */
@Composable
private fun StackColumn(
    reversed: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        var remaining = if (loose.hasBoundedHeight) loose.maxHeight else Constraints.Infinity
        val placeables =
            measurables.map { measurable ->
                measurable.measure(loose.copy(maxHeight = remaining.coerceAtLeast(0))).also {
                    if (remaining != Constraints.Infinity) remaining -= it.height
                }
            }
        val width = maxOf(placeables.maxOfOrNull { it.width } ?: 0, loose.minWidth)
        layout(width, placeables.sumOf { it.height }) {
            var y = 0
            (if (reversed) placeables.asReversed() else placeables).forEach {
                it.place(0, y)
                y += it.height
            }
        }
    }
}

/**
 * The engine chip at the start of the field: the engine (or scope) this search uses; tap for
 * Fenix's engines, its scopes and its search settings.
 */
@Composable
private fun EngineChip(
    shown: SearchEngine?,
    engines: List<SearchEngine>,
    onPick: (SearchEngine) -> Unit,
    onSettings: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val label = shown?.let { stringResource(R.string.search_engine_selector_content_description, it.name) }.orEmpty()
    Box(
        modifier =
            Modifier
                .padding(start = 4.dp)
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = label }
                .testTag(SidebarTestTag.SEARCH_ENGINE_CHIP),
        contentAlignment = Alignment.Center,
    ) {
        if (shown != null) EngineIcon(shown) else Icon(painterResource(iconsR.drawable.mozac_ic_search_24), contentDescription = null)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = stringResource(R.string.search_header_menu_item_2),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            val (scopes, searchEngines) = engines.partition { it.type == SearchEngine.Type.APPLICATION }
            listOf(searchEngines, scopes).filter { it.isNotEmpty() }.forEach { group ->
                group.forEach { engine ->
                    DropdownMenuItem(
                        text = { Text(engine.name) },
                        leadingIcon = { EngineIcon(engine) },
                        onClick = {
                            open = false
                            onPick(engine)
                        },
                    )
                }
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_settings_menu_item)) },
                leadingIcon = { Icon(painterResource(iconsR.drawable.mozac_ic_settings_24), contentDescription = null) },
                onClick = {
                    open = false
                    onSettings()
                },
            )
        }
    }
}

/** An engine's icon; Fenix's scope icons are monochrome and take the text colour. */
@Composable
private fun EngineIcon(engine: SearchEngine) {
    Image(
        bitmap = engine.icon.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        colorFilter = if (engine.type == SearchEngine.Type.APPLICATION) ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant) else null,
    )
}

/** A link on the clipboard: Paste fills the field; Go opens it straight away. */
@Composable
private fun ClipboardChip(
    link: String,
    onPaste: () -> Unit,
    onGo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(colors.surfaceContainerHighest)
                .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconsR.drawable.mozac_ic_link_24), contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(stringResource(R.string.sideraven_search_clipboard), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(link, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onPaste, modifier = Modifier.testTag(SidebarTestTag.SEARCH_CLIPBOARD_PASTE)) {
            Text(stringResource(R.string.sideraven_search_paste))
        }
        TextButton(onClick = onGo, modifier = Modifier.testTag(SidebarTestTag.SEARCH_CLIPBOARD_GO)) {
            Text(stringResource(R.string.sideraven_search_go))
        }
    }
}

private const val SCRIM_ALPHA = 0.4f
