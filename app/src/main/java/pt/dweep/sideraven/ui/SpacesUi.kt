/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.isSystemInDarkTheme
import pt.dweep.sideraven.DEFAULT_SPACE_ACCENT
import pt.dweep.sideraven.theme.SpaceAccents
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import pt.dweep.sideraven.ZenIcons
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mozilla.fenix.R
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarSpace
import mozilla.components.ui.icons.R as iconsR

/** Emoji offered when creating a Space (any emoji can be typed instead). */
internal val SpaceEmojiPresets =
    listOf("📁", "🏠", "💼", "📚", "🎮", "🎵", "🛒", "✈️", "💡", "🧪", "🎨", "📰", "💬", "🏃", "🍳", "💰", "🌱")

/** The Space Header's fold state and control, when the Sidebar shows Spaces as a header. */
internal data class SpaceHeaderFold(
    val folded: Boolean,
    val canFold: Boolean,
    val onToggle: () -> Unit,
)

/**
 * The Spaces above their Pinned Tabs: the 1.2 icon row (tap to switch, + to create), or, with
 * [header], the Space Header — the current Space's name in one row, the fold control in it, and a
 * sideways swipe between Spaces.
 */
@Composable
internal fun SpaceRow(
    spaces: List<SidebarSpace>,
    onIntent: (SidebarIntent) -> Unit,
    syncProblem: Boolean = false,
    zenSyncOn: Boolean = false,
    dropTarget: String? = null,
    onIconBounds: (spaceId: String, bounds: androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
    header: SpaceHeaderFold? = null,
    modifier: Modifier = Modifier,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    // Deleting a Space that has Pinned Tabs asks first; otherwise Undo is enough.
    // A synced Space's delete reaches other devices, so it always asks while Zen Sync is on.
    val requestDelete: (SidebarSpace) -> Unit = { space ->
        if (space.pinnedCount > 0 || (zenSyncOn && !space.isLocal)) confirmingDelete = space.id else onIntent(SidebarIntent.DeleteSpace(space.id))
    }
    val currentLabel = stringResource(R.string.sideraven_space_current)
    val editLabel = stringResource(R.string.sideraven_space_edit)
    val moveEarlierLabel = stringResource(R.string.sideraven_move_earlier)
    val moveLaterLabel = stringResource(R.string.sideraven_move_later)
    val deleteLabel = stringResource(R.string.sideraven_space_delete)
    val unloadAllLabel = stringResource(R.string.sideraven_unload_all)
    val newSpaceLabel = stringResource(R.string.sideraven_new_space)
    val currentSpace = spaces.firstOrNull { it.isCurrent }

    if (header != null && currentSpace != null) {
        SpaceHeader(
            spaces = spaces,
            fold = header,
            dropTarget = dropTarget,
            onIconBounds = onIconBounds,
            syncProblem = syncProblem,
            menuOpen = menuOpen,
            listOpen = listOpen,
            onOpenList = { listOpen = true },
            onOpenMenu = { menuOpen = true },
            onCloseMenu = { menuOpen = false },
            onCloseList = { listOpen = false },
            // The menus live in the Header's icon Box, so each one grows out of the icon itself,
            // like the icon row's menu does.
            menuItems = {
                SpaceMenuItems(
                    space = currentSpace,
                    canDelete = spaces.size > 1,
                    onDismiss = { menuOpen = false },
                    onEdit = { editing = currentSpace.id },
                    onNewFolder = { creatingFolder = true },
                    onUnload = { onIntent(SidebarIntent.UnloadSpace(currentSpace.id)) },
                    onCloseUnpinned = { onIntent(SidebarIntent.CloseUnpinnedTabs(currentSpace.id)) },
                    onDelete = { requestDelete(currentSpace) },
                )
            },
            listItems = {
                spaces.forEach { space ->
                    DropdownMenuItem(
                        leadingIcon = { SpaceIcon(space.icon, size = 20.dp) },
                        text = {
                            Text(
                                text = space.name,
                                color = if (space.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        trailingIcon =
                            if (space.isCurrent) {
                                {
                                    Icon(
                                        painter = painterResource(iconsR.drawable.mozac_ic_checkmark_24),
                                        contentDescription = currentLabel,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        onClick = {
                            listOpen = false
                            if (!space.isCurrent) onIntent(SidebarIntent.SwitchSpace(space.id))
                        },
                    )
                }
                DropdownMenuItem(
                    leadingIcon = { Icon(painterResource(iconsR.drawable.mozac_ic_plus_24), contentDescription = null) },
                    text = { Text(newSpaceLabel) },
                    onClick = {
                        listOpen = false
                        creating = true
                    },
                )
            },
            onIntent = onIntent,
            modifier = modifier,
        )
    } else {
        val itemWidthPx = with(LocalDensity.current) { (SPACE_ITEM_SIZE + SPACE_ITEM_GAP).toPx() }
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val haptics = LocalHapticFeedback.current
        val home = rememberSpringHome()
        val currentOnIntent by rememberUpdatedState(onIntent)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag(SidebarTestTag.SPACE_ROW),
            horizontalArrangement = Arrangement.spacedBy(SPACE_ITEM_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            spaces.forEachIndexed { index, space ->
                key(space.id) {
                    var itemMenuOpen by remember { mutableStateOf(false) }
                    var dragX by remember { mutableStateOf(0f) }
                    var dragging by remember { mutableStateOf(false) }
                    Box(
                        modifier =
                            Modifier
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationX = dragX }
                                // Long-press then drag reorders; long-press and release opens the menu.
                                .pointerInput(space.id, index, spaces.size) {
                                    var moved = false
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            moved = false
                                            home.stop()
                                            dragging = true
                                            dragX = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragX += amount.x
                                            if (kotlin.math.abs(dragX) > touchSlop) moved = true
                                        },
                                        onDragEnd = {
                                            val target = (index + (dragX / itemWidthPx).roundToInt()).coerceIn(0, spaces.size - 1)
                                            if (!moved) {
                                                itemMenuOpen = true
                                            } else if (target != index) {
                                                currentOnIntent(SidebarIntent.MoveSpace(space.id, target))
                                            }
                                            dragging = false
                                            // Moved: it is already in its new place; put back: it settles home.
                                            if (moved && target != index) dragX = 0f else home.axis(dragX) { dragX = it }
                                        },
                                        onDragCancel = {
                                            if (!moved) itemMenuOpen = true
                                            dragging = false
                                            home.axis(dragX) { dragX = it }
                                        },
                                    )
                                }
                                .size(SPACE_ITEM_SIZE)
                                .onGloballyPositioned { onIconBounds(space.id, it.boundsInRoot()) }
                                .clip(CircleShape)
                                .clickable(role = Role.Tab) { if (!dragging) onIntent(SidebarIntent.SwitchSpace(space.id)) }
                                .semantics {
                                    contentDescription = space.name
                                    selected = space.isCurrent
                                    if (space.isCurrent) stateDescription = currentLabel
                                    customActions =
                                        buildList {
                                            add(CustomAccessibilityAction(editLabel) { editing = space.id; true })
                                            if (spaces.size > 1) {
                                                add(CustomAccessibilityAction(deleteLabel) { requestDelete(space); true })
                                            }
                                            // TalkBack's way to reorder (dragging is the touch way).
                                            if (index > 0) add(CustomAccessibilityAction(moveEarlierLabel) { onIntent(SidebarIntent.MoveSpace(space.id, index - 1)); true })
                                            if (index < spaces.lastIndex) add(CustomAccessibilityAction(moveLaterLabel) { onIntent(SidebarIntent.MoveSpace(space.id, index + 1)); true })
                                        }
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        DropdownMenu(
                            expanded = itemMenuOpen,
                            onDismissRequest = { itemMenuOpen = false },
                            modifier = Modifier.testTag(SidebarTestTag.SPACE_MENU),
                        ) {
                            SpaceMenuItems(
                                space = space,
                                canDelete = spaces.size > 1,
                                onDismiss = { itemMenuOpen = false },
                                onEdit = { editing = space.id },
                                onNewFolder = { creatingFolder = true },
                                onUnload = { onIntent(SidebarIntent.UnloadSpace(space.id)) },
                                onCloseUnpinned = { onIntent(SidebarIntent.CloseUnpinnedTabs(space.id)) },
                                onDelete = { requestDelete(space) },
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .then(
                                        // A tab dragged over a Space icon highlights it as the drop target.
                                        if (space.isCurrent || space.id == dropTarget) {
                                            Modifier
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpaceIcon(space.icon, size = 18.dp)
                        }
                    }
                }
            }
            if (syncProblem) SyncProblemDot()
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { creating = true }
                        .semantics { contentDescription = newSpaceLabel }
                        .testTag(SidebarTestTag.NEW_SPACE),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_plus_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (creating) {
        SpaceDialog(
            title = stringResource(R.string.sideraven_new_space),
            confirmLabel = stringResource(R.string.sideraven_space_create),
            onDismiss = { creating = false },
            onConfirm = { name, icon, accent ->
                creating = false
                onIntent(SidebarIntent.CreateSpace(name, icon, accent))
            },
        )
    }
    if (creatingFolder) {
        SpaceDialog(
            title = stringResource(R.string.sideraven_new_folder),
            confirmLabel = stringResource(R.string.sideraven_space_create),
            onDismiss = { creatingFolder = false },
            onConfirm = { name, icon, _ ->
                creatingFolder = false
                onIntent(SidebarIntent.CreateFolder(name, icon))
            },
            initialIcon = pt.dweep.sideraven.DEFAULT_FOLDER_ICON,
            showAccent = false,
        )
    }
    spaces.firstOrNull { it.id == confirmingDelete }?.let { space ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(R.string.sideraven_space_delete_confirm, space.name)) },
            text = {
                Text(
                    listOfNotNull(
                        stringResource(R.string.sideraven_space_delete_confirm_body, space.pinnedCount).takeIf { space.pinnedCount > 0 },
                        stringResource(R.string.sideraven_zen_delete_reaches).takeIf { zenSyncOn && !space.isLocal },
                    ).joinToString(" "),
                    modifier = Modifier.testTag(SidebarTestTag.DELETE_MESSAGE),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = null
                        onIntent(SidebarIntent.DeleteSpace(space.id))
                    },
                    modifier = Modifier.testTag(SidebarTestTag.SPACE_DELETE_CONFIRM),
                ) { Text(stringResource(R.string.sideraven_space_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text(stringResource(R.string.sideraven_cancel)) }
            },
        )
    }
    spaces.firstOrNull { it.id == editing }?.let { space ->
        SpaceDialog(
            title = editLabel,
            confirmLabel = stringResource(R.string.sideraven_space_save),
            onDismiss = { editing = null },
            onConfirm = { name, icon, accent ->
                editing = null
                onIntent(SidebarIntent.EditSpace(space.id, name, icon, accent))
            },
            // While Zen Sync is on, a Space can stay on this tablet only.
            initialLocal = space.isLocal.takeIf { zenSyncOn },
            onConfirmLocal = { local -> if (local != space.isLocal) onIntent(SidebarIntent.SetSpaceLocal(space.id, local)) },
            initialName = space.name,
            initialIcon = space.icon,
            initialAccent = space.accent,
        )
    }
}

/** A Space's menu: its name, then edit, new folder, unload, close unpinned and delete. */
@Composable
private fun SpaceMenuItems(
    space: SidebarSpace,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onNewFolder: () -> Unit,
    onUnload: () -> Unit,
    onCloseUnpinned: () -> Unit,
    onDelete: () -> Unit,
) {
    // The Space's name, now that the Sidebar no longer shows it above the tabs.
    Text(
        text = space.name,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.sideraven_space_edit)) },
        onClick = {
            onDismiss()
            onEdit()
        },
    )
    if (space.isCurrent) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sideraven_new_folder)) },
            onClick = {
                onDismiss()
                onNewFolder()
            },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.sideraven_unload_all)) },
        onClick = {
            onDismiss()
            onUnload()
        },
    )
    if (space.unpinnedCount > 0) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.sideraven_close_unpinned)) },
            onClick = {
                onDismiss()
                onCloseUnpinned()
            },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.sideraven_space_delete)) },
        enabled = canDelete,
        onClick = {
            onDismiss()
            onDelete()
        },
    )
}

