/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import pt.dweep.sideraven.theme.SidebarFill
import pt.dweep.sideraven.theme.sidebarFill
import pt.dweep.sideraven.UnloadOthersFrom
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.zIndex
import pt.dweep.sideraven.theme.SideravenMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pt.dweep.sideraven.essentialsGridColumns
import pt.dweep.sideraven.EssentialsPerRow
import pt.dweep.sideraven.ESSENTIALS_GRID_VISIBLE_ROWS
import pt.dweep.sideraven.ESSENTIALS_GRID_MAX_COLUMNS
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.IconButton
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarIntent
import mozilla.components.ui.icons.R as iconsR

/** Tile heights: regular, and the larger tiles of a short (1–3) row. */
private val TileHeight = 48.dp
private val LargeTileHeight = 56.dp
private val TileGap = 4.dp

/** Ghost Essentials are dimmed. */
private const val GHOST_ALPHA = 0.45f

/**
 * The Essentials Section. Persistent Mode: a grid that adapts to the count (1–3 stretch across one
 * row; 4+ use four columns) and scrolls by itself, with an edge fade, past three rows.
 * Collapsed Mode: a single column
 * that scrolls on its own once taller than [collapsedMaxHeight].
 */
@Composable
fun EssentialsSection(
    essentials: List<SidebarEssential>,
    asGrid: Boolean,
    collapsedMaxHeight: Dp,
    onIntent: (SidebarIntent) -> Unit,
    // Collapsed Mode's scroll, so Section Sizing can see how much the column holds.
    collapsedScroll: ScrollState = rememberScrollState(),
    dropTarget: DropTarget = DropTarget.None,
    newUrls: Set<String> = emptySet(),
    // Whether there's a Space to pin an Essential to (TalkBack's "Pin to this space").
    canPinToSpace: Boolean = false,
) {
    val label = stringResource(R.string.sideraven_essentials)
    val colors = MaterialTheme.colorScheme
    // Drop-target outline while a tab is dragged over the section.
    val outline =
        when (dropTarget) {
            DropTarget.PinAccepted -> Modifier.border(2.dp, colors.primary, MaterialTheme.shapes.medium)
            DropTarget.PinRejected -> Modifier.border(1.dp, colors.outline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            DropTarget.None -> Modifier
        }
    // Essential drags: tile bounds (root coordinates) to find where a dragged Essential lands.
    val tileBounds = remember { mutableStateMapOf<String, Rect>() }
    val onEssentialDropped: (String, Offset, DragZone?) -> Unit = { url, dropAt, zone ->
        when (zone) {
            // Dropped on Pin: it becomes one of the Space's Pinned Tabs.
            DragZone.Pin -> onIntent(SidebarIntent.EssentialToSpace(url))
            // Dropped on the Tab List: unpin.
            DragZone.Unpin -> onIntent(SidebarIntent.UnpinEssential(url))
            else ->
                essentials.indexOfFirst { tileBounds[it.url]?.contains(dropAt) == true }
                    .takeIf { it >= 0 && essentials[it].url != url }
                    ?.let { onIntent(SidebarIntent.MoveEssential(url, it)) }
        }
    }
    val sectionModifier =
        Modifier
            .then(outline)

    if (essentials.isEmpty()) return

    if (asGrid) {
        val perRow = LocalSidebarLook.current.essentialsPerRow
        val columns = essentialsGridColumns(essentials.size, perRow)
        // Few Essentials stretch into large tiles only while the grid adapts to the count.
        val tileHeight = if (perRow == EssentialsPerRow.Auto && essentials.size < ESSENTIALS_GRID_MAX_COLUMNS) LargeTileHeight else TileHeight
        val maxGridHeight = TileHeight * ESSENTIALS_GRID_VISIBLE_ROWS + TileGap * (ESSENTIALS_GRID_VISIBLE_ROWS - 1)
        val scroll = rememberScrollState()
        val fade = 16.dp
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .then(sectionModifier)
                    .semantics { contentDescription = label }
                    .testTag(SidebarTestTag.ESSENTIALS)
                    // Count changes resize the grid smoothly.
                    .animateHeight(LocalSidebarMotion.current.move(SideravenMotion.ITEM_PLACEMENT_MS, SideravenMotion.EaseInOutStrong))
                    .heightIn(max = maxGridHeight)
                    // Past three rows the grid scrolls; a fade marks the clipped edge.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fadePx = fade.toPx()
                        if (scroll.canScrollForward) {
                            drawRect(
                                brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fadePx, endY = size.height),
                                topLeft = Offset(0f, size.height - fadePx),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        if (scroll.canScrollBackward) {
                            drawRect(
                                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fadePx),
                                size = Size(size.width, fadePx),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                    .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            essentials.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { essential ->
                        key(essential.url) {
                            EssentialTile(
                                essential,
                                onIntent,
                                Modifier.weight(1f).onGloballyPositioned { tileBounds[essential.url] = it.boundsInRoot() },
                                onDropped = onEssentialDropped,
                                height = tileHeight,
                                animateEntrance = essential.url in newUrls,
                                onMoveEarlier = essentials.moveTo(essential, -1, onIntent),
                                onMoveLater = essentials.moveTo(essential, 1, onIntent),
                                canPinToSpace = canPinToSpace,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = collapsedMaxHeight)
                    .verticalScroll(collapsedScroll)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .then(sectionModifier)
                    .semantics { contentDescription = label }
                    .testTag(SidebarTestTag.ESSENTIALS),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            essentials.forEach { essential ->
                key(essential.url) {
                    EssentialTile(
                        essential,
                        onIntent,
                        Modifier.fillMaxWidth().onGloballyPositioned { tileBounds[essential.url] = it.boundsInRoot() },
                        onDropped = onEssentialDropped,
                        animateEntrance = essential.url in newUrls,
                        onMoveEarlier = essentials.moveTo(essential, -1, onIntent),
                        onMoveLater = essentials.moveTo(essential, 1, onIntent),
                        canPinToSpace = canPinToSpace,
                    )
                }
            }
        }
    }
}

/** Moving [essential] [step] places along, or null when that's past either end. */
private fun List<SidebarEssential>.moveTo(
    essential: SidebarEssential,
    step: Int,
    onIntent: (SidebarIntent) -> Unit,
): (() -> Unit)? {
    val to = indexOf(essential) + step
    return if (to in indices) {
        { onIntent(SidebarIntent.MoveEssential(essential.url, to)) }
    } else {
        null
    }
}

@Composable
private fun EssentialTile(
    essential: SidebarEssential,
    onIntent: (SidebarIntent) -> Unit,
    modifier: Modifier,
    onDropped: (url: String, dropAtInRoot: Offset, zone: DragZone?) -> Unit,
    height: Dp = TileHeight,
    animateEntrance: Boolean = false,
    // TalkBack's way to reorder (dragging is the touch way); null at either end.
    onMoveEarlier: (() -> Unit)? = null,
    onMoveLater: (() -> Unit)? = null,
    canPinToSpace: Boolean = false,
) {
    val drag = LocalSidebarDrag.current
    val pinToSpaceLabel = stringResource(R.string.sideraven_pin_to_this_space)
    val moveEarlierLabel = stringResource(R.string.sideraven_move_earlier)
    val moveLaterLabel = stringResource(R.string.sideraven_move_later)
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    // Long-press then drag moves or unpins; long-press and release opens the menu.
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptics = LocalHapticFeedback.current
    val home = rememberSpringHome()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnDropped by rememberUpdatedState(onDropped)
    // A newly pinned Essential enters with scale 0.95 -> 1 and a fade (180ms).
    // Only a newly pinned Essential enters; a tile rebuilt by a mode or layout change is already there.
    val entrance = remember { Animatable(if (animateEntrance) 0f else 1f) }
    val motion = LocalSidebarMotion.current
    LaunchedEffect(Unit) { if (entrance.value < 1f) entrance.animateTo(1f, motion.fade(SideravenMotion.ITEM_ENTER_MS)) }
    val notOpen = stringResource(R.string.sideraven_essential_not_open)
    val unpinLabel = stringResource(R.string.sideraven_unpin_essential)
    val asleepLabel = stringResource(R.string.sideraven_asleep)
    val resetLabel = stringResource(R.string.sideraven_reset_essential)
    val unloadLabel = stringResource(R.string.sideraven_unload)
    val closeLabel = stringResource(R.string.sideraven_close_tab)
    val renameLabel = stringResource(R.string.sideraven_rename)
    var renaming by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.small

    Box(
        modifier =
            modifier
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer {
                    // Reduced motion: fade only, no growing.
                    val enter = if (motion.moves) 0.95f + 0.05f * entrance.value else 1f
                    val lift = if (dragging) 1.03f else 1f
                    scaleX = enter * lift
                    scaleY = enter * lift
                    // While dragged, the drag layer draws its floating copy.
                    alpha = if (dragging) 0f else entrance.value
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                }
                .pointerInput(essential.url) {
                    var moved = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            moved = false
                            home.stop()
                            dragging = true
                            dragOffset = Offset.Zero
                            drag.start(DragItem(DragSource.Essential, essential.title, essential.url, essential.icon), bounds.center)
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
                            if (moved) currentOnDropped(essential.url, dropAt, zone) else menuOpen = true
                            dragging = false
                            // Put back where it was: it settles home; moved elsewhere: it is already in its new place.
                            if (zone == null && bounds.contains(dropAt)) home.offset(dragOffset) { dragOffset = it } else dragOffset = Offset.Zero
                        },
                        // The tile's click handler takes the "up" of a plain long-press, which ends the
                        // detector as a cancel: a cancel without movement still opens the menu.
                        onDragCancel = {
                            drag.end()
                            if (!moved) menuOpen = true
                            dragging = false
                            home.offset(dragOffset) { dragOffset = it }
                        },
                    )
                }
                .height(height)
                .clip(shape)
                .background(sidebarFill(if (essential.isSelected) SidebarFill.High else SidebarFill.Container))
                .then(
                    when {
                        essential.isSelected -> Modifier.border(1.5.dp, colors.primary, shape)
                        essential.isGhost -> Modifier.border(1.dp, colors.outlineVariant, shape)
                        else -> Modifier
                    },
                )
                // The drag detector above opens the menu on release; the tap ending a long press is
                // ignored here. Nothing here may consume the gesture, or drags break.
                .clickable(indication = ripple(), interactionSource = null, role = Role.Button) {
                    if (!dragging) onIntent(SidebarIntent.SelectEssential(essential.url))
                }
                .semantics {
                    contentDescription = essential.title
                    selected = essential.isSelected
                    if (essential.isGhost) stateDescription = notOpen else if (essential.sleeping) stateDescription = asleepLabel
                    customActions =
                        buildList {
                            add(CustomAccessibilityAction(unpinLabel) { onIntent(SidebarIntent.UnpinEssential(essential.url)); true })
                            add(CustomAccessibilityAction(renameLabel) { renaming = true; true })
                            if (!essential.isGhost) {
                                add(CustomAccessibilityAction(resetLabel) { onIntent(SidebarIntent.ResetEssential(essential.url)); true })
                                add(CustomAccessibilityAction(unloadLabel) { onIntent(SidebarIntent.UnloadEssential(essential.url)); true })
                                add(CustomAccessibilityAction(closeLabel) { onIntent(SidebarIntent.CloseEssentialTab(essential.url)); true })
                            }
                            // Dropping on the Space's pinned block is the touch way.
                            if (canPinToSpace) add(CustomAccessibilityAction(pinToSpaceLabel) { onIntent(SidebarIntent.EssentialToSpace(essential.url)); true })
                            onMoveEarlier?.let { add(CustomAccessibilityAction(moveEarlierLabel) { it(); true }) }
                            onMoveLater?.let { add(CustomAccessibilityAction(moveLaterLabel) { it(); true }) }
                        }
                }
                .testTag(SidebarTestTag.ESSENTIAL),
        contentAlignment = Alignment.Center,
    ) {
        val iconModifier =
            Modifier.size(24.dp).alpha(
                fadedAlpha(
                    when {
                        essential.isGhost -> GHOST_ALPHA
                        essential.sleeping -> SLEEP_ALPHA
                        else -> 1f
                    },
                ),
            ).greyWhenSleeping(essential.sleeping)
        val icon = essential.icon
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = iconModifier.clip(RoundedCornerShape(5.dp)),
            )
        } else {
            Box(modifier = iconModifier) { LocalFaviconLoader.current(essential.url, 24.dp) }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!essential.isGhost) {
                DropdownMenuItem(
                    text = { Text(resetLabel) },
                    onClick = {
                        menuOpen = false
                        onIntent(SidebarIntent.ResetEssential(essential.url))
                    },
                )
                if (!essential.isSelected && !essential.sleeping) {
                    DropdownMenuItem(
                        text = { Text(unloadLabel) },
                        onClick = {
                            menuOpen = false
                            onIntent(SidebarIntent.UnloadEssential(essential.url))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sideraven_unload_others)) },
                    onClick = {
                        menuOpen = false
                        onIntent(SidebarIntent.UnloadOthers(UnloadOthersFrom.Essential(essential.url)))
                    },
                )
                // The touch way to close an Essential Tab (the Sidebar Address Bar adds a visible one).
                DropdownMenuItem(
                    text = { Text(closeLabel) },
                    onClick = {
                        menuOpen = false
                        onIntent(SidebarIntent.CloseEssentialTab(essential.url))
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
            DropdownMenuItem(
                text = { Text(unpinLabel) },
                onClick = {
                    menuOpen = false
                    onIntent(SidebarIntent.UnpinEssential(essential.url))
                },
            )
        }
        if (renaming) {
            RenameDialog(
                currentName = essential.title,
                onDismiss = { renaming = false },
                onRename = { name ->
                    renaming = false
                    onIntent(SidebarIntent.RenameEssential(essential.url, name))
                },
            )
        }
    }
}

