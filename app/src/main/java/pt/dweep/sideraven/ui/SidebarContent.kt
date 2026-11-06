/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import pt.dweep.sideraven.theme.SidebarFill
import pt.dweep.sideraven.theme.sidebarFill
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import kotlinx.coroutines.CoroutineStart
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import pt.dweep.sideraven.UnloadOthersFrom
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.ui.input.pointer.PointerType
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.composed
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.SidebarSpace
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import org.mozilla.fenix.tabstray.browser.compose.createListReorderState
import pt.dweep.sideraven.ESSENTIALS_CAP
import pt.dweep.sideraven.PinResult
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.ArrangedShares
import pt.dweep.sideraven.SpacesShown
import pt.dweep.sideraven.DEFAULT_SPACE_ACCENT
import pt.dweep.sideraven.CollapsedSection
import pt.dweep.sideraven.MAX_ARRANGED_SHARE
import pt.dweep.sideraven.SectionSizing
import pt.dweep.sideraven.SidebarControlKind
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarDesktopTabs
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.CloseButtons
import pt.dweep.sideraven.SidebarInactiveSection
import pt.dweep.sideraven.SidebarPinnedRow
import pt.dweep.sideraven.SidebarSide
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.TabListDirection
import pt.dweep.sideraven.SidebarWidth
import pt.dweep.sideraven.TabAudio
import pt.dweep.sideraven.theme.SideravenMotion
import pt.dweep.sideraven.theme.frameBackground
import mozilla.components.ui.icons.R as iconsR

/** Persistent Mode width at the Standard setting. */
val PersistentSidebarWidth = SidebarWidth.Standard.dp.dp

/** Collapsed Mode width. */
val CollapsedSidebarWidth = 56.dp

/** Width the Sidebar takes for [mode] at [width] (Sideraven Settings); 0 when hidden. */
fun sidebarWidth(
    mode: SidebarMode?,
    width: SidebarWidth = SidebarWidth.Standard,
): Dp =
    when (mode) {
        SidebarMode.Persistent -> width.dp.dp
        SidebarMode.Collapsed -> CollapsedSidebarWidth
        SidebarMode.AutoHide, null -> 0.dp
    }

/** Inactive Tabs Section keys: its header, and a prefix for its tabs (kept out of Tab Drags). */
private const val INACTIVE_HEADER_KEY = "sidebar.inactiveHeader"
private const val INACTIVE_KEY_PREFIX = "inactive:"

/** Desktop Tabs keys: its header, and a prefix for its (read-only, never dragged) rows. */
private const val DESKTOP_HEADER_KEY = "sidebar.desktopHeader"
private const val DESKTOP_KEY_PREFIX = "desktop:"

private fun isActiveTabKey(key: String) =
    key != LIST_HEADER_KEY && key != INACTIVE_HEADER_KEY && !key.startsWith(INACTIVE_KEY_PREFIX) &&
        key != DESKTOP_HEADER_KEY && !key.startsWith(DESKTOP_KEY_PREFIX)

/** The Desktop Tabs header: "On desktop (N)", expanding in place. */
@Composable
private fun DesktopTabsHeader(
    group: SidebarDesktopTabs,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val header = stringResource(R.string.sideraven_desktop_tabs_header, group.tabs.size)
    val expandState = stringResource(if (group.expanded) R.string.sideraven_expanded else R.string.sideraven_collapsed)
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (group.expanded) 180f else 0f,
            animationSpec = LocalSidebarMotion.current.fade(SideravenMotion.CHEVRON_ROTATE_MS),
            label = "desktopChevron",
        )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics {
                    contentDescription = header
                    stateDescription = expandState
                }
                .testTag(SidebarTestTag.DESKTOP_TABS_HEADER)
                .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_chevron_down_24),
            contentDescription = null,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevronRotation },
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_device_desktop_24),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.onSurfaceVariant,
        )
    }
}

/** One Desktop Tab: read-only, tapping opens it here. */
@Composable
private fun DesktopTabRow(
    tab: pt.dweep.sideraven.sync.DesktopTab,
    icon: android.graphics.Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sendingLabel = stringResource(R.string.sideraven_send_to_desktop_loading)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp)) {
            if (icon != null) {
                Image(bitmap = icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)))
            } else {
                LocalFaviconLoader.current(tab.url, 20.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = tab.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (tab.isSending) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).testTag(SidebarTestTag.DESKTOP_TAB_SENDING)
                    .semantics { contentDescription = sendingLabel },
                strokeWidth = 2.dp,
            )
        }
    }
}

/**
 * The Inactive Tabs Section header. Persistent: "Inactive (N)", expanding in place, with "Close
 * all inactive". Collapsed: a stacked-tabs icon with the count, revealing the full Sidebar.
 */
@Composable
private fun InactiveSectionHeader(
    section: SidebarInactiveSection,
    showLabels: Boolean,
    onToggle: () -> Unit,
    onReveal: () -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val header = stringResource(R.string.sideraven_inactive_header, section.count)
    val expandState = stringResource(if (section.expanded) R.string.sideraven_expanded else R.string.sideraven_collapsed)
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (section.expanded) 180f else 0f,
            animationSpec = LocalSidebarMotion.current.fade(SideravenMotion.CHEVRON_ROTATE_MS),
            label = "inactiveChevron",
        )

    if (!showLabels) {
        val revealLabel = stringResource(R.string.sideraven_inactive_show, section.count)
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button, onClick = onReveal)
                    .semantics { contentDescription = revealLabel }
                    .testTag(SidebarTestTag.INACTIVE_HEADER),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_tab_tray_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.onSurfaceVariant,
            )
            Text(
                text = section.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 2.dp),
            )
        }
        return
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics {
                    contentDescription = header
                    stateDescription = expandState
                }
                .testTag(SidebarTestTag.INACTIVE_HEADER)
                .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_chevron_down_24),
            contentDescription = null,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevronRotation },
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = header,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCloseAll, modifier = Modifier.size(40.dp).testTag(SidebarTestTag.INACTIVE_CLOSE_ALL)) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_delete_24),
                contentDescription = stringResource(R.string.sideraven_inactive_close_all),
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}


/** Key of the Tab List's leading spacer; not draggable. */
private const val LIST_HEADER_KEY = "sidebar.listHeader"

/** A picked-up tab grows slightly and casts a shadow. */
private const val DRAG_LIFT_SCALE = 1.03f

/** Sleeping tabs, Essentials and Pinned Tabs: lighter than a Ghost's dimming. */
internal const val SLEEP_ALPHA = 0.6f

/**
 * A Sleeping tab's, Essential's or Pinned Tab's favicon, drawn in grey; with the dimming, the one look
 * for sleep, by hand or by Auto-Sleep.
 */
internal fun Modifier.greyWhenSleeping(sleeping: Boolean): Modifier =
    if (!sleeping) {
        this
    } else {
        drawWithCache {
            val grey =
                androidx.compose.ui.graphics.Paint().apply {
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })
                }
            onDrawWithContent {
                drawIntoCanvas { it.saveLayer(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size), grey) }
                drawContent()
                drawIntoCanvas { it.restore() }
            }
        }
    }
private val DRAG_LIFT_ELEVATION = 8.dp

/** Titles start fading in once the Sidebar is this far from Collapsed to Persistent. */
private const val TITLE_REVEAL_START = 0.6f

/** Test tags for Sidebar UI tests. */
object SidebarTestTag {
    const val ROOT = "sidebar.root"
    const val TAB_LIST = "sidebar.tabList"
    const val TAB_ITEM = "sidebar.tabItem"
    const val NEW_TAB = "sidebar.newTab"
    const val CLOSE_BUTTON = "sidebar.closeButton"
    const val TOGGLE = "sidebar.toggle"
    const val SCRIM = "sidebar.scrim"
    const val OVERLAY_PANEL = "sidebar.overlayPanel"
    const val SWIPE_ZONE = "sidebar.swipeZone"
    const val CONTEXT_MENU = "sidebar.contextMenu"
    const val ESSENTIALS = "sidebar.essentials"
    const val ESSENTIAL = "sidebar.essential"
    const val PIN_ITEM = "sidebar.pinItem"
    const val MODE_SWITCH = "sidebar.modeSwitch"
    const val SPACE_ROW = "sidebar.spaceRow"
    const val SPACE_HEADER = "sidebar.spaceHeader"
    const val PIN_TARGET = "sidebar.pinTarget"
    const val ESSENTIALS_TARGET = "sidebar.essentialsTarget"
    const val UNPIN_TARGET = "sidebar.unpinTarget"
    const val DRAG_GHOST = "sidebar.dragGhost"
    const val SEARCH_SCRIM = "sidebar.searchScrim"
    const val SEARCH_ENGINE_CHIP = "sidebar.searchEngineChip"
    const val SEARCH_CLEAR = "sidebar.searchClear"
    const val SEARCH_QR = "sidebar.searchQr"
    const val SEARCH_VOICE = "sidebar.searchVoice"
    const val SEARCH_CLIPBOARD_PASTE = "sidebar.searchClipboardPaste"
    const val SEARCH_CLIPBOARD_GO = "sidebar.searchClipboardGo"
    const val NEW_SPACE = "sidebar.newSpace"
    const val SPACE_NAME_FIELD = "sidebar.spaceNameField"
    const val SPACE_CONFIRM = "sidebar.spaceConfirm"
    const val ZEN_ICONS_TAB = "sidebar.zenIconsTab"
    const val SPACE_MENU = "sidebar.spaceMenu"
    const val MOVE_TO_SPACE = "sidebar.moveToSpace"
    const val SPACE_SWITCHER = "sidebar.spaceSwitcher"
    const val SYNC_PROBLEM = "sidebar.syncProblem"
    const val SPACE_LOCAL = "sidebar.spaceLocal"
    const val DELETE_MESSAGE = "sidebar.deleteMessage"
    const val ADDRESS_BAR = "sidebar.addressBar"
    const val ADDRESS_PILL = "sidebar.addressPill"
    const val ADDRESS_BACK = "sidebar.addressBack"
    const val ADDRESS_FORWARD = "sidebar.addressForward"
    const val ADDRESS_RELOAD = "sidebar.addressReload"
    const val ADDRESS_LOADING = "sidebar.addressLoading"
    const val ADDRESS_RESET = "sidebar.addressReset"
    const val ADDRESS_STOP = "sidebar.addressStop"
    const val LAYOUT_BUTTON = "sidebar.layoutButton"
    const val ADDRESS_MENU = "sidebar.addressMenu"
    const val ADDRESS_SITE_INFO = "sidebar.addressSiteInfo"
    const val ADDRESS_CLOSE = "sidebar.addressClose"
    const val SYNCED_TABS = "sidebar.syncedTabs"
    const val GRAB_BAR = "sidebar.grabBar"
    const val ARRANGE_DONE = "sidebar.arrangeDone"
    const val ARRANGE_RESET = "sidebar.arrangeReset"
    const val SEARCH_POPUP = "sidebar.searchPopup"
    const val SEARCH_FIELD = "sidebar.searchField"
    const val SEARCH_OPEN_HOME = "sidebar.searchOpenHome"
    const val PINNED_TABS = "sidebar.pinnedTabs"
    const val PINNED_FOLD = "sidebar.pinnedFold"
    const val PINNED_STACK = "sidebar.pinnedStack"
    const val PINNED_TAB = "sidebar.pinnedTab"
    const val PIN_SPACE_ITEM = "sidebar.pinSpaceItem"
    const val PINNED_CLOSE = "sidebar.pinnedClose"
    const val RENAME_FIELD = "sidebar.renameField"
    const val FOLDER = "sidebar.folder"
    const val FOLDER_POPUP = "sidebar.folderPopup"
    const val MOVE_TO_FOLDER = "sidebar.moveToFolder"
    const val FOLDER_DELETE_KEEP = "sidebar.folderDeleteKeep"
    const val FOLDER_DELETE_REMOVE = "sidebar.folderDeleteRemove"
    const val RENAME_CONFIRM = "sidebar.renameConfirm"
    const val SPACE_DELETE_CONFIRM = "sidebar.spaceDeleteConfirm"
    const val AUDIO_BUTTON = "sidebar.audioButton"
    const val AUDIO_DOT = "sidebar.audioDot"
    const val MOUSE_EDGE = "sidebar.mouseEdge"
    const val FLAP = "sidebar.flap"
    const val INACTIVE_HEADER = "sidebar.inactiveHeader"
    const val INACTIVE_CLOSE_ALL = "sidebar.inactiveCloseAll"
    const val DESKTOP_TABS_HEADER = "sidebar.desktopTabsHeader"
    const val DESKTOP_TAB_SENDING = "sidebar.desktopTabSending"
}

