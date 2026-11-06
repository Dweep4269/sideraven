/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import pt.dweep.sideraven.theme.SidebarFill
import pt.dweep.sideraven.theme.sidebarFill
import pt.dweep.sideraven.UnloadOthersFrom
import pt.dweep.sideraven.SidebarSpace
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import mozilla.components.ui.icons.R as iconsR
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pt.dweep.sideraven.theme.SideravenMotion
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import pt.dweep.sideraven.SidebarPinnedRow
import pt.dweep.sideraven.SidebarFolder
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarPinnedTab

/** Ghost pins are dimmed, like Ghost Essentials. */
private const val PIN_GHOST_ALPHA = 0.45f

/**
 * The current Space's Pinned Tabs, above its Tab List. Scrolls by
 * itself past [maxHeight] so pins never push the tabs off screen.
 */
@Composable
internal fun PinnedTabsBlock(
    pinned: List<SidebarPinnedTab>,
    showLabels: Boolean,
    maxHeight: Dp,
    onIntent: (SidebarIntent) -> Unit,
    rows: List<SidebarPinnedRow> = emptyList(),
    folders: List<SidebarFolder> = emptyList(),
    dropTarget: DropTarget = DropTarget.None,
    // The block's scroll, so Section Sizing can see how much it holds.
    scroll: ScrollState = rememberScrollState(),
    // While Arrange Mode's grab bar is dragged, the height follows the finger instead of animating to it.
    resizing: Boolean = false,
    // Pinned Fold: the block stays (empty) so it can animate shut.
    keepWhenEmpty: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    // Where a dragged Pinned Tab lands: Essentials, below the block (unpin), or another pin's place.
    val onPinDropped: (String, Offset, DragZone?) -> Unit = { pinId, dropAt, zone ->
        when (zone) {
            DragZone.Essentials -> onIntent(SidebarIntent.PinnedToEssentials(pinId))
            // Dropped on the Tab List: unpin.
            DragZone.Unpin -> onIntent(SidebarIntent.UnpinPinnedTab(pinId))
            else ->
                rowBounds.entries.firstOrNull { (id, bounds) -> id != pinId && bounds.contains(dropAt) }?.key?.let { target ->
                    if (target.startsWith(FOLDER_KEY_PREFIX)) {
                        onIntent(SidebarIntent.MoveIntoFolder(pinId, target.removePrefix(FOLDER_KEY_PREFIX)))
                    } else {
                        onIntent(SidebarIntent.MovePinnedTab(pinId, target))
                    }
                }
        }
    }
    val outline =
        when (dropTarget) {
            DropTarget.PinAccepted -> Modifier.border(2.dp, colors.primary, MaterialTheme.shapes.medium)
            DropTarget.PinRejected -> Modifier.border(1.dp, colors.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            DropTarget.None -> Modifier
        }
    if (pinned.isEmpty() && rows.isEmpty() && !keepWhenEmpty) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateHeight(if (resizing) snap() else LocalSidebarMotion.current.move(SideravenMotion.ITEM_ENTER_MS, SideravenMotion.EaseInOutStrong))
                .then(outline)
                .heightIn(max = maxHeight)
                .verticalScroll(scroll)
                .padding(vertical = if (pinned.isEmpty() && rows.isEmpty()) 0.dp else 4.dp)
                .testTag(SidebarTestTag.PINNED_TABS),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (rows.isEmpty()) {
            pinned.forEach { pin ->
                key(pin.id) {
                    PinnedTabRow(pin, showLabels, onIntent, folders = folders, onDropped = onPinDropped, onBounds = { rowBounds[pin.id] = it }, neighbours = pinned.neighboursOf(pin))
                }
            }
        } else {
            // Collapsed Mode shows the top level only: a Folder opens a popup of its pins.
            rows.filter { showLabels || it.depth == 0 }.forEach { row ->
                key(if (row is SidebarPinnedRow.FolderRow) FOLDER_KEY_PREFIX + row.folder.id else (row as SidebarPinnedRow.PinRow).pin.id) {
                when (row) {
                    is SidebarPinnedRow.FolderRow ->
                        Box(Modifier.onGloballyPositioned { rowBounds[FOLDER_KEY_PREFIX + row.folder.id] = it.boundsInRoot() }) {
                            FolderRow(
                                row.folder,
                                showLabels,
                                onIntent,
                                isFirst = row.folder.id == folders.firstOrNull { it.depth == row.depth }?.id,
                                contents = folderContents(row.folder.id, folders, pinned),
                                onDropped = { dropAt ->
                                    // A Folder dropped on a sibling Folder takes its place.
                                    rowBounds.entries
                                        .firstOrNull { (key, bounds) ->
                                            key.startsWith(FOLDER_KEY_PREFIX) && key != FOLDER_KEY_PREFIX + row.folder.id && bounds.contains(dropAt)
                                        }?.key
                                        ?.removePrefix(FOLDER_KEY_PREFIX)
                                        ?.let { onIntent(SidebarIntent.MoveFolderTo(row.folder.id, it)) }
                                },
                            )
                        }
                    is SidebarPinnedRow.PinRow ->
                        PinnedTabRow(
                            row.pin,
                            showLabels,
                            onIntent,
                            depth = row.depth,
                            folders = folders,
                            onDropped = onPinDropped,
                            onBounds = { rowBounds[row.pin.id] = it },
                            neighbours = pinned.neighboursOf(row.pin),
                        )
                }
                }
            }
        }
    }
}