/**
 * The Space Header: the current Space's icon, name and fold control in one 40 dp row. Tap the icon
 * for every Space (and a new one), long-press it for the Space's menu; tap the name to fold its
 * Pinned Tabs; swipe sideways for the previous or next Space.
 */
@Composable
private fun SpaceHeader(
    spaces: List<SidebarSpace>,
    fold: SpaceHeaderFold,
    dropTarget: String?,
    onIconBounds: (spaceId: String, bounds: androidx.compose.ui.geometry.Rect) -> Unit,
    syncProblem: Boolean,
    menuOpen: Boolean,
    listOpen: Boolean,
    onOpenList: () -> Unit,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onCloseList: () -> Unit,
    menuItems: @Composable ColumnScope.() -> Unit,
    listItems: @Composable ColumnScope.() -> Unit,
    onIntent: (SidebarIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = spaces.firstOrNull { it.isCurrent } ?: return
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { SPACE_SWITCH_THRESHOLD.toPx() }
    val currentSpaces by rememberUpdatedState(spaces)
    val currentOnIntent by rememberUpdatedState(onIntent)
    val foldLabel = stringResource(if (fold.folded) R.string.sideraven_unfold_pinned else R.string.sideraven_fold_pinned)
    val previousLabel = stringResource(R.string.sideraven_previous_space)
    val nextLabel = stringResource(R.string.sideraven_next_space)
    val chevron by animateFloatAsState(if (fold.folded) 0f else 180f, label = "spaceHeaderChevron")
    // A neighbouring Space: -1 the one before, +1 the one after (no wrapping).
    fun neighbour(step: Int): SidebarSpace? {
        val list = currentSpaces
        val at = list.indexOfFirst { it.isCurrent }
        return list.getOrNull(at + step)
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SPACE_HEADER_HEIGHT)
                .padding(horizontal = 8.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(total) > threshold) {
                                // Swiping left brings the next Space in, as a page would.
                                neighbour(if (total < 0) 1 else -1)?.let { space ->
                                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    currentOnIntent(SidebarIntent.SwitchSpace(space.id))
                                }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        total += amount
                    }
                }.semantics {
                    customActions =
                        listOfNotNull(
                            neighbour(-1)?.let { space -> CustomAccessibilityAction(previousLabel) { onIntent(SidebarIntent.SwitchSpace(space.id)).let { true } } },
                            neighbour(1)?.let { space -> CustomAccessibilityAction(nextLabel) { onIntent(SidebarIntent.SwitchSpace(space.id)).let { true } } },
                        )
                }.testTag(SidebarTestTag.SPACE_HEADER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(SPACE_HEADER_ICON)
                    .clip(CircleShape)
                    .onGloballyPositioned { onIconBounds(current.id, it.boundsInRoot()) }
                    // Tap lists the Spaces; long-press is the Space's own menu, as in the icon row.
                    .combinedClickable(role = Role.Button, onClick = onOpenList, onLongClick = onOpenMenu)
                    .then(
                        if (current.id == dropTarget) {
                            Modifier.border(2.dp, colors.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .semantics { contentDescription = current.name },
            contentAlignment = Alignment.Center,
        ) {
            // Both menus hang off the icon, like the icon row's menu hangs off its icon.
            DropdownMenu(
                expanded = listOpen,
                onDismissRequest = onCloseList,
                modifier = Modifier.testTag(SidebarTestTag.SPACE_MENU),
            ) {
                listItems()
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onCloseMenu,
                modifier = Modifier.testTag(SidebarTestTag.SPACE_MENU),
            ) {
                menuItems()
            }
            SpaceIcon(current.icon, size = 20.dp)
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .height(SPACE_HEADER_HEIGHT)
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (fold.canFold) {
                            Modifier.clickable(role = Role.Button, onClickLabel = foldLabel) { fold.onToggle() }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = current.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Only the Divider under the pins says the count; the Header repeats itself otherwise.
            if (fold.canFold) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_chevron_down_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevron },
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        if (syncProblem) SyncProblemDot()
    }
}

/** Folded Pinned Tabs, drawn as a stack: two rules, the shorter under the longer. */
@Composable
private fun PinnedStackMark(color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(width = 12.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(color))
        Box(Modifier.size(width = 8.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(color))
    }
}

/** A Space icon's touch target and the gap between icons. */
private val SPACE_ITEM_SIZE = 44.dp
private val SPACE_ITEM_GAP = 2.dp

/** The Space Header: one row, its icon, and how far a sideways swipe must go to change Space. */
private val SPACE_HEADER_HEIGHT = 40.dp
private val SPACE_HEADER_ICON = 36.dp
private val SPACE_SWITCH_THRESHOLD = 48.dp

internal fun accentLabel(key: String): Int =
    when (key) {
        "rose" -> R.string.sideraven_accent_rose
        "violet" -> R.string.sideraven_accent_violet
        "blue" -> R.string.sideraven_accent_blue
        "teal" -> R.string.sideraven_accent_teal
        "green" -> R.string.sideraven_accent_green
        "amber" -> R.string.sideraven_accent_amber
        "slate" -> R.string.sideraven_accent_slate
        else -> R.string.sideraven_accent_ember
    }

/** Collapsed Mode's Space switcher: the current Space's icon, opening a menu of all Spaces. */
@Composable
internal fun CollapsedSpaceButton(
    spaces: List<SidebarSpace>,
    onIntent: (SidebarIntent) -> Unit,
) {
    val current = spaces.firstOrNull { it.isCurrent } ?: return
    var menuOpen by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val switchLabel = stringResource(R.string.sideraven_spaces)
    val currentLabel = stringResource(R.string.sideraven_space_current)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button) { menuOpen = true }
                .semantics { contentDescription = "$switchLabel: ${current.name}" }
                .testTag(SidebarTestTag.SPACE_SWITCHER),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            SpaceIcon(current.icon, size = 18.dp)
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.testTag(SidebarTestTag.SPACE_MENU),
        ) {
            spaces.forEach { space ->
                DropdownMenuItem(
                    text = { Text(space.name) },
                    leadingIcon = { SpaceIcon(space.icon, size = 18.dp) },
                    trailingIcon =
                        if (space.isCurrent) {
                            {
                                Icon(
                                    painter = painterResource(iconsR.drawable.mozac_ic_checkmark_24),
                                    contentDescription = currentLabel,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        },
                    onClick = {
                        menuOpen = false
                        onIntent(SidebarIntent.SwitchSpace(space.id))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sideraven_new_space)) },
                leadingIcon = {
                    Icon(painter = painterResource(iconsR.drawable.mozac_ic_plus_24), contentDescription = null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menuOpen = false
                    creating = true
                },
            )
        }
    }
    if (creating) {
        SpaceDialog(
            title = stringResource(R.string.sideraven_new_space),
            confirmLabel = stringResource(R.string.sideraven_space_create),
            onDismiss = { creating = false },
            onConfirm = { name, icon, accent ->
                creating = false
                onIntent(SidebarIntent.CreateSpace(name, icon, accent))
            },
        )
    }
}

/**
 * A Space or Folder icon: one of Zen's icons (tinted), or an emoji. A Zen icon this version
 * doesn't know renders a neutral dot; the stored value is kept as is.
 */
@Composable
internal fun SpaceIcon(
    icon: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val zen = ZenIcons.drawableFor(icon)
    when {
        zen != null ->
            Icon(
                painter = painterResource(zen),
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        ZenIcons.isZenIcon(icon) || icon.isBlank() ->
            Box(Modifier.size(size * 0.4f).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
        else -> Text(text = icon, fontSize = (size.value * 0.9f).sp, maxLines = 1)
    }
}

/** Name and emoji for a new or edited Space. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpaceDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, accent: String) -> Unit,
    initialName: String = "",
    initialIcon: String = SpaceEmojiPresets[2],
    initialAccent: String = DEFAULT_SPACE_ACCENT,
    showAccent: Boolean = true,
    initialLocal: Boolean? = null,
    onConfirmLocal: (Boolean) -> Unit = {},
) {
    var local by rememberSaveable { mutableStateOf(initialLocal ?: false) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var icon by rememberSaveable { mutableStateOf(initialIcon) }
    var accent by rememberSaveable { mutableStateOf(initialAccent) }
    var zenTab by rememberSaveable { mutableStateOf(ZenIcons.isZenIcon(initialIcon)) }
    val dark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.sideraven_space_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(SidebarTestTag.SPACE_NAME_FIELD),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sideraven_space_icon), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    // Two icon sets: emoji, and Zen's own icons (so desktop Spaces look the same).
                    IconSetTab(stringResource(R.string.sideraven_icons_emoji), selected = !zenTab) { zenTab = false }
                    IconSetTab(stringResource(R.string.sideraven_icons_zen), selected = zenTab, testTag = SidebarTestTag.ZEN_ICONS_TAB) { zenTab = true }
                }
                Spacer(Modifier.height(4.dp))
                if (zenTab) {
                    Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        FlowRow {
                            ZenIcons.all.keys.forEach { name ->
                                val value = ZenIcons.iconFor(name)
                                val chosen = value == icon
                                Box(
                                    modifier =
                                        Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .then(if (chosen) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                                            .clickable(role = Role.RadioButton) { icon = value }
                                            .semantics {
                                                contentDescription = name.replace('-', ' ')
                                                selected = chosen
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SpaceIcon(value, size = 20.dp)
                                }
                            }
                        }
                    }
                }
                if (!zenTab) FlowRow {
                    SpaceEmojiPresets.forEach { emoji ->
                        val chosen = emoji == icon
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .then(if (chosen) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                                    .clickable(role = Role.RadioButton) { icon = emoji }
                                    .semantics { selected = chosen },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
                if (initialLocal != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Switch) { local = !local }
                                .testTag(SidebarTestTag.SPACE_LOCAL),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.sideraven_space_local), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.sideraven_space_local_summary), style = MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.Switch(checked = local, onCheckedChange = null)
                    }
                }
                if (showAccent) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.sideraven_space_colour), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow {
                    SpaceAccents.all.forEach { option ->
                        val chosen = option.key == accent
                        val label = stringResource(accentLabel(option.key))
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.RadioButton) { accent = option.key }
                                    .semantics {
                                        contentDescription = label
                                        selected = chosen
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(if (chosen) 30.dp else 24.dp)
                                        .clip(CircleShape)
                                        .background(if (dark) option.dark else option.light)
                                        .then(
                                            if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                                        ),
                            )
                        }
                    }
                }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (initialLocal != null) onConfirmLocal(local)
                    onConfirm(name.trim(), icon, accent)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(SidebarTestTag.SPACE_CONFIRM),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sideraven_cancel)) }
        },
    )
}

/** One of the icon picker's two tabs. */
@Composable
private fun IconSetTab(
    label: String,
    selected: Boolean,
    testTag: String = "",
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected }.testTag(testTag),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Zen Sync's last attempt failed: a small dot (details are in Settings > Sync). */
@Composable
internal fun SyncProblemDot() {
    val label = stringResource(R.string.sideraven_zen_problem)
    Box(
        modifier =
            Modifier
                .size(12.dp)
                .semantics { contentDescription = label }
                .testTag(SidebarTestTag.SYNC_PROBLEM),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
    }
}