/**
 * The Sidebar: Tab List plus Sidebar Controls. Renders [state] and emits [SidebarIntent]s; holds
 * no logic of its own beyond motion.
 *
 * Docked modes (Persistent, Collapsed): width changes animate over 220ms and the Content Area is
 * resized **once** per change via [onDockedWidthChanged] — after the Sidebar has grown over the
 * content, or before it shrinks — so the web page isn't re-laid out every frame. Switching to
 * Auto-Hide slides the docked Sidebar out to the left as the content takes the width.
 *
 * Auto-Hide: the Sidebar is revealed over the Content Area with a Scrim. [onOverlayActiveChanged]
 * tells the host to let this view cover the content while the overlay is on screen.
 */
@Composable
fun SidebarContent(
    state: SidebarUiState,
    onIntent: (SidebarIntent) -> Unit,
    modifier: Modifier = Modifier,
    onDockedWidthChanged: (Dp) -> Unit = {},
    onOverlayActiveChanged: (Boolean) -> Unit = {},
) {
    // The Sidebar's side (Sideraven Settings): on the right its placement mirrors (start is the
    // right edge, and slides and swipes run the other way); its content keeps the reading direction.
    val contentDirection = LocalLayoutDirection.current
    val right = state.side == SidebarSide.Right
    // Haptic ticks off (Sideraven Settings): every tick in the Sidebar goes nowhere.
    val haptics = LocalHapticFeedback.current.takeIf { state.look.haptics } ?: NoHaptics
    CompositionLocalProvider(
        LocalHapticFeedback provides haptics,
        LocalLayoutDirection provides if (right) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalSidebarSide provides SidebarSideLayout(if (right) -1f else 1f, contentDirection),
        LocalSidebarLook provides state.look,
        LocalSidebarMotion provides SidebarMotion(state.look.animations),
    ) {
    // The Sidebar's height, for placing the Flap along the edge.
    var edgeHeightPx by remember { mutableIntStateOf(0) }
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .onSizeChanged { edgeHeightPx = it.height }
                // Test tags double as resource ids, so the benchmarks' UiAutomator can find the Sidebar.
                .semantics { testTagsAsResourceId = true },
    ) {
        DockedSidebar(state = state, onIntent = onIntent, onDockedWidthChanged = onDockedWidthChanged)
        // Order matters: Compose sends a touch to the top-most sibling only, so the mouse strip
        // sits underneath and the Swipe Zone (which also reacts to mouse hover) goes on top.
        if (state.isVisible && state.mode == SidebarMode.AutoHide && !state.isRevealed) {
            MouseEdgeZone(onReveal = { onIntent(SidebarIntent.Reveal) })
        }
        // A pull from the edge: the hidden Sidebar comes out with the finger.
        val pull = remember { RevealPull() }
        val density = LocalDensity.current
        // On a narrow phone the tray leaves the page a margin to tap, whatever width was chosen.
        val trayWidth = minOf(state.width.dp.dp, LocalConfiguration.current.screenWidthDp.dp - TRAY_PAGE_MARGIN)
        val panelWidthPx = with(density) { trayWidth.toPx() }
        val overshootPx = with(density) { SideravenMotion.TRAY_OVERSHOOT_DP.dp.toPx() }
        // The Auto-Hide tray: the Sidebar and its Flap slide out together, one rigid piece.
        val tray = remember { SidebarTray(panelWidthPx, overshootPx) }
        // Where the Flap sits along the edge; while it's being moved, wherever the finger has it.
        var movingFlap by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(state.flapPosition) { movingFlap = null }
        val edge = FlapEdge(edgeHeightPx.toFloat(), with(density) { SwipeZoneHeight.toPx() }, movingFlap ?: state.flapPosition)
        if (state.isVisible && state.showsSwipeZone) {
            SwipeZone(pull = pull, panelWidthPx = panelWidthPx, edge = edge, onReveal = { onIntent(SidebarIntent.Reveal) })
        }
        // Auto-Hide's overlay; Collapsed Mode also reveals it for the Inactive Tabs Section.
        if (state.isVisible && (state.mode == SidebarMode.AutoHide || (state.mode == SidebarMode.Collapsed && state.isRevealed))) {
            AutoHideOverlay(state = state, onIntent = onIntent, onOverlayActiveChanged = onOverlayActiveChanged, pull = pull, tray = tray, trayWidth = trayWidth)
        } else {
            LaunchedEffect(Unit) { onOverlayActiveChanged(false) }
        }
        // On top of everything: the tray's handle, at the edge while hidden and on the Sidebar's
        // outer edge while it's out.
        if (state.isVisible && state.showsFlap) {
            SidebarFlap(
                revealed = state.isRevealed,
                tray = tray,
                pull = pull,
                panelWidthPx = panelWidthPx,
                edge = edge,
                onMove = { movingFlap = it },
                onIntent = onIntent,
            )
        }
    }
    }
}

/** Haptic feedback that does nothing (Haptic ticks off). */
private object NoHaptics : androidx.compose.ui.hapticfeedback.HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** The look settings (row density, close buttons) for every row below the Sidebar's root. */
internal val LocalSidebarLook = androidx.compose.runtime.staticCompositionLocalOf { SidebarLook() }

/** Which way the Sidebar faces: [dir] is 1 docked left, -1 docked right; its content's direction. */
internal class SidebarSideLayout(
    val dir: Float,
    val contentDirection: LayoutDirection,
)

internal val LocalSidebarSide = staticCompositionLocalOf { SidebarSideLayout(1f, LayoutDirection.Ltr) }

@Composable
private fun DockedSidebar(
    state: SidebarUiState,
    onIntent: (SidebarIntent) -> Unit,
    onDockedWidthChanged: (Dp) -> Unit,
) {
    val targetWidth = sidebarWidth(state.mode.takeIf { state.isVisible }, state.width)
    val dir = LocalSidebarSide.current.dir
    val width = remember { Animatable(targetWidth.value) }
    // Horizontal offset used to slide the docked Sidebar out to (and in from) Auto-Hide.
    val slide = remember { Animatable(0f) }
    val lastVisibleMode = remember { mutableStateOf(state.mode.takeIf { state.isVisible }) }
    val currentOnDockedWidthChanged by rememberUpdatedState(onDockedWidthChanged)
    // Keyboard mode changes snap: keyboard actions never animate. Nor does anything below Full motion.
    val spec: AnimationSpec<Float> =
        if (state.animateModeChanges && LocalSidebarMotion.current.moves) {
            tween(SideravenMotion.MODE_CHANGE_MS, easing = SideravenMotion.EaseOutStrong)
        } else {
            snap()
        }

    LaunchedEffect(targetWidth, state.mode, state.isVisible) {
        val target = targetWidth.value
        val previousMode = lastVisibleMode.value
        lastVisibleMode.value = state.mode.takeIf { state.isVisible }
        when {
            width.value == target -> currentOnDockedWidthChanged(targetWidth)
            // Docked -> Auto-Hide: content takes the width, the Sidebar slides out left.
            target == 0f && state.isVisible && state.mode == SidebarMode.AutoHide -> {
                currentOnDockedWidthChanged(targetWidth)
                slide.animateTo(-width.value, spec)
                width.snapTo(0f)
                slide.snapTo(0f)
            }
            // Auto-Hide -> docked: slide in from the left, then give the content its inset.
            width.value == 0f && previousMode == SidebarMode.AutoHide -> {
                width.snapTo(target)
                slide.snapTo(-target)
                slide.animateTo(0f, spec)
                currentOnDockedWidthChanged(targetWidth)
            }
            // Screen or tier gating hides and shows instantly.
            target == 0f || width.value == 0f -> {
                width.snapTo(target)
                currentOnDockedWidthChanged(targetWidth)
            }
            target > width.value -> {
                width.animateTo(target, spec)
                currentOnDockedWidthChanged(targetWidth)
            }
            else -> {
                currentOnDockedWidthChanged(targetWidth)
                width.animateTo(target, spec)
            }
        }
    }

    if (width.value == 0f) return

    // 0 at Collapsed width, 1 at Persistent width.
    val expansion =
        ((width.value - CollapsedSidebarWidth.value) / (state.width.dp - CollapsedSidebarWidth.value))
            .coerceIn(0f, 1f)
    // Titles appear after ~60% of the expansion and vanish immediately when collapsing.
    val titleAlpha =
        if (state.mode == SidebarMode.Collapsed) {
            0f
        } else {
            ((expansion - TITLE_REVEAL_START) / (1f - TITLE_REVEAL_START)).coerceIn(0f, 1f)
        }

    val density = LocalDensity.current
    CompositionLocalProvider(LocalSidebarSlide provides { with(density) { slide.value.dp.toPx() } * dir }) {
        SidebarPanel(
            state = state,
            onIntent = onIntent,
            titleAlpha = titleAlpha,
            modifier =
                Modifier
                    .width(width.value.dp)
                    .graphicsLayer { translationX = slide.value.dp.toPx() * dir }
                    .testTag(SidebarTestTag.ROOT),
        )
    }
}

/**
 * Auto-Hide Mode: the Sidebar revealed over the Content Area at full width with a Scrim.
 * Dismissed by tapping the Scrim, back (predictive back drags it along), or swiping it left;
 * selecting a tab, Essential or New Tab dismisses it via the intent handler. Swipes that start
 * on a tab close the tab instead, and the Sidebar stays open.
 */