/** The Pinned Tabs just above and below [pin] in its folder (or outside any), for moving it a step. */
private fun List<SidebarPinnedTab>.neighboursOf(pin: SidebarPinnedTab): Pair<String?, String?> {
    val siblings = filter { it.folderId == pin.folderId }
    val at = siblings.indexOfFirst { it.id == pin.id }
    return siblings.getOrNull(at - 1)?.id to siblings.getOrNull(at + 1)?.id
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedTabRow(
    pin: SidebarPinnedTab,
    showLabels: Boolean,
    onIntent: (SidebarIntent) -> Unit,
    depth: Int = 0,
    folders: List<SidebarFolder> = emptyList(),
    onDropped: (pinId: String, dropAtInRoot: Offset, zone: DragZone?) -> Unit = { _, _, _ -> },
    onBounds: (Rect) -> Unit = {},
    // TalkBack's way to reorder (dragging is the touch way): the neighbour above and below in its folder.
    neighbours: Pair<String?, String?> = null to null,
) {
    val drag = LocalSidebarDrag.current
    val colors = MaterialTheme.colorScheme
    val moveUpLabel = stringResource(R.string.sideraven_move_up)
    val moveDownLabel = stringResource(R.string.sideraven_move_down)
    val pinEssentialLabel = stringResource(R.string.sideraven_pin_essential)
    var menuOpen by remember { mutableStateOf(false) }
    // Long-press then drag moves the pin; long-press and release opens its menu (as for Essentials).
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptics = LocalHapticFeedback.current
    val home = rememberSpringHome()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnDropped by rememberUpdatedState(onDropped)
    val notOpen = stringResource(R.string.sideraven_essential_not_open)
    val unpinLabel = stringResource(R.string.sideraven_unpin_essential)
    val resetLabel = stringResource(R.string.sideraven_reset_essential)
    val pinnedLabel = stringResource(R.string.sideraven_pinned_tab)
    val asleepLabel = stringResource(R.string.sideraven_asleep)
    val closeLabel = stringResource(R.string.sideraven_close_tab)
    val unloadLabel = stringResource(R.string.sideraven_unload)
    val renameLabel = stringResource(R.string.sideraven_rename)
    var renaming by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    bounds = it.boundsInRoot()
                    onBounds(bounds)
                }
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    // While dragged, the drag layer draws its floating copy.
                    alpha = if (dragging) 0f else 1f
                }
                .pointerInput(pin.id) {
                    var moved = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            moved = false
                            home.stop()
                            dragging = true
                            dragOffset = Offset.Zero
                            drag.start(DragItem(DragSource.Pinned, pin.title, pin.url, pin.icon), bounds.center)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            drag.move(bounds.center + dragOffset)
                            if (dragOffset.getDistance() > touchSlop) moved = true
                        },
                        onDragEnd = {
                            val zone = drag.end()
                            val dropAt = bounds.center + dragOffset
                            if (moved) currentOnDropped(pin.id, dropAt, zone) else menuOpen = true
                            dragging = false
                            // Put back where it was: it settles home; moved elsewhere: it is already in its new place.
                            if (zone == null && bounds.contains(dropAt)) home.offset(dragOffset) { dragOffset = it } else dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            drag.end()
                            if (!moved) menuOpen = true
                            dragging = false
                            home.offset(dragOffset) { dragOffset = it }
                        },
                    )
                }
                .height(LocalSidebarLook.current.tabRows.pinnedDp.dp)
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (dragging) colors.surfaceContainerHigh else if (pin.isSelected) sidebarFill(SidebarFill.High) else Color.Transparent)
                .clickable(role = Role.Button) { if (!dragging) onIntent(SidebarIntent.SelectPinnedTab(pin.id)) }
                .semantics {
                    contentDescription = pin.title
                    selected = pin.isSelected
                    stateDescription =
                        when {
                            pin.isGhost -> "$pinnedLabel, $notOpen"
                            pin.sleeping -> "$pinnedLabel, $asleepLabel"
                            else -> pinnedLabel
                        }
                    customActions =
                        buildList {
                            add(CustomAccessibilityAction(unpinLabel) { onIntent(SidebarIntent.UnpinPinnedTab(pin.id)); true })
                            add(CustomAccessibilityAction(renameLabel) { renaming = true; true })
                            if (!pin.isGhost) {
                                add(CustomAccessibilityAction(resetLabel) { onIntent(SidebarIntent.ResetPinnedTab(pin.id)); true })
                                add(CustomAccessibilityAction(unloadLabel) { onIntent(SidebarIntent.UnloadPinnedTab(pin.id)); true })
                                add(CustomAccessibilityAction(closeLabel) { onIntent(SidebarIntent.ClosePinnedTab(pin.id)); true })
                            }
                            // Dropping on Essentials is the touch way.
                            add(CustomAccessibilityAction(pinEssentialLabel) { onIntent(SidebarIntent.PinnedToEssentials(pin.id)); true })
                            neighbours.first?.let { above -> add(CustomAccessibilityAction(moveUpLabel) { onIntent(SidebarIntent.MovePinnedTab(pin.id, above)); true }) }
                            neighbours.second?.let { below -> add(CustomAccessibilityAction(moveDownLabel) { onIntent(SidebarIntent.MovePinnedTab(pin.id, below)); true }) }
                        }
                }
                .testTag(SidebarTestTag.PINNED_TAB),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The Active Tab Indicator, tinted with the Space's accent.
        if (pin.isSelected) {
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (showLabels) 12.dp + FolderIndent * depth else 0.dp, end = if (showLabels) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showLabels) Arrangement.Start else Arrangement.Center,
        ) {
            val iconModifier =
                Modifier.size(20.dp).alpha(
                    fadedAlpha(
                        when {
                            pin.isGhost -> PIN_GHOST_ALPHA
                            pin.sleeping -> SLEEP_ALPHA
                            else -> 1f
                        },
                    ),
                ).greyWhenSleeping(pin.sleeping)
            val icon = pin.icon
            if (icon != null) {
                Image(bitmap = icon.asImageBitmap(), contentDescription = null, modifier = iconModifier.clip(RoundedCornerShape(4.dp)))
            } else {
                Box(modifier = iconModifier) { LocalFaviconLoader.current(pin.url, 20.dp) }
            }
            if (showLabels) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = pin.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.weight(1f).alpha(
                            fadedAlpha(
                                when {
                                    pin.isGhost -> PIN_GHOST_ALPHA + 0.25f
                                    pin.sleeping -> SLEEP_ALPHA
                                    else -> 1f
                                },
                            ),
                        ),
                )
                // Closing leaves a Ghost: shown on the selected Pinned Tab, like the Tab Close Button.
                if (pin.isSelected) {
                    IconButton(
                        onClick = { onIntent(SidebarIntent.ClosePinnedTab(pin.id)) },
                        modifier = Modifier.size(40.dp).testTag(SidebarTestTag.PINNED_CLOSE),
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
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!pin.isGhost) {
                DropdownMenuItem(
                    text = { Text(resetLabel) },
                    onClick = {
                        menuOpen = false
                        onIntent(SidebarIntent.ResetPinnedTab(pin.id))
                    },
                )
                if (!pin.isSelected && !pin.sleeping) {
                    DropdownMenuItem(
                        text = { Text(unloadLabel) },
                        onClick = {
                            menuOpen = false
                            onIntent(SidebarIntent.UnloadPinnedTab(pin.id))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sideraven_unload_others)) },
                    onClick = {
                        menuOpen = false
                        onIntent(SidebarIntent.UnloadOthers(UnloadOthersFrom.Pinned(pin.id)))
                    },
                )
                DropdownMenuItem(
                    text = { Text(closeLabel) },
                    onClick = {
                        menuOpen = false
                        onIntent(SidebarIntent.ClosePinnedTab(pin.id))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(renameLabel) },
                onClick = {
                    menuOpen = false
                    renaming = true
                },
            )
            if (folders.isNotEmpty()) {
                MoveToFolderMenuItems(folders = folders, inFolder = depth > 0) { folderId ->
                    menuOpen = false
                    onIntent(SidebarIntent.MoveIntoFolder(pin.id, folderId))
                }
            }
            DropdownMenuItem(
                text = { Text(unpinLabel) },
                onClick = {
                    menuOpen = false
                    onIntent(SidebarIntent.UnpinPinnedTab(pin.id))
                },
            )
        }
        if (renaming) {
            RenameDialog(
                currentName = pin.title,
                onDismiss = { renaming = false },
                onRename = { name ->
                    renaming = false
                    onIntent(SidebarIntent.RenamePinnedTab(pin.id, name))
                },
            )
        }
    }
}