@Composable
private fun AutoHideOverlay(
    state: SidebarUiState,
    onIntent: (SidebarIntent) -> Unit,
    onOverlayActiveChanged: (Boolean) -> Unit,
    pull: RevealPull,
    tray: SidebarTray,
    trayWidth: Dp,
) {
    val density = LocalDensity.current
    val panelWidthPx = with(density) { trayWidth.toPx() }
    val overshootPx = with(density) { SideravenMotion.TRAY_OVERSHOOT_DP.dp.toPx() }
    val trayColor = pt.dweep.sideraven.theme.LocalFrameTint.current?.middle ?: MaterialTheme.colorScheme.surface
    // Offsets below are inward-positive; [dir] turns them into screen directions.
    val dir = LocalSidebarSide.current.dir
    val offset = tray.offset
    // Below Full motion the panel doesn't slide: it is placed at once and fades (Reduced) or not (Off).
    val fadeIn = tray.alpha
    val motion = LocalSidebarMotion.current
    val currentOnOverlayActiveChanged by rememberUpdatedState(onOverlayActiveChanged)
    val closeLabel = stringResource(R.string.sideraven_close_sidebar)

    LaunchedEffect(state.isRevealed) {
        if (state.isRevealed) {
            currentOnOverlayActiveChanged(true)
            if (motion.moves) {
                // Let go mid-pull, it carries on at the finger's speed; otherwise the usual slide.
                val fling = pull.releaseVelocity.also { pull.releaseVelocity = 0f }
                if (fling > 0f) {
                    offset.animateTo(0f, motion.springSettle(), fling)
                } else {
                    offset.animateTo(0f, tween(SideravenMotion.REVEAL_MS, easing = SideravenMotion.EaseOutStrong))
                }
            } else {
                fadeIn.snapTo(0f)
                offset.snapTo(0f)
                fadeIn.animateTo(1f, motion.fade(SideravenMotion.REVEAL_MS))
            }
        } else {
            if (motion.moves) {
                if (offset.isRunning && offset.targetValue == -panelWidthPx) {
                    // A fling is already carrying it out.
                    snapshotFlow { offset.isRunning }.first { !it }
                } else {
                    offset.animateTo(-panelWidthPx, tween(SideravenMotion.DISMISS_MS, easing = SideravenMotion.EaseOutStrong))
                }
            } else {
                fadeIn.animateTo(0f, motion.fade(SideravenMotion.DISMISS_MS))
                offset.snapTo(-panelWidthPx)
                fadeIn.snapTo(1f)
            }
            currentOnOverlayActiveChanged(false)
        }
    }

    PredictiveBackHandler(enabled = state.isRevealed) { progress ->
        try {
            // Below Full motion, back fades the panel instead of sliding it.
            progress.collect { event -> if (motion.moves) offset.snapTo(-panelWidthPx * event.progress) else fadeIn.snapTo(1f - event.progress) }
            onIntent(SidebarIntent.Dismiss)
        } catch (e: CancellationException) {
            if (motion.moves) offset.animateTo(0f, motion.move(SideravenMotion.REVEAL_MS)) else fadeIn.animateTo(1f, motion.fade(SideravenMotion.REVEAL_MS))
            throw e
        }
    }

    // 0 hidden, 1 fully revealed.
    val progress = (1f + offset.value / panelWidthPx).coerceIn(0f, 1f)
    val hidden = progress == 0f && !state.isRevealed
    // A width change while hidden must not leave a sliver showing.
    LaunchedEffect(panelWidthPx) {
        offset.updateBounds(-panelWidthPx, overshootPx)
        if (!state.isRevealed) offset.snapTo(-panelWidthPx)
    }
    // A pull from the edge moves the hidden panel 1:1; let go short of opening, it settles back.
    val currentRevealed by rememberUpdatedState(state.isRevealed)
    LaunchedEffect(pull) {
        // collectLatest: pulling again interrupts a settle back. Below Full motion nothing moves with
        // the finger; letting go past the threshold opens it the usual way (fade or instant).
        snapshotFlow { pull.pulling to pull.px }.collectLatest { (pulling, px) ->
            if (currentRevealed || !motion.moves) return@collectLatest
            if (pulling) {
                currentOnOverlayActiveChanged(true)
                offset.snapTo(-panelWidthPx + px)
            } else if (px == 0f && offset.value > -panelWidthPx) {
                offset.animateTo(-panelWidthPx, motion.springSettle())
                currentOnOverlayActiveChanged(false)
            }
        }
    }

    Box(
        modifier =
            Modifier
                // Hidden, the panel stays composed and measured (a reveal slides it in as one finished
                // sheet, scrolled where it was) but isn't placed, drawn or announced, and takes no
                // room, so the wrap-content host leaves the page touchable.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    if (hidden) layout(0, 0) {} else layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)
                .fillMaxSize(),
    ) {
        if (!hidden) Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress * fadeIn.value }
                    .background(Color.Black.copy(alpha = SideravenMotion.SCRIM_ALPHA))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = closeLabel,
                    ) { onIntent(SidebarIntent.Dismiss) }
                    .semantics { contentDescription = closeLabel }
                    .testTag(SidebarTestTag.SCRIM),
        )
        CompositionLocalProvider(LocalSidebarSlide provides { offset.value * dir }) {
        SidebarPanel(
            state = state,
            onIntent = onIntent,
            titleAlpha = 1f,
            modifier =
                Modifier
                    .width(trayWidth)
                    // Springing past its open edge, the tray stays solid: its colour reaches back to the screen edge.
                    .drawBehind {
                        val extra = overshootPx * 2
                        drawRect(trayColor, topLeft = Offset(if (dir > 0f) -extra else size.width, 0f), size = Size(extra, size.height))
                    }
                    .graphicsLayer {
                        translationX = offset.value * dir
                        alpha = fadeIn.value
                    }
                    .dragsTray(tray, panelWidthPx) { onIntent(SidebarIntent.Dismiss) }
                    .testTag(SidebarTestTag.OVERLAY_PANEL),
        )
        }
    }
}

/** Swipe Zone height: Android lets an app reclaim at most 200dp of each edge. */
val SwipeZoneHeight = 200.dp

/** Swipe Zone width: covers the system back gesture's edge area. */
val SwipeZoneWidth = 24.dp


/**
 * The Swipe Zone: an invisible strip, vertically centred on the left edge, excluded from the
 * system back gesture so a plain swipe in reveals the Sidebar. Outside it, back wins.
 */
@Composable
private fun BoxScope.SwipeZone(
    pull: RevealPull,
    panelWidthPx: Float,
    edge: FlapEdge,
    onReveal: () -> Unit,
) {
    val currentOnReveal by rememberUpdatedState(onReveal)
    Box(
        modifier =
            Modifier
                // Centred on the Flap, wherever it was moved.
                .align(Alignment.TopStart)
                .offset { IntOffset(0, (edge.centrePx - SwipeZoneHeight.toPx() / 2f).roundToInt()) }
                .width(SwipeZoneWidth)
                .height(SwipeZoneHeight)
                .systemGestureExclusion()
                .revealOnMouseDwell { currentOnReveal() }
                .pullToReveal(pull, panelWidthPx) { currentOnReveal() }
                .testTag(SidebarTestTag.SWIPE_ZONE),
    )
}

/** How long a mouse pointer rests at the left edge before the Auto-Hide Sidebar reveals. */
private const val MOUSE_EDGE_DWELL_MS = 150L

/** Width of the mouse edge zone: a sliver, so touch input beneath it is barely affected. */
private val MouseEdgeWidth = 4.dp

/**
 * Auto-Hide with a mouse: resting the pointer at the left edge reveals the Sidebar, as on desktop.
 * Reacts to mouse hover only; touch never produces hover events.
 */
@Composable
private fun BoxScope.MouseEdgeZone(onReveal: () -> Unit) {
    val currentOnReveal by rememberUpdatedState(onReveal)
    Box(
        modifier =
            Modifier
                .align(Alignment.CenterStart)
                .width(MouseEdgeWidth)
                .fillMaxHeight()
                .revealOnMouseDwell { currentOnReveal() }
                .testTag(SidebarTestTag.MOUSE_EDGE),
    )
}

/** Calls [onReveal] once a mouse pointer has rested on this element for [MOUSE_EDGE_DWELL_MS]. */
private fun Modifier.revealOnMouseDwell(onReveal: () -> Unit): Modifier =
    pointerInput(Unit) {
        coroutineScope {
            var dwell: Job? = null
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val isMouse = event.changes.any { it.type == PointerType.Mouse }
                    when (event.type) {
                        PointerEventType.Enter ->
                            if (isMouse) {
                                dwell?.cancel()
                                dwell = launch {
                                    delay(MOUSE_EDGE_DWELL_MS)
                                    onReveal()
                                }
                            }
                        PointerEventType.Exit -> dwell?.cancel()
                        else -> Unit
                    }
                }
            }
        }
    }

/** Sidebar Flap: visible handle size, and its (larger) touch target. */
private val FlapVisualWidth = 12.dp
private val FlapVisualHeight = 64.dp
private val FlapTouchWidth = 32.dp
private val FlapTouchHeight = 96.dp

/**
 * The Sidebar Flap: the Auto-Hide tray's handle. Hidden, it sits at the edge; it comes
 * out with the Sidebar, fixed to its outer edge. Tap it to open or close the Sidebar, drag it to pull
 * the Sidebar out or push it back, or hold it and drag along the edge to move it.
 */
@Composable
private fun BoxScope.SidebarFlap(
    revealed: Boolean,
    tray: SidebarTray,
    pull: RevealPull,
    panelWidthPx: Float,
    edge: FlapEdge,
    onMove: (Float) -> Unit,
    onIntent: (SidebarIntent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (revealed) R.string.sideraven_close_sidebar else R.string.sideraven_open_sidebar)
    val moveUpLabel = stringResource(R.string.sideraven_move_handle_up)
    val moveDownLabel = stringResource(R.string.sideraven_move_handle_down)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val currentRevealed by rememberUpdatedState(revealed)
    val currentEdge by rememberUpdatedState(edge)
    val dir = LocalSidebarSide.current.dir
    var held by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, (edge.centrePx - FlapTouchHeight.toPx() / 2f).roundToInt()) }
                // Part of the tray: as far out as the Sidebar is, so the two move as one.
                .graphicsLayer {
                    val out = tray.offset.value + panelWidthPx
                    translationX = out * dir
                    alpha = if (out > 0f) tray.alpha.value else 1f
                }
                .size(FlapTouchWidth, FlapTouchHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = label,
                ) { currentOnIntent(if (currentRevealed) SidebarIntent.Dismiss else SidebarIntent.Reveal) }
                .then(
                    if (revealed) {
                        Modifier.dragsTray(tray, panelWidthPx) { currentOnIntent(SidebarIntent.Dismiss) }
                    } else {
                        Modifier.pullToReveal(pull, panelWidthPx) { currentOnIntent(SidebarIntent.Reveal) }
                    },
                )
                // Innermost, so a hold claims the finger before the drags and the tap above see it.
                .holdToMove(
                    travelPx = { currentEdge.travelPx },
                    from = { currentEdge.position },
                    onHeld = { held = it },
                    onMove = onMove,
                    onDrop = { currentOnIntent(SidebarIntent.MoveFlap(it)) },
                )
                .semantics {
                    contentDescription = label
                    // TalkBack's way to move the handle (holding and dragging is the touch way).
                    customActions =
                        buildList {
                            if (edge.position > 0f) add(CustomAccessibilityAction(moveUpLabel) { currentOnIntent(SidebarIntent.MoveFlap(flapStep(currentEdge.position, -1))); true })
                            if (edge.position < 1f) add(CustomAccessibilityAction(moveDownLabel) { currentOnIntent(SidebarIntent.MoveFlap(flapStep(currentEdge.position, 1))); true })
                        }
                }
                .testTag(SidebarTestTag.FLAP),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .size(FlapVisualWidth, FlapVisualHeight)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    // The Sidebar's own colour: the handle reads as part of the tray.
                    .background(pt.dweep.sideraven.theme.LocalFrameTint.current?.middle ?: colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            // A short grip line, brighter while held.
            Box(
                modifier =
                    Modifier
                        .size(3.dp, 20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.onSurfaceVariant.copy(alpha = if (held) 1f else 0.6f)),
            )
        }
    }
}

/**
 * The Auto-Hide tray's place: [offset] is how far (px, inward-positive) the Sidebar is out, from
 * minus its width (hidden) to 0 (open); [alpha] fades it below Full motion. The Sidebar and its
 * Flap both follow it.
 */
internal class SidebarTray(
    widthPx: Float,
    overshootPx: Float,
) {
    // Bounded: hidden can't be passed, and open only by a small spring past the edge.
    val offset = Animatable(-widthPx).apply { updateBounds(-widthPx, overshootPx) }
    val alpha = Animatable(1f)
}

/**
 * Where the Flap and the Swipe Zone sit along an edge [heightPx] tall: [position] 0 puts the Swipe
 * Zone ([zonePx] tall) at the top, 1 at the bottom; both are centred on [centrePx].
 */
internal class FlapEdge(
    private val heightPx: Float,
    private val zonePx: Float,
    val position: Float,
) {
    /** How far (px) the centre travels from the top position to the bottom one. */
    val travelPx: Float get() = (heightPx - zonePx).coerceAtLeast(0f)

    val centrePx: Float get() = if (heightPx <= zonePx) heightPx / 2f else zonePx / 2f + position * travelPx
}

/**
 * Hold, then drag along the edge: reports each new position (0 top, 1 bottom) to [onMove], ticking
 * at the middle and at either end, and the last to [onDrop] when let go. A hold let go without
 * moving does nothing, and is never a tap.
 */
private fun Modifier.holdToMove(
    travelPx: () -> Float,
    from: () -> Float,
    onHeld: (Boolean) -> Unit,
    onMove: (Float) -> Unit,
    onDrop: (Float) -> Unit,
): Modifier =
    composed {
        val haptics by rememberUpdatedState(LocalHapticFeedback.current)
        val currentOnHeld by rememberUpdatedState(onHeld)
        val currentOnMove by rememberUpdatedState(onMove)
        val currentOnDrop by rememberUpdatedState(onDrop)
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val hold = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                hold.consume()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnHeld(true)
                val travel = travelPx()
                var position = from()
                var moved = false
                drag(hold.id) { change ->
                    val step = change.positionChange().y
                    change.consume()
                    if (travel <= 0f || step == 0f) return@drag
                    val before = position
                    position = (position + step / travel).coerceIn(0f, 1f)
                    if (position != before) {
                        moved = true
                        val crossedMiddle = (before < 0.5f) != (position < 0.5f)
                        val reachedEnd = position == 0f || position == 1f
                        if (crossedMiddle || reachedEnd) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        currentOnMove(position)
                    }
                }
                // The finger lifting after a hold is not a tap.
                currentEvent.changes.forEach { it.consume() }
                currentOnHeld(false)
                if (moved) {
                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    currentOnDrop(position)
                }
            }
        }
    }

/**
 * Dragging the open tray (the Sidebar or its Flap): it follows the finger; let go a third of the
 * way in, or flung toward the edge, it leaves at the finger's speed; otherwise it settles open.
 */
private fun Modifier.dragsTray(
    tray: SidebarTray,
    panelWidthPx: Float,
    onDismiss: () -> Unit,
): Modifier =
    composed {
        val dir = LocalSidebarSide.current.dir
        val motion = LocalSidebarMotion.current
        val scope = rememberCoroutineScope()
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        val offset = tray.offset
        draggable(
            orientation = Orientation.Horizontal,
            state =
                rememberDraggableState { delta ->
                    // Applied at once, not queued: a queued move left over when the finger lifts
                    // would cancel the settle that follows and leave the tray where it was.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo((offset.value + delta * dir).coerceIn(-panelWidthPx, 0f)) }
                },
            onDragStopped = { velocity ->
                val inward = velocity * dir
                if (inward < -DISMISS_FLING_VELOCITY || offset.value < -panelWidthPx / 3f) {
                    // Leaves at the finger's speed; the dismissal waits for it, not restarts it.
                    scope.launch { offset.animateTo(-panelWidthPx, motion.springSettle(), inward) }
                    currentOnDismiss()
                } else {
                    offset.animateTo(0f, motion.springSettle(), inward)
                }
            },
        )
    }

/** One TalkBack step of the handle along the edge: a tenth of its travel, rounded to tenths. */
internal fun flapStep(
    position: Float,
    direction: Int,
): Float = (round(position * 10f + direction) / 10f).coerceIn(0f, 1f)

/** Room (dp) the tray always leaves the page on a narrow phone. */
private val TRAY_PAGE_MARGIN = 56.dp

/** Fling speed (px/s) that closes the revealed Sidebar, or opens it from the edge, regardless of distance. */
private const val DISMISS_FLING_VELOCITY = 1000f

/** A pull from the Swipe Zone or Flap: how far (px, inward) the finger has drawn the hidden Sidebar out. */
internal class RevealPull {
    var px by mutableFloatStateOf(0f)
    var pulling by mutableStateOf(false)

    /** The finger's inward speed (px/s) when a pull let go to open; the opening carries it on. */
    var releaseVelocity = 0f
}

/**
 * Drags from the edge pull the hidden Sidebar out with the finger. Let go past a third
 * of its width, or with an inward fling, it opens; otherwise it settles back.
 */
private fun Modifier.pullToReveal(
    pull: RevealPull,
    panelWidthPx: Float,
    onReveal: () -> Unit,
): Modifier =
    composed {
        val dir = LocalSidebarSide.current.dir
        val currentOnReveal by rememberUpdatedState(onReveal)
        pointerInput(panelWidthPx, dir) {
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = {
                    tracker.resetTracking()
                    pull.px = 0f
                    pull.pulling = true
                },
                onHorizontalDrag = { change, delta ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    pull.px = (pull.px + delta * dir).coerceIn(0f, panelWidthPx)
                },
                onDragEnd = {
                    val inward = tracker.calculateVelocity().x * dir
                    val opens = pull.px > panelWidthPx / 3f || inward > DISMISS_FLING_VELOCITY
                    pull.pulling = false
                    if (opens) {
                        pull.releaseVelocity = inward.coerceAtLeast(0f)
                        currentOnReveal()
                    }
                    pull.px = 0f
                },
                onDragCancel = {
                    pull.pulling = false
                    pull.px = 0f
                },
            )
        }
    }

/**
 * The private Sidebar's title, as in Zen: "Private browsing" with the private glyph, or the glyph
 * alone when Collapsed.
 */