/** Indent per Folder level. */
private val FolderIndent = 16.dp

/** "Move to folder": expands in place into the Space's Folders, plus "Out of folder". */
@Composable
private fun MoveToFolderMenuItems(
    folders: List<SidebarFolder>,
    inFolder: Boolean,
    onMove: (folderId: String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.sideraven_move_to_folder)) },
        onClick = { open = !open },
        modifier = Modifier.testTag(SidebarTestTag.MOVE_TO_FOLDER),
    )
    if (open) {
        if (inFolder) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sideraven_out_of_folder)) },
                onClick = { onMove(null) },
                contentPadding = PaddingValues(start = 28.dp, end = 12.dp),
            )
        }
        folders.forEach { folder ->
            DropdownMenuItem(
                text = { Text(folder.name) },
                leadingIcon = { SpaceIcon(folder.icon, size = 16.dp) },
                onClick = { onMove(folder.id) },
                contentPadding = PaddingValues(start = 28.dp + FolderIndent * folder.depth, end = 12.dp),
            )
        }
    }
}

/** A Folder row: tap to collapse or expand; long-press for Edit, Move up/down and Delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: SidebarFolder,
    showLabels: Boolean,
    onIntent: (SidebarIntent) -> Unit,
    isFirst: Boolean,
    contents: List<SidebarPinnedTab> = emptyList(),
    onDropped: (dropAtInRoot: Offset) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var popupOpen by remember { mutableStateOf(false) }
    // Long-press then drag reorders Folders; long-press and release opens the menu.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptics = LocalHapticFeedback.current
    val home = rememberSpringHome()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnDropped by rememberUpdatedState(onDropped)
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val expandedLabel = stringResource(R.string.sideraven_folder_expanded)
    val collapsedLabel = stringResource(R.string.sideraven_folder_collapsed)
    val editLabel = stringResource(R.string.sideraven_folder_edit)
    val deleteLabel = stringResource(R.string.sideraven_folder_delete)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    if (dragging) shadowElevation = 6f
                }
                .pointerInput(folder.id) {
                    var moved = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            moved = false
                            home.stop()
                            dragging = true
                            dragOffset = Offset.Zero
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            if (dragOffset.getDistance() > touchSlop) moved = true
                        },
                        onDragEnd = {
                            val dropAt = bounds.center + dragOffset
                            if (moved) currentOnDropped(dropAt) else menuOpen = true
                            dragging = false
                            if (bounds.contains(dropAt)) home.offset(dragOffset) { dragOffset = it } else dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            if (!moved) menuOpen = true
                            dragging = false
                            home.offset(dragOffset) { dragOffset = it }
                        },
                    )
                }
                .height(40.dp)
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (dragging) colors.surfaceContainerHigh else Color.Transparent)
                .clickable(role = Role.Button) {
                    if (dragging) return@clickable
                    // Collapsed Mode has no room to expand in place: a popup lists the Folder's pins.
                    if (showLabels) onIntent(SidebarIntent.ToggleFolder(folder.id)) else popupOpen = true
                }
                .semantics {
                    contentDescription = folder.name
                    stateDescription = if (folder.expanded) expandedLabel else collapsedLabel
                    customActions =
                        listOf(
                            CustomAccessibilityAction(editLabel) { editing = true; true },
                            CustomAccessibilityAction(deleteLabel) { deleting = true; true },
                        )
                }
                .testTag(SidebarTestTag.FOLDER),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (showLabels) 12.dp + FolderIndent * folder.depth else 0.dp, end = if (showLabels) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showLabels) Arrangement.Start else Arrangement.Center,
        ) {
            SpaceIcon(folder.icon, size = 18.dp)
            if (showLabels) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val chevron by androidx.compose.animation.core.animateFloatAsState(
                    if (folder.expanded) 180f else 0f,
                    LocalSidebarMotion.current.fade(SideravenMotion.CHEVRON_ROTATE_MS),
                    label = "folderChevron",
                )
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_chevron_down_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevron },
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = popupOpen,
            onDismissRequest = { popupOpen = false },
            modifier = Modifier.testTag(SidebarTestTag.FOLDER_POPUP),
        ) {
            contents.forEach { pin ->
                DropdownMenuItem(
                    text = { Text(pin.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Box(Modifier.size(18.dp).alpha(if (pin.isGhost) PIN_GHOST_ALPHA else 1f)) { LocalFaviconLoader.current(pin.url, 18.dp) }
                    },
                    onClick = {
                        popupOpen = false
                        onIntent(SidebarIntent.SelectPinnedTab(pin.id))
                    },
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(editLabel) }, onClick = { menuOpen = false; editing = true })
            if (!isFirst) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sideraven_move_up)) },
                    onClick = { menuOpen = false; onIntent(SidebarIntent.MoveFolder(folder.id, -1)) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sideraven_move_down)) },
                onClick = { menuOpen = false; onIntent(SidebarIntent.MoveFolder(folder.id, 1)) },
            )
            DropdownMenuItem(text = { Text(deleteLabel) }, onClick = { menuOpen = false; deleting = true })
        }
    }
    if (editing) {
        SpaceDialog(
            title = editLabel,
            confirmLabel = stringResource(R.string.sideraven_space_save),
            onDismiss = { editing = false },
            onConfirm = { name, icon, _ ->
                editing = false
                onIntent(SidebarIntent.EditFolder(folder.id, name, icon))
            },
            initialName = folder.name,
            initialIcon = folder.icon,
            showAccent = false,
        )
    }
    if (deleting) {
        // Deleting asks what happens to the Folder's Pinned Tabs.
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.sideraven_folder_delete_confirm, folder.name)) },
            text = {
                Text(
                    listOfNotNull(
                        stringResource(R.string.sideraven_folder_delete_body, folder.count),
                        stringResource(R.string.sideraven_zen_delete_reaches).takeIf { LocalZenSyncOn.current },
                    ).joinToString(" "),
                    modifier = Modifier.testTag(SidebarTestTag.DELETE_MESSAGE),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { deleting = false; onIntent(SidebarIntent.DeleteFolder(folder.id, keepPins = true)) },
                    modifier = Modifier.testTag(SidebarTestTag.FOLDER_DELETE_KEEP),
                ) { Text(stringResource(R.string.sideraven_folder_keep_pins)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = false; onIntent(SidebarIntent.DeleteFolder(folder.id, keepPins = false)) },
                    modifier = Modifier.testTag(SidebarTestTag.FOLDER_DELETE_REMOVE),
                ) { Text(stringResource(R.string.sideraven_folder_remove_pins)) }
            },
        )
    }
}

/** Row keys for Folder rows among the pin rows' bounds. */
private const val FOLDER_KEY_PREFIX = "folder:"

/** The Pinned Tabs inside [folderId], nested Folders included, in order. */
private fun folderContents(
    folderId: String,
    folders: List<SidebarFolder>,
    pinned: List<SidebarPinnedTab>,
): List<SidebarPinnedTab> {
    val ids = mutableSetOf(folderId)
    var added = true
    while (added) {
        added = folders.filter { it.parentId in ids && it.id !in ids }.map { it.id }.let { ids.addAll(it) }
    }
    return pinned.filter { it.folderId in ids }
}