@Composable
private fun PrivateTitle(showLabels: Boolean) {
    val title = stringResource(R.string.sideraven_private_title)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = if (showLabels) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.sideraven_ic_private_24),
            contentDescription = if (showLabels) null else title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showLabels) {
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** The Sidebar's column: Tab List, Divider, Sidebar Controls. */
@Composable
private fun SidebarPanel(
    state: SidebarUiState,
    onIntent: (SidebarIntent) -> Unit,
    titleAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val showLabels = titleAlpha > 0f
    // The panel's content keeps the reading direction, whichever side it docks on.
    CompositionLocalProvider(LocalLayoutDirection provides LocalSidebarSide.current.contentDirection) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxHeight()
                .frameBackground(),
    ) {
    val panelHeight = maxHeight
    val drag = remember { SidebarDragState() }
    CompositionLocalProvider(LocalZenSyncOn provides state.zenSyncOn, LocalSidebarDrag provides drag) {
    // Stay clear of the status bar, navigation bar and cutouts (edge-to-edge).
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                // Only what Fenix's own content padding doesn't already clear (web pages pad, home doesn't).
                .remainingInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + if (LocalSidebarSide.current.dir < 0f) WindowInsetsSides.End else WindowInsetsSides.Start),
                )
                .onGloballyPositioned { drag.contentTop = it.boundsInRoot().top },
    ) {
        SideEffect { drag.offered = if (state.isPrivate) emptySet() else DragZone.entries.toSet() }
        if (state.isPrivate) PrivateTitle(showLabels)
        var draggingTabKey by remember { mutableStateOf<String?>(null) }
        // Drop on a Space icon: moves the tab to that Space.
        val spaceIconBounds = remember { mutableStateMapOf<String, Rect>() }
        var listOrigin by remember { mutableStateOf(Offset.Zero) }
        var spaceDropTarget by remember { mutableStateOf<String?>(null) }
        // The pinned block (drop a tab there to pin it to the Space) and the Essentials Section.
        // A section under the finger lights up as the drop target.
        val hoveredZone = drag.hovered
        val essentialsHighlight =
            when {
                hoveredZone != DragZone.Essentials -> DropTarget.None
                state.pinBlock == null -> DropTarget.PinAccepted
                else -> DropTarget.PinRejected
            }
        val pinHighlight =
            when {
                hoveredZone != DragZone.Pin -> DropTarget.None
                state.spacePinBlock == null -> DropTarget.PinAccepted
                else -> DropTarget.PinRejected
            }
        fun spaceAt(position: Offset): String? {
            val point = listOrigin + position
            return spaceIconBounds.entries
                .firstOrNull { (id, bounds) -> id != state.currentSpace?.id && bounds.contains(point) }
                ?.key
        }
        val haptics = LocalHapticFeedback.current
        // Section Sizing (Collapsed Mode): the Tab List keeps its share of the strip; Essentials and
        // Pinned Tabs share the rest, each taking what it holds (its scroll's viewport plus what's past it).
        val essentialsScroll = rememberScrollState()
        val pinnedScroll = rememberScrollState()
        val density = LocalDensity.current
        val sharedHeight = drag.essentialsSection.height + drag.pinnedSection.height + drag.tabList.height
        // Arrange Mode: while a grab bar is dragged its size shows live; otherwise the remembered sizes.
        var draggedShares by remember { mutableStateOf<ArrangedShares?>(null) }
        var dragging by remember { mutableStateOf(false) }
        var lettingGo by remember { mutableIntStateOf(0) }
        LaunchedEffect(state.arranged) { if (!dragging) draggedShares = null }
        LaunchedEffect(lettingGo) {
            if (lettingGo == 0) return@LaunchedEffect
            // By then the stored size has reached the state (or was already the same).
            withFrameNanos {}
            withFrameNanos {}
            if (!dragging) draggedShares = null
        }
        val arrangeIntent: (SidebarIntent) -> Unit = { intent ->
            if (intent == SidebarIntent.ResetArrangement) draggedShares = null
            onIntent(intent)
        }
        val arranged = draggedShares ?: state.arranged
        val sectionMax =
            if (showLabels) {
                null
            } else {
                val essentialsHeld = contentHeight(essentialsScroll, state.essentials.isNotEmpty())
                val pinnedHeld = contentHeight(pinnedScroll, state.pinned.isNotEmpty() || state.folders.isNotEmpty())
                if (sharedHeight > 0f && essentialsHeld != null && pinnedHeld != null) {
                    SectionSizing.maxHeights(
                        sharedHeight,
                        essentialsHeld,
                        pinnedHeld,
                        arrangedEssentials = arranged.essentials?.times(sharedHeight),
                        arrangedPinned = arranged.pinned?.times(sharedHeight),
                    )
                } else {
                    null
                }
            }
        // A grab bar in Arrange Mode: drags its section's height, live, and remembers it when let go.
        @Composable
        fun ArrangeBar(
            section: CollapsedSection,
            label: String,
        ) {
            val own = if (section == CollapsedSection.Essentials) drag.essentialsSection else drag.pinnedSection
            val other = if (section == CollapsedSection.Essentials) drag.pinnedSection else drag.essentialsSection
            val scroll = if (section == CollapsedSection.Essentials) essentialsScroll else pinnedScroll
            // As far as the Tab List and the other section allow, and no further than the section holds. A plain
            // value, so the remembered lambdas below pick up each frame's (a function reference wouldn't).
            val limit = minOf(sharedHeight * MAX_ARRANGED_SHARE - other.height, contentHeight(scroll, shown = true) ?: Float.MAX_VALUE)
            GrabBar(
                label = label,
                height = { own.height },
                room = { limit },
                onDragging = { px ->
                    if (sharedHeight > 0f) {
                        dragging = true
                        draggedShares = (draggedShares ?: state.arranged).with(section, px / sharedHeight)
                    }
                },
                onLetGo = {
                    dragging = false
                    draggedShares?.get(section)?.let { onIntent(SidebarIntent.ArrangeSection(section, it)) }
                    lettingGo++
                },
                onStep = { by ->
                    if (sharedHeight > 0f) {
                        onIntent(SidebarIntent.ArrangeSection(section, (own.height + by * sharedHeight).coerceIn(0f, limit.coerceAtLeast(0f)) / sharedHeight))
                    }
                },
            )
        }
        // Until everything is measured: a share each that still leaves the Tab List its own.
        fun maxHeightDp(px: Float?) = px?.let { with(density) { it.toDp() } } ?: (panelHeight * ((1f - SectionSizing.TAB_LIST_SHARE) / 2f))
        // An empty section takes no room; during a drag the drag layer draws its target instead.
        val essentialsShown = state.essentials.isNotEmpty()
        // Dividers take the Space's own colour when it has one; a Space on the default colour keeps the neutral line.
        val dividerTint = spaceDividerTint(state.currentSpace?.accent)
        // Above the section, so a newly pinned Essential is known across mode and layout changes.
        val newEssentials = rememberNewKeys(state.essentials.map { it.url })
        LaunchedEffect(essentialsShown) { if (!essentialsShown) drag.essentialsSection = Rect.Zero }
        if (essentialsShown) {
            Box(
                Modifier
                    .animateHeight(if (dragging) snap() else LocalSidebarMotion.current.move(SideravenMotion.MODE_CHANGE_MS, SideravenMotion.EaseInOutStrong))
                    .onGloballyPositioned { drag.essentialsSection = it.boundsInRoot() }
                    .ignoringTouches(state.arranging),
            ) {
                EssentialsSection(
                    essentials = state.essentials,
                    newUrls = newEssentials,
                    asGrid = showLabels,
                    collapsedMaxHeight = maxHeightDp(sectionMax?.essentials),
                    collapsedScroll = essentialsScroll,
                    onIntent = onIntent,
                    dropTarget = essentialsHighlight,
                    canPinToSpace = state.spaces.isNotEmpty(),
                )
            }
            // The Divider between the Essentials Section and the Tab List; a grab bar in Arrange Mode.
            if (state.arranging) ArrangeBar(CollapsedSection.Essentials, stringResource(R.string.sideraven_resize_essentials)) else SectionDivider(dividerTint)
        }
        val moveTargets = state.spaces.filterNot { it.isCurrent }
        val tabMenuExtras: @Composable (String, () -> Unit) -> Unit = { tabId, dismiss ->
            if (state.desktopSendingOn && !state.isPrivate && state.currentSpace?.isLocal == false &&
                state.allTabs.firstOrNull { it.id == tabId }?.let { pt.dweep.sideraven.sync.desktopSendableUrl(it.url) } == true
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sideraven_send_to_desktop)) },
                    onClick = {
                        dismiss()
                        onIntent(SidebarIntent.SendToDesktop(tabId))
                    },
                )
            }
            // Unload: not for the tab on screen, nor one already asleep.
            state.allTabs.firstOrNull { it.id == tabId }?.takeUnless { it.isSelected || it.sleeping }?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sideraven_unload)) },
                    onClick = {
                        dismiss()
                        onIntent(SidebarIntent.UnloadTab(tabId))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sideraven_unload_others)) },
                onClick = {
                    dismiss()
                    onIntent(SidebarIntent.UnloadOthers(UnloadOthersFrom.Tab(tabId)))
                },
            )
            if (state.spaces.isNotEmpty()) {
                PinToSpaceMenuItem(block = state.spacePinBlock) {
                    dismiss()
                    onIntent(SidebarIntent.PinToSpace(tabId))
                }
            }
            if (moveTargets.isNotEmpty()) MoveToSpaceMenuItems(moveTargets) { spaceId ->
                dismiss()
                onIntent(SidebarIntent.MoveTabToSpace(tabId, spaceId))
            }
        }
        val spaceEnter = rememberSpaceSwitchMotion(state)
        // The Space's Pinned Tabs, then a Divider, above its Tab List.
        val pinnedShown = state.pinned.isNotEmpty() || state.folders.isNotEmpty()
        // Pinned Fold: only the Pinned Tab on screen stays (not while arranging, where the Divider is the grab bar).
        val folded = state.pinnedFolded && !state.arranging
        val shownPins = if (folded) state.pinned.filter { it.isSelected } else state.pinned
        LaunchedEffect(pinnedShown) { if (!pinnedShown) drag.pinnedSection = Rect.Zero }
        // Spaces shown as a Header: one row above the Pinned Tabs, as in the prototype, not down by
        // the controls — the icon row's slot then stays empty and takes no room.
        val spaceHeader = state.look.spacesShown == SpacesShown.Header && showLabels && state.spaces.isNotEmpty()
        if (spaceHeader) {
            SpaceRow(
                spaces = state.spaces,
                onIntent = onIntent,
                syncProblem = state.syncProblem,
                zenSyncOn = state.zenSyncOn,
                dropTarget = spaceDropTarget,
                onIconBounds = { id, bounds -> spaceIconBounds[id] = bounds },
                header =
                    SpaceHeaderFold(
                        folded = folded,
                        canFold = pinnedShown && !state.arranging,
                        onToggle = { onIntent(SidebarIntent.TogglePinnedFold) },
                    ),
                modifier = Modifier.graphicsLayer { spaceEnter.apply(this) },
            )
        }
        if (pinnedShown) {
            Box(
                Modifier
                    .onGloballyPositioned { drag.pinnedSection = it.boundsInRoot() }
                    .graphicsLayer { spaceEnter.apply(this) }
                    .ignoringTouches(state.arranging),
            ) {
                PinnedTabsBlock(
                    pinned = shownPins,
                    showLabels = showLabels,
                    maxHeight = if (showLabels) panelHeight / 3 else maxHeightDp(sectionMax?.pinned),
                    scroll = pinnedScroll,
                    onIntent = onIntent,
                    rows = if (folded) shownPins.map { SidebarPinnedRow.PinRow(it, 0) } else state.pinnedRows,
                    folders = if (folded) emptyList() else state.folders,
                    keepWhenEmpty = folded,
                    dropTarget = pinHighlight,
                    resizing = dragging,
                )
            }
            if (state.arranging) {
                ArrangeBar(CollapsedSection.Pinned, stringResource(R.string.sideraven_resize_pinned))
            } else {
                PinnedFoldDivider(folded = folded, count = state.pinned.size, showLabels = showLabels, tint = dividerTint) { onIntent(SidebarIntent.TogglePinnedFold) }
            }
        }
        key(state.currentSpace?.id) {
        val listState = rememberLazyListState()
        // Tab List Direction, and where each block starts in the list (the header spacer is index 0).
        val bottomUp = LocalSidebarLook.current.tabListDirection == TabListDirection.BottomUp
        val desktopShown = state.desktopTabs?.takeIf { showLabels }
        val desktopItems = desktopShown?.let { 1 + if (it.expanded) it.tabs.size else 0 } ?: 0
        val inactiveItems = state.inactive?.let { 1 + if (it.expanded && showLabels) it.tabs.size else 0 } ?: 0
        val tabsStart = if (bottomUp) 1 + desktopItems + inactiveItems else 1
        // The first inactive tab, after its header.
        val inactiveStart = if (bottomUp) 1 + desktopItems + 1 else 1 + state.tabs.size + 1
        // A long bottom-up list opens at its end, by the address bar.
        LaunchedEffect(Unit) { if (bottomUp && state.tabs.isNotEmpty()) listState.scrollToItem(tabsStart + state.tabs.size - 1) }
        val scrollMoves = LocalSidebarMotion.current.moves
        ScrollToNewTab(
            state = state,
            scrollTo = { atEnd ->
                val index = if (atEnd) tabsStart + state.tabs.size - 1 else if (bottomUp) tabsStart else 0
                if (scrollMoves) listState.animateScrollToItem(index) else listState.scrollToItem(index)
            },
        )

        // Tab Drag: long-press then drag reorders; long-press and release opens the Tab Context Menu.
        val currentOnIntent by rememberUpdatedState(onIntent)
        var menuTabId by remember { mutableStateOf<String?>(null) }
        var pendingMenuTabId by remember { mutableStateOf<String?>(null) }
        val reorderState =
            createListReorderState(
                listState = listState,
                onMove = { from, to ->
                    val fromId = (from.key as? String)?.takeIf(::isActiveTabKey)
                    val toId = (to.key as? String)?.takeIf(::isActiveTabKey)
                    if (fromId != null && toId != null) {
                        currentOnIntent(SidebarIntent.MoveTab(fromId, toId, movedDown = to.index > from.index))
                    }
                },
                ignoredItems = listOf(LIST_HEADER_KEY, INACTIVE_HEADER_KEY),
                onLongPress = { item ->
                    pendingMenuTabId =
                        (item.key as? String)
                            ?.takeUnless { it == LIST_HEADER_KEY || it == INACTIVE_HEADER_KEY }
                            ?.removePrefix(INACTIVE_KEY_PREFIX)
                },
                onExitLongPress = { pendingMenuTabId = null },
            )
        LaunchedEffect(reorderState.draggingItemKey) {
            if (reorderState.draggingItemKey == null && pendingMenuTabId != null) {
                menuTabId = pendingMenuTabId
                pendingMenuTabId = null
            }
        }

        CompositionLocalProvider(LocalTabMenuExtras provides tabMenuExtras) {
        LazyColumn(
            state = listState,
            // While a tab is dragged, the list's own scrolling must not claim the gesture
            // (edge auto-scroll still comes from the reorder state).
            userScrollEnabled = reorderState.draggingItemKey == null,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .ignoringTouches(state.arranging, SidebarTestTag.TAB_LIST)
                    .sidebarListPressAndDrag(
                        listState = listState,
                        reorderState = reorderState,
                        onDragMove = { position ->
                            draggingTabKey = reorderState.draggingItemKey as? String
                            val key = draggingTabKey
                            if (drag.item == null && key != null) {
                                val id = key.removePrefix(INACTIVE_KEY_PREFIX)
                                state.allTabs.firstOrNull { it.id == id }?.let { tab ->
                                    // Where the finger holds the row, from the row's centre.
                                    val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                    val rowCentre = row?.let { Offset(drag.tabList.width / 2f, it.offset + it.size / 2f) }
                                    val grab = rowCentre?.let { position - it } ?: Offset.Zero
                                    drag.start(DragItem(DragSource.Tab, tab.title, tab.url, tab.icon, grab), listOrigin + position)
                                }
                            }
                            drag.move(listOrigin + position)
                            spaceDropTarget = draggingTabKey?.let { spaceAt(position) }
                        },
                        onDragFinished = { key, position ->
                            val y = position.y
                            val zone = drag.end()
                            val spaceTarget = spaceDropTarget
                            spaceDropTarget = null
                            draggingTabKey = null
                            val tabId = key?.takeUnless { it == LIST_HEADER_KEY || it == INACTIVE_HEADER_KEY }?.removePrefix(INACTIVE_KEY_PREFIX)
                            val inactiveHeaderY =
                                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == INACTIVE_HEADER_KEY }?.offset?.toFloat()
                            // Bottom up, the section is above the tabs: past its last row is out of it.
                            val inactiveEndY =
                                listState.layoutInfo.visibleItemsInfo
                                    .filter { (it.key as? String)?.let { k -> k == INACTIVE_HEADER_KEY || k.startsWith(INACTIVE_KEY_PREFIX) } == true }
                                    .maxOfOrNull { (it.offset + it.size).toFloat() }
                            when {
                                tabId == null -> Unit
                                // Dropped on Pin: pin it to this Space.
                                zone == DragZone.Pin -> {
                                    haptics.performHapticFeedback(
                                        if (state.spacePinBlock == null) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                                    )
                                    onIntent(SidebarIntent.PinToSpace(tabId))
                                }
                                // Dropped on another Space's icon: the tab moves there.
                                spaceTarget != null -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onIntent(SidebarIntent.MoveTabToSpace(tabId, spaceTarget))
                                }
                                // Dropped on Essentials: pin (refusals explain themselves).
                                zone == DragZone.Essentials -> {
                                    haptics.performHapticFeedback(
                                        if (state.pinBlock == null) HapticFeedbackType.Confirm else HapticFeedbackType.Reject,
                                    )
                                    onIntent(SidebarIntent.PinTab(tabId))
                                }
                                // An inactive tab dropped among the active tabs (above the Inactive header; below the section, bottom up): reactivate it.
                                key.startsWith(INACTIVE_KEY_PREFIX) && !bottomUp && inactiveHeaderY != null && y < inactiveHeaderY ->
                                    onIntent(SidebarIntent.ReactivateTab(tabId))
                                key.startsWith(INACTIVE_KEY_PREFIX) && bottomUp && inactiveEndY != null && y > inactiveEndY ->
                                    onIntent(SidebarIntent.ReactivateTab(tabId))
                                else -> Unit
                            }
                        },
                    )
                    .onGloballyPositioned {
                        listOrigin = it.boundsInRoot().topLeft
                        drag.tabList = it.boundsInRoot()
                    }
                    .graphicsLayer { spaceEnter.apply(this) }
                    .testTag(SidebarTestTag.TAB_LIST),
            // Bottom up: a short list rests on the bottom edge.
            verticalArrangement = Arrangement.spacedBy(2.dp, if (bottomUp) Alignment.Bottom else Alignment.Top),
        ) {
            item(key = LIST_HEADER_KEY) { Spacer(Modifier.height(8.dp)) }
            // Top down: the tabs, then the Inactive and Desktop Tabs sections. Bottom up mirrors it, so the
            // newest tabs sit by the address bar and the sections stay at the far end.
            if (bottomUp) {
            // Desktop Tabs: read-only, only where rows have labels.
            state.desktopTabs?.takeIf { showLabels }?.let { group ->
                item(key = DESKTOP_HEADER_KEY) {
                    DesktopTabsHeader(group, onToggle = { onIntent(SidebarIntent.ToggleDesktopTabs) }, modifier = sidebarItem())
                }
                if (group.expanded) {
                    itemsIndexed(group.tabs, key = { index, tab -> "$DESKTOP_KEY_PREFIX$index|${tab.url}" }) { _, tab ->
                        DesktopTabRow(tab, group.icons[tab.url], onClick = { onIntent(SidebarIntent.OpenDesktopTab(tab.url)) }, modifier = sidebarItem())
                    }
                }
            }
            state.inactive?.let { section ->
                item(key = INACTIVE_HEADER_KEY) {
                    InactiveSectionHeader(
                        section = section,
                        showLabels = showLabels,
                        onToggle = { onIntent(SidebarIntent.ToggleInactiveSection) },
                        onReveal = { onIntent(SidebarIntent.RevealInactiveSection) },
                        onCloseAll = { onIntent(SidebarIntent.CloseAllInactive) },
                        modifier = Modifier.then(sidebarItem()),
                    )
                }
                if (section.expanded && showLabels) {
                    itemsIndexed(section.tabs, key = { _, tab -> INACTIVE_KEY_PREFIX + tab.id }) { inactiveIndex, tab ->
                        // List index: where the section's first tab sits, plus its place in the section.
                        SidebarReorderableItem(
                            state = reorderState,
                            key = INACTIVE_KEY_PREFIX + tab.id,
                            position = inactiveStart + inactiveIndex,
                        ) {
                        SwipeToCloseTabItem(
                            tab = tab,
                            showLabels = true,
                            labelAlpha = titleAlpha,
                            menuOpen = menuTabId == tab.id,
                            onMenuRequest = { menuTabId = tab.id },
                            onMenuDismiss = { menuTabId = null },
                            // Selecting an inactive tab makes it active again (Fenix tracks last access).
                            onClick = { if (pendingMenuTabId == null) onIntent(SidebarIntent.SelectTab(tab.id)) },
                            onClose = { onIntent(SidebarIntent.CloseTab(tab.id)) },
                            onDuplicate = { onIntent(SidebarIntent.DuplicateTab(tab.id)) },
                            pinBlock = state.pinBlock,
                            onPin = { onIntent(SidebarIntent.PinTab(tab.id)) },
                            onMoveUp = null,
                            onMoveDown = null,
                            onToggleMute = { onIntent(SidebarIntent.ToggleMute(tab.id)) },
                            isDragging = reorderState.draggingItemKey == INACTIVE_KEY_PREFIX + tab.id,
                        )
                        }
                    }
                }
            }

            itemsIndexed(state.tabs, key = { _, tab -> tab.id }) { index, tab ->
                SidebarReorderableItem(state = reorderState, key = tab.id, position = tabsStart + index) {
                    val above = state.tabs.getOrNull(index - 1)
                    val below = state.tabs.getOrNull(index + 1)
                    SwipeToCloseTabItem(
                        tab = tab,
                        showLabels = showLabels,
                        labelAlpha = titleAlpha,
                        isDragging = reorderState.draggingItemKey == tab.id,
                        menuOpen = menuTabId == tab.id,
                        onMenuRequest = { menuTabId = tab.id },
                        onMenuDismiss = { menuTabId = null },
                        onClick = { if (pendingMenuTabId == null) onIntent(SidebarIntent.SelectTab(tab.id)) },
                        onClose = { onIntent(SidebarIntent.CloseTab(tab.id)) },
                        onDuplicate = { onIntent(SidebarIntent.DuplicateTab(tab.id)) },
                        pinBlock = state.pinBlock,
                        onPin = { onIntent(SidebarIntent.PinTab(tab.id)) },
                        onMoveUp = above?.let { { onIntent(SidebarIntent.MoveTab(tab.id, it.id, movedDown = false)) } },
                        onMoveDown = below?.let { { onIntent(SidebarIntent.MoveTab(tab.id, it.id, movedDown = true)) } },
                        onToggleMute = { onIntent(SidebarIntent.ToggleMute(tab.id)) },
                    )
                }
            }
            } else {
            itemsIndexed(state.tabs, key = { _, tab -> tab.id }) { index, tab ->
                SidebarReorderableItem(state = reorderState, key = tab.id, position = tabsStart + index) {
                    val above = state.tabs.getOrNull(index - 1)
                    val below = state.tabs.getOrNull(index + 1)
                    SwipeToCloseTabItem(
                        tab = tab,
                        showLabels = showLabels,
                        labelAlpha = titleAlpha,
                        isDragging = reorderState.draggingItemKey == tab.id,
                        menuOpen = menuTabId == tab.id,
                        onMenuRequest = { menuTabId = tab.id },
                        onMenuDismiss = { menuTabId = null },
                        onClick = { if (pendingMenuTabId == null) onIntent(SidebarIntent.SelectTab(tab.id)) },
                        onClose = { onIntent(SidebarIntent.CloseTab(tab.id)) },
                        onDuplicate = { onIntent(SidebarIntent.DuplicateTab(tab.id)) },
                        pinBlock = state.pinBlock,
                        onPin = { onIntent(SidebarIntent.PinTab(tab.id)) },
                        onMoveUp = above?.let { { onIntent(SidebarIntent.MoveTab(tab.id, it.id, movedDown = false)) } },
                        onMoveDown = below?.let { { onIntent(SidebarIntent.MoveTab(tab.id, it.id, movedDown = true)) } },
                        onToggleMute = { onIntent(SidebarIntent.ToggleMute(tab.id)) },
                    )
                }
            }

            state.inactive?.let { section ->
                item(key = INACTIVE_HEADER_KEY) {
                    InactiveSectionHeader(
                        section = section,
                        showLabels = showLabels,
                        onToggle = { onIntent(SidebarIntent.ToggleInactiveSection) },
                        onReveal = { onIntent(SidebarIntent.RevealInactiveSection) },
                        onCloseAll = { onIntent(SidebarIntent.CloseAllInactive) },
                        modifier = Modifier.then(sidebarItem()),
                    )
                }
                if (section.expanded && showLabels) {
                    itemsIndexed(section.tabs, key = { _, tab -> INACTIVE_KEY_PREFIX + tab.id }) { inactiveIndex, tab ->
                        // List index: where the section's first tab sits, plus its place in the section.
                        SidebarReorderableItem(
                            state = reorderState,
                            key = INACTIVE_KEY_PREFIX + tab.id,
                            position = inactiveStart + inactiveIndex,
                        ) {
                        SwipeToCloseTabItem(
                            tab = tab,
                            showLabels = true,
                            labelAlpha = titleAlpha,
                            menuOpen = menuTabId == tab.id,
                            onMenuRequest = { menuTabId = tab.id },
                            onMenuDismiss = { menuTabId = null },
                            // Selecting an inactive tab makes it active again (Fenix tracks last access).
                            onClick = { if (pendingMenuTabId == null) onIntent(SidebarIntent.SelectTab(tab.id)) },
                            onClose = { onIntent(SidebarIntent.CloseTab(tab.id)) },
                            onDuplicate = { onIntent(SidebarIntent.DuplicateTab(tab.id)) },
                            pinBlock = state.pinBlock,
                            onPin = { onIntent(SidebarIntent.PinTab(tab.id)) },
                            onMoveUp = null,
                            onMoveDown = null,
                            onToggleMute = { onIntent(SidebarIntent.ToggleMute(tab.id)) },
                            isDragging = reorderState.draggingItemKey == INACTIVE_KEY_PREFIX + tab.id,
                        )
                        }
                    }
                }
            }

            // Desktop Tabs: read-only, only where rows have labels.
            state.desktopTabs?.takeIf { showLabels }?.let { group ->
                item(key = DESKTOP_HEADER_KEY) {
                    DesktopTabsHeader(group, onToggle = { onIntent(SidebarIntent.ToggleDesktopTabs) }, modifier = sidebarItem())
                }
                if (group.expanded) {
                    itemsIndexed(group.tabs, key = { index, tab -> "$DESKTOP_KEY_PREFIX$index|${tab.url}" }) { _, tab ->
                        DesktopTabRow(tab, group.icons[tab.url], onClick = { onIntent(SidebarIntent.OpenDesktopTab(tab.url)) }, modifier = sidebarItem())
                    }
                }
            }
            }
        }

        }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        val labelsChanged = rememberNewKeys(listOf(showLabels.toString())).isNotEmpty()
        androidx.compose.foundation.layout.Column(
            Modifier.animateHeight(LocalSidebarMotion.current.move(SideravenMotion.MODE_CHANGE_MS, SideravenMotion.EaseInOutStrong)),
        ) {
        key(showLabels) {
        FadeInOnEnter(animate = labelsChanged) {
        androidx.compose.foundation.layout.Column {
        // The Space icon row (Persistent Mode; Collapsed Mode gets a Space menu).
        // Collapsed Mode: the current Space's icon, opening a menu of Spaces.
        if (!showLabels && state.spaces.isNotEmpty()) {
            if (state.syncProblem) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SyncProblemDot() }
            CollapsedSpaceButton(spaces = state.spaces, onIntent = onIntent)
        }
        // The Header option showed the Space above its Pinned Tabs; the icon row keeps this slot.
        if (showLabels && state.spaces.isNotEmpty() && !spaceHeader) {
            SpaceRow(
                spaces = state.spaces,
                onIntent = onIntent,
                syncProblem = state.syncProblem,
                zenSyncOn = state.zenSyncOn,
                dropTarget = spaceDropTarget,
                onIconBounds = { id, bounds -> spaceIconBounds[id] = bounds },
            )
        }

        if (state.arranging) {
            ArrangeControls(arrangeIntent)
        } else {
        SidebarControls(
            controls = state.controls,
            showLabels = showLabels,
            nextMode = state.nextMode,
            isPrivate = state.isPrivate,
            chosenLayout = state.chosenLayout,
            onIntent = onIntent,
        )
        // The Sidebar Address Bar at the bottom edge, where the thumb lands first.
        if (showLabels) {
            state.address?.let { address ->
                // Sideways swipes move through the Tab List; not from an Essential or Pinned Tab, which isn't in it.
                val selected = state.tabs.indexOfFirst { it.isSelected }
                AddressBar(
                    address = address,
                    onIntent = onIntent,
                    previousTab = state.tabs.getOrNull(selected - 1)?.id?.takeIf { selected >= 0 },
                    nextTab = state.tabs.getOrNull(selected + 1)?.id?.takeIf { selected >= 0 },
                )
            }
        }
        }
        }
        }
        }
        }
    }
    SidebarDragLayer(
        drag = drag,
        showLabels = showLabels,
        currentSpace = state.currentSpace,
        essentialsBlocked = state.pinBlock != null,
        pinBlocked = state.spacePinBlock != null,
    )
    }
    }
}
}

/**
 * Tab Close Gesture: swipe toward the nearest screen edge, starting on the
 * item; see [SwipeToClose].
 */
@Composable
private fun SwipeToCloseTabItem(
    tab: SidebarTab,
    showLabels: Boolean,
    labelAlpha: Float,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    pinBlock: PinResult?,
    onPin: () -> Unit,
    isDragging: Boolean,
    menuOpen: Boolean,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row: @Composable (swiping: Boolean) -> Unit = { swiping ->
        SidebarTabItem(
            tab = tab,
            showLabels = showLabels,
            labelAlpha = labelAlpha,
            onClick = onClick,
            onClose = onClose,
            onDuplicate = onDuplicate,
            pinBlock = pinBlock,
            onPin = onPin,
            isDragging = isDragging,
            menuOpen = menuOpen,
            onMenuRequest = onMenuRequest,
            onMenuDismiss = onMenuDismiss,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onToggleMute = onToggleMute,
            isSwiping = swiping,
        )
    }
    // Swipe to close off (Sideraven Settings): the row closes by button, menu and keyboard only.
    if (!LocalSidebarLook.current.swipeToClose) {
        Box(modifier) { row(false) }
        return
    }
    SwipeToClose(onClose = onClose, modifier = modifier) { swiping -> row(swiping) }
}

/**
 * A 48dp tab row with a rounded pill inset inside it. The selected tab carries the Active Tab
 * Indicator (accent bar + tonal fill), which appears instantly; the old fill fades out. Any other
 * row being swiped ([isSwiping]) takes the tonal fill without the bar, so it's clear what the
 * finger is on. Collapsed Mode shows the favicon only.
 */
@Composable
private fun SidebarTabItem(
    tab: SidebarTab,
    showLabels: Boolean,
    labelAlpha: Float,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    pinBlock: PinResult?,
    onPin: () -> Unit,
    isDragging: Boolean,
    menuOpen: Boolean,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onToggleMute: () -> Unit,
    isSwiping: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val muteLabel = stringResource(if (tab.audio == TabAudio.Muted) R.string.sideraven_unmute_tab else R.string.sideraven_mute_tab)
    val audioState =
        when (tab.audio) {
            TabAudio.Playing -> stringResource(R.string.sideraven_playing_audio)
            TabAudio.Muted -> stringResource(R.string.sideraven_muted)
            TabAudio.None -> null
        }
    val interactionSource = remember { MutableInteractionSource() }
    val duplicateLabel = stringResource(R.string.sideraven_duplicate_tab)
    val asleepLabel = stringResource(R.string.sideraven_asleep)
    val moveUpLabel = stringResource(R.string.sideraven_move_up)
    val moveDownLabel = stringResource(R.string.sideraven_move_down)
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val closeLabel = stringResource(R.string.sideraven_close_tab)
    val fill by
        animateColorAsState(
            targetValue = if (tab.isSelected || isSwiping) sidebarFill(SidebarFill.High) else Color.Transparent,
            animationSpec =
                if (tab.isSelected) {
                    snap()
                } else {
                    LocalSidebarMotion.current.fade(SideravenMotion.SELECTION_FADE_MS)
                },
            label = "tabFill",
        )

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(LocalSidebarLook.current.tabRows.rowDp.dp)
                // Picked up by a Tab Drag: lift slightly above its neighbours.
                .graphicsLayer {
                    val lift = if (isDragging) DRAG_LIFT_SCALE else 1f
                    scaleX = lift
                    scaleY = lift
                    alpha = if (isDragging) 0f else 1f
                    shadowElevation = if (isDragging) DRAG_LIFT_ELEVATION.toPx() else 0f
                    shape = RoundedCornerShape(10.dp)
                    clip = false
                }
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (isDragging) colors.surfaceContainerHigh else fill)
                .hoverable(interactionSource)
                // Long-press is handled by the list (menu on release, Tab Drag on move); the list ignores
                // the tap that ends a long press. Nothing here may consume the gesture, or drags break.
                // Right-click opens the menu.
                .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Tab, onClick = onClick)
                .onSecondaryClick(onMenuRequest)
                .semantics {
                    selected = tab.isSelected
                    contentDescription = tab.title
                    listOfNotNull(audioState, asleepLabel.takeIf { tab.sleeping }).takeIf { it.isNotEmpty() }?.let { stateDescription = it.joinToString() }
                    customActions =
                        buildList {
                            add(CustomAccessibilityAction(closeLabel) { onClose(); true })
                            if (tab.audio != TabAudio.None) add(CustomAccessibilityAction(muteLabel) { onToggleMute(); true })
                            add(CustomAccessibilityAction(duplicateLabel) { onDuplicate(); true })
                            onMoveUp?.let { add(CustomAccessibilityAction(moveUpLabel) { it(); true }) }
                            onMoveDown?.let { add(CustomAccessibilityAction(moveDownLabel) { it(); true }) }
                        }
                }
                .testTag(SidebarTestTag.TAB_ITEM),
    ) {
        TabContextMenu(
            tabId = tab.id,
            expanded = menuOpen,
            onDismiss = onMenuDismiss,
            onClose = {
                onMenuDismiss()
                onClose()
            },
            onDuplicate = {
                onMenuDismiss()
                onDuplicate()
            },
            pinBlock = pinBlock,
            onPin = {
                onMenuDismiss()
                onPin()
            },
        )

        if (tab.isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .fillMaxHeight(0.55f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primary),
            )
        }

        Row(
            // A Sleeping tab is lightly dimmed.
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp).alpha(fadedAlpha(if (tab.sleeping) SLEEP_ALPHA else 1f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.greyWhenSleeping(tab.sleeping)) {
                TabFavicon(tab)
                // Collapsed Mode: a small audio dot on the favicon.
                if (!showLabels && tab.audio != TabAudio.None) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (tab.audio == TabAudio.Muted) colors.onSurfaceVariant else colors.primary)
                                .testTag(SidebarTestTag.AUDIO_DOT),
                    )
                }
            }
            if (showLabels) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tab.isSelected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = labelAlpha },
                )
                // Audio indicator: tap to mute/unmute.
                if (tab.audio != TabAudio.None) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(40.dp).testTag(SidebarTestTag.AUDIO_BUTTON),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (tab.audio == TabAudio.Muted) iconsR.drawable.mozac_ic_audio_muted_24 else iconsR.drawable.mozac_ic_audio_24,
                                ),
                            contentDescription = muteLabel,
                            modifier = Modifier.size(16.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
                // Tab Close Button: on the selected tab and a pressed or hovered tab, or on every tab.
                androidx.compose.animation.AnimatedVisibility(
                    visible = tab.isSelected || isPressed || isHovered || LocalSidebarLook.current.closeButtons == CloseButtons.EveryTab,
                    enter = androidx.compose.animation.fadeIn(LocalSidebarMotion.current.fade(SideravenMotion.SELECTION_FADE_MS)),
                    exit = androidx.compose.animation.ExitTransition.None,
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(40.dp).testTag(SidebarTestTag.CLOSE_BUTTON),
                    ) {
                        Icon(
                            painter = painterResource(iconsR.drawable.mozac_ic_cross_20),
                            contentDescription = closeLabel,
                            modifier = Modifier.size(16.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The Tab Context Menu: every tab gesture's non-gesture alternative. Pin arrives with Essentials. */
@Composable
private fun TabContextMenu(
    tabId: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    pinBlock: PinResult?,
    onPin: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SidebarTestTag.CONTEXT_MENU),
    ) {
        // Private browsing has no Essentials; at the Essentials Cap it's unavailable, with the reason shown.
        if (pinBlock != PinResult.PrivateRefused) DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(R.string.sideraven_pin_essential))
                    when (pinBlock) {
                        PinResult.Full -> R.string.sideraven_essentials_full
                        PinResult.PrivateRefused -> R.string.sideraven_essentials_private
                        else -> null
                    }?.let { reason ->
                        Text(
                            text = stringResource(reason, ESSENTIALS_CAP),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            enabled = pinBlock == null,
            onClick = onPin,
            modifier = Modifier.testTag(SidebarTestTag.PIN_ITEM),
        )
        // Entries that need more than the tab (e.g. the Spaces), provided by the panel.
        LocalTabMenuExtras.current(tabId, onDismiss)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sideraven_duplicate_tab)) },
            onClick = onDuplicate,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sideraven_close_tab)) },
            onClick = onClose,
        )
    }
}

/** Horizontal travel of a Space switch's entrance. */
private val SpaceSwitchOffset = 16.dp

/** The entrance of a Space's header and tabs after a switch: fade in from the side of travel. */
private class SpaceSwitchMotion(
    val progress: Animatable<Float, *>,
    val direction: Float,
    val offsetPx: Float,
) {
    fun apply(scope: androidx.compose.ui.graphics.GraphicsLayerScope) {
        val p = progress.value
        scope.alpha = p
        scope.translationX = (1f - p) * offsetPx * direction
    }
}

/**
 * Space switches move the content in from the direction of travel (the next Space's tabs enter
 * from the right). Keyboard switches snap, like every keyboard action.
 */
@Composable
private fun rememberSpaceSwitchMotion(state: SidebarUiState): SpaceSwitchMotion {
    val motion = LocalSidebarMotion.current
    // Reduced motion: the new Space's content fades in without moving.
    val offsetPx = if (motion.moves) with(LocalDensity.current) { SpaceSwitchOffset.toPx() } else 0f
    val spaceId = state.currentSpace?.id
    val currentIndex = state.spaces.indexOfFirst { it.isCurrent }
    val last = remember { intArrayOf(currentIndex) }
    // Made in the same composition that shows the new Space's tabs, so no frame shows them unfaded.
    val start =
        remember(spaceId) {
            val previous = last[0]
            val animate = previous != -1 && currentIndex != -1 && previous != currentIndex && state.animateModeChanges
            Animatable(if (animate) 0f else 1f) to (if (currentIndex > previous) 1f else -1f)
        }
    SideEffect { last[0] = currentIndex }
    LaunchedEffect(start) { if (start.first.value < 1f) start.first.animateTo(1f, motion.fade(SideravenMotion.MODE_CHANGE_MS)) }
    return SpaceSwitchMotion(start.first, start.second, offsetPx)
}

/** Whether Zen Sync is on, for the delete confirmations' "other devices" sentence. */
internal val LocalZenSyncOn = staticCompositionLocalOf { false }

/** Extra Tab Context Menu entries for a tab id, given a way to close the menu. */
internal val LocalTabMenuExtras = staticCompositionLocalOf<@Composable (String, () -> Unit) -> Unit> { { _, _ -> } }

/** "Pin to space", unavailable at the cap or for private tabs, with the reason shown. */
@Composable
private fun PinToSpaceMenuItem(
    block: PinResult?,
    onPin: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(stringResource(R.string.sideraven_pin_space))
                when (block) {
                    PinResult.PrivateRefused -> stringResource(R.string.sideraven_space_pin_private)
                    else -> null
                }?.let { reason -> Text(text = reason, style = MaterialTheme.typography.bodySmall) }
            }
        },
        enabled = block == null,
        onClick = onPin,
        modifier = Modifier.testTag(SidebarTestTag.PIN_SPACE_ITEM),
    )
}

/** "Move to space": expands in place into one entry per other Space. */
@Composable
private fun MoveToSpaceMenuItems(
    targets: List<SidebarSpace>,
    onMove: (spaceId: String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.sideraven_move_to_space)) },
        trailingIcon = {
            Icon(
                painter = painterResource(if (open) iconsR.drawable.mozac_ic_chevron_up_24 else iconsR.drawable.mozac_ic_chevron_down_24),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        onClick = { open = !open },
        modifier = Modifier.testTag(SidebarTestTag.MOVE_TO_SPACE),
    )
    if (open) {
        targets.forEach { space ->
            DropdownMenuItem(
                text = { Text(space.name) },
                leadingIcon = { SpaceIcon(space.icon, size = 18.dp) },
                onClick = { onMove(space.id) },
                contentPadding = PaddingValues(start = 28.dp, end = 12.dp),
            )
        }
    }
}

/** Opens [onClick] on a mouse right-click (secondary button), leaving touch input alone. */
private fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    onClick()
                }
            }
        }
    }

/**
 * Loads a favicon for a URL. The app provides Fenix's own `Favicon` (BrowserIcons, private-aware);
 * the default draws a globe, so Sidebar UI works without Fenix's components (e.g. in tests).
 */
val LocalFaviconLoader =
    staticCompositionLocalOf<@Composable (url: String, size: Dp) -> Unit> {
        { _, size -> GlobeIcon(size) }
    }

@Composable
internal fun GlobeIcon(size: Dp) {
    Icon(
        painter = painterResource(iconsR.drawable.mozac_ic_globe_24),
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TabFavicon(tab: SidebarTab) {
    val icon = tab.icon
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
        )
    } else {
        LocalFaviconLoader.current(tab.url, 20.dp)
    }
}

/** One of the Sidebar Controls. */
private class SidebarControl(
    val iconRes: Int,
    val label: String,
    val testTag: String,
    val onClick: () -> Unit,
)

/** Arrange Mode's grab bars: TalkBack's "Show more" and "Show less" move one by this share of the strip. */
private const val ARRANGE_STEP = 0.1f

/**
 * While [ignore] (Arrange Mode), taps and drags inside don't reach the content, and TalkBack doesn't
 * offer its actions: only the grab bars and Arrange Mode's buttons act.
 */
private fun Modifier.ignoringTouches(
    ignore: Boolean,
    // The node's own test tag survives (clearing the semantics would drop it).
    tag: String? = null,
): Modifier =
    if (!ignore) {
        this
    } else {
        clearAndSetSemantics { tag?.let { this[SemanticsProperties.TestTag] = it } }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

/**
 * Arrange Mode's grab bar in place of a Divider: as wide as the strip, about 32dp tall. Dragging it
 * sets the height of the section above ([height] when picked up, never past [room]), with a tick on
 * pickup; TalkBack gets Show more and Show less.
 */
@Composable
private fun GrabBar(
    label: String,
    height: () -> Float,
    room: () -> Float,
    onDragging: (Float) -> Unit,
    onLetGo: () -> Unit,
    onStep: (Float) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val more = stringResource(R.string.sideraven_section_show_more)
    val less = stringResource(R.string.sideraven_section_show_less)
    val currentHeight by rememberUpdatedState(height)
    val currentRoom by rememberUpdatedState(room)
    val currentOnDragging by rememberUpdatedState(onDragging)
    val currentOnLetGo by rememberUpdatedState(onLetGo)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    var start = 0f
                    var moved = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            start = currentHeight()
                            moved = 0f
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            moved += dy
                            currentOnDragging((start + moved).coerceIn(0f, currentRoom().coerceAtLeast(0f)))
                        },
                        onDragEnd = { currentOnLetGo() },
                        onDragCancel = { currentOnLetGo() },
                    )
                }
                .semantics {
                    contentDescription = label
                    customActions =
                        listOf(
                            CustomAccessibilityAction(more) {
                                onStep(ARRANGE_STEP)
                                true
                            },
                            CustomAccessibilityAction(less) {
                                onStep(-ARRANGE_STEP)
                                true
                            },
                        )
                }
                .testTag(SidebarTestTag.GRAB_BAR),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(4.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Arrange Mode's buttons, in place of the Sidebar Controls: Reset to the automatic sizes, and Done. */
@Composable
private fun ArrangeControls(onIntent: (SidebarIntent) -> Unit) {
    ControlRow(
        iconRes = iconsR.drawable.mozac_ic_arrow_clockwise_24,
        label = stringResource(R.string.sideraven_arrange_reset),
        showLabel = false,
        labelAlpha = 0f,
        testTag = SidebarTestTag.ARRANGE_RESET,
    ) { onIntent(SidebarIntent.ResetArrangement) }
    ControlRow(
        iconRes = iconsR.drawable.mozac_ic_checkmark_24,
        label = stringResource(R.string.sideraven_arrange_done),
        showLabel = false,
        labelAlpha = 0f,
        testTag = SidebarTestTag.ARRANGE_DONE,
    ) { onIntent(SidebarIntent.DoneArranging) }
    Spacer(Modifier.height(4.dp))
}

/**
 * The Divider colour: the Space's own colour when it has one (the light or dark variant that belongs
 * to this theme, which [MaterialTheme.colorScheme] already holds as its primary), else the neutral
 * outline. A Space on the default colour keeps the Sidebar plain.
 */
@Composable
internal fun spaceDividerTint(accent: String?): Color =
    if (accent != null && accent != DEFAULT_SPACE_ACCENT) {
        MaterialTheme.colorScheme.primary.copy(alpha = SPACE_DIVIDER_ALPHA)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

private const val SPACE_DIVIDER_ALPHA = 0.45f

/**
 * The Divider under Pinned Tabs, which folds them (Pinned Fold): a short line, inset from the
 * Sidebar's edges like Zen's adjustable Divider, with a chevron at its end. Folded, a stack mark
 * and the count say what it holds. Its working area is tall enough to hit without aiming, without
 * stealing taps from the Pinned Tab above or the tables below.
 */
@Composable
private fun PinnedFoldDivider(
    folded: Boolean,
    count: Int,
    showLabels: Boolean,
    tint: Color,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(if (folded) R.string.sideraven_unfold_pinned else R.string.sideraven_fold_pinned)
    val chevronRotation by animateFloatAsState(if (folded) 0f else 180f, label = "pinnedFoldChevron")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PINNED_FOLD_HEIGHT)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { contentDescription = label }
                .testTag(SidebarTestTag.PINNED_FOLD)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = tint)
        if (folded) {
            Box(
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .testTag(SidebarTestTag.PINNED_STACK),
                contentAlignment = Alignment.Center,
            ) {
                PinnedStackMark(tint)
            }
        }
        if (folded && showLabels) {
            Text(
                text = pluralStringResource(R.plurals.sideraven_pinned_count, count, count),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_chevron_down_24),
            contentDescription = null,
            modifier = Modifier.padding(start = if (folded) 0.dp else 4.dp).size(14.dp).graphicsLayer { rotationZ = chevronRotation },
            tint = colors.onSurfaceVariant,
        )
    }
}

/** Folded Pinned Tabs, drawn as a stack: two rules, the shorter under the longer. */
@Composable
private fun PinnedStackMark(tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(width = 12.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(tint))
        Box(Modifier.size(width = 8.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(tint))
    }
}

/** 32 dp of working area: 8 dp above and below the 16 dp line, and no overlap with its neighbours. */
private val PINNED_FOLD_HEIGHT = 32.dp

/** The Divider after Essentials: Zen's top Divider, running to the Sidebar's edges. */
@Composable
private fun SectionDivider(tint: Color) {
    HorizontalDivider(color = tint)
}

/**
 * What a section holds: its scroll's viewport plus what's past it; 0 when the section isn't shown, and
 * null until its scroll has been measured (before that, its range reads as Int.MAX_VALUE).
 */
private fun contentHeight(
    scroll: ScrollState,
    shown: Boolean,
): Float? =
    when {
        !shown -> 0f
        scroll.maxValue == Int.MAX_VALUE -> null
        else -> scroll.viewportSize.toFloat() + scroll.maxValue.toFloat()
    }

/** The test tag of each Sidebar Control's button. */
internal val SidebarControlKind.testTag: String
    get() =
        when (this) {
            SidebarControlKind.NewTab -> SidebarTestTag.NEW_TAB
            SidebarControlKind.BrowsingModeSwitch -> SidebarTestTag.MODE_SWITCH
            SidebarControlKind.SyncedTabs -> SidebarTestTag.SYNCED_TABS
            SidebarControlKind.Toggle -> SidebarTestTag.TOGGLE
            SidebarControlKind.LayoutButton -> SidebarTestTag.LAYOUT_BUTTON
        }

/**
 * Sidebar Controls, drawn from [controls] in order. One row of icon buttons sharing the full
 * Sidebar's width (long-press for the name); a column of icons when Collapsed.
 */
@Composable
private fun SidebarControls(
    controls: List<SidebarControlKind>,
    showLabels: Boolean,
    nextMode: SidebarMode?,
    isPrivate: Boolean,
    chosenLayout: AddressBarLayout,
    onIntent: (SidebarIntent) -> Unit,
) {
    // The Toggle and layout button picture the window with the Sidebar on its own side.
    val onRight = LocalSidebarSide.current.dir < 0

    fun sided(
        left: Int,
        right: Int,
    ) = if (onRight) right else left
    // The Collapsed column never shows the layout button: mid mode change it would flash there.
    val shown = if (showLabels) controls else controls - SidebarControlKind.LayoutButton
    val buttons =
        shown.mapNotNull { kind ->
            when (kind) {
                SidebarControlKind.NewTab ->
                    SidebarControl(iconsR.drawable.mozac_ic_plus_24, stringResource(R.string.sideraven_new_tab), kind.testTag) { onIntent(SidebarIntent.NewTab) }
                // Browsing Mode Switch: its label names the mode it switches to.
                SidebarControlKind.BrowsingModeSwitch ->
                    SidebarControl(
                        if (isPrivate) R.drawable.sideraven_ic_private_fill_24 else R.drawable.sideraven_ic_private_24,
                        stringResource(if (isPrivate) R.string.sideraven_switch_to_normal else R.string.sideraven_switch_to_private),
                        kind.testTag,
                    ) { onIntent(SidebarIntent.SwitchBrowsingMode) }
                SidebarControlKind.SyncedTabs ->
                    SidebarControl(R.drawable.sideraven_ic_synced_tabs_24, stringResource(R.string.sideraven_synced_tabs), kind.testTag) {
                        onIntent(SidebarIntent.OpenSyncedTabs)
                    }
                // The Toggle's icon pictures the mode it switches to: a wide Sidebar, a narrow one, or a hidden one.
                SidebarControlKind.Toggle -> {
                    val target = nextMode ?: return@mapNotNull null
                    SidebarControl(
                        when (target) {
                            SidebarMode.Persistent -> sided(R.drawable.sideraven_ic_sidebar_persistent_24, R.drawable.sideraven_ic_sidebar_persistent_right_24)
                            SidebarMode.Collapsed -> sided(R.drawable.sideraven_ic_sidebar_collapsed_24, R.drawable.sideraven_ic_sidebar_collapsed_right_24)
                            SidebarMode.AutoHide -> sided(R.drawable.sideraven_ic_sidebar_autohide_24, R.drawable.sideraven_ic_sidebar_autohide_right_24)
                        },
                        stringResource(
                            when (target) {
                                SidebarMode.Persistent -> R.string.sideraven_expand_sidebar
                                SidebarMode.Collapsed -> R.string.sideraven_collapse_sidebar
                                SidebarMode.AutoHide -> R.string.sideraven_autohide_sidebar
                            },
                        ),
                        kind.testTag,
                    ) { onIntent(SidebarIntent.ToggleMode) }
                }
                // The layout button: the address moves to Fenix's toolbar (top or bottom, as Fenix is set)
                // or the Sidebar; in the list only where it can act.
                SidebarControlKind.LayoutButton ->
                    SidebarControl(
                        // Pictures where the address goes.
                        if (chosenLayout == AddressBarLayout.Sidebar) {
                            sided(R.drawable.sideraven_ic_address_in_toolbar_24, R.drawable.sideraven_ic_address_in_toolbar_right_24)
                        } else {
                            sided(R.drawable.sideraven_ic_address_in_sidebar_24, R.drawable.sideraven_ic_address_in_sidebar_right_24)
                        },
                        stringResource(if (chosenLayout == AddressBarLayout.Sidebar) R.string.sideraven_layout_to_top else R.string.sideraven_layout_to_sidebar),
                        kind.testTag,
                    ) { onIntent(SidebarIntent.ToggleAddressBarLayout) }
            }
        }
    if (showLabels) {
        // Equal shares of the row: fewer buttons, wider touch targets.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            buttons.forEach { ControlButton(it, Modifier.weight(1f)) }
        }
    } else {
        buttons.forEach { ControlRow(it.iconRes, it.label, showLabel = false, labelAlpha = 0f, testTag = it.testTag, onClick = it.onClick) }
        Spacer(Modifier.height(4.dp))
    }
}

/** An icon-only control; long-press (or hover) shows its name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlButton(
    control: SidebarControl,
    modifier: Modifier = Modifier,
) {
    // The share of the row goes on a plain Box: TooltipBox doesn't pass a weight on to the Row.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(control.label) } },
            state = rememberTooltipState(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClick = control.onClick)
                        .semantics { contentDescription = control.label }
                        .testTag(control.testTag),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(control.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ControlRow(
    iconRes: Int,
    label: String,
    showLabel: Boolean,
    labelAlpha: Float,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(testTag)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showLabel) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            )
        }
    }
}

/**
 * When a tab that wasn't there before appears at either end of the Tab List, scroll to it so the
 * user sees where it went ([scrollTo] gets true for the end). Reorders and selection changes don't scroll.
 */
@Composable
private fun ScrollToNewTab(
    state: SidebarUiState,
    scrollTo: suspend (atEnd: Boolean) -> Unit,
) {
    val firstId = state.tabs.firstOrNull()?.id
    val lastId = state.tabs.lastOrNull()?.id
    val seen = remember { mutableSetOf<String>() }
    LaunchedEffect(firstId, lastId) {
        val known = seen.isNotEmpty()
        val newFirst = known && firstId != null && firstId !in seen
        val newLast = known && lastId != null && lastId !in seen
        seen.clear()
        state.tabs.mapTo(seen) { it.id }
        when {
            newFirst -> scrollTo(false)
            newLast -> scrollTo(true)
        }
    }
}
