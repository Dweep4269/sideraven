/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import android.content.res.Configuration
import android.view.Gravity
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Toast
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.navigation.fragment.NavHostFragment
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.state.action.LastAccessAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.lib.state.ext.observeAsComposableState
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.maxActiveTime
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.ext.tabClosedUndoMessage
import org.mozilla.fenix.utils.allowUndo
import pt.dweep.sideraven.theme.SideravenTheme
import org.mozilla.fenix.compose.Favicon
import pt.dweep.sideraven.ui.LocalFaviconLoader
import pt.dweep.sideraven.ui.SidebarContent

/**
 * Places the Sidebar to the left of Fenix's content. Called once from [HomeActivity] right
 * after its layout is inflated (a `SIDERAVEN-HOOK`).
 *
 * The nav host container (which holds each screen's toolbar and content) is moved into a frame
 * and inset from the left by the docked Sidebar width, so the toolbar spans only the Content
 * Area. The Sidebar draws over the content while its width animates; the inset changes once.
 *
 * While the Sidebar is shown, the frame takes the Sidebar's surface colour and the content sits
 * inside it as an inset, rounded panel (framed content); corners are painted by [CornerMaskView].
 */
/** Gutter between the frame and the content panel. */
private const val FRAME_GUTTER_DP = 6

/** Corner radius of the content panel. */
private const val FRAME_CORNER_DP = 12

/**
 * Everything the Sidebar's state is derived from besides the browser's tabs. One value, so a
 * change to any input recomputes the state (no input can be left out of the recompute keys).
 */
internal data class SidebarInputs(
    val isPrivate: Boolean,
    val tier: SidebarTier,
    val screen: SidebarScreen,
    val chosenMode: SidebarMode? = null,
    val isRevealed: Boolean = false,
    val swipeZoneEnabled: Boolean = true,
    val essentials: List<Essential> = emptyList(),
    val essentialLinks: Map<String, String> = emptyMap(),
    val essentialIcons: Map<String, Bitmap> = emptyMap(),
    val inactiveTabsEnabled: Boolean = false,
    val inactiveExpanded: Boolean = false,
    val maxActiveTime: Long = Long.MAX_VALUE,
    val flapEnabled: Boolean = true,
    val flapPosition: Float = 0.5f,
    val controlPlaces: ControlPlaces = ControlPlaces(),
    val arranging: Boolean = false,
    val arranged: ArrangedShares = ArrangedShares(),
    val spaces: SpacesData? = null,
    val addressBarLayout: AddressBarLayout = AddressBarLayout.Top,
    val sidebarWidth: SidebarWidth = SidebarWidth.Standard,
    val sidebarSide: SidebarSide = SidebarSide.Left,
    val sleepingTabIds: Set<String> = emptySet(),
    val pinnedLinks: Map<String, String> = emptyMap(),
    val pinnedIcons: Map<String, Bitmap> = emptyMap(),
    val desktopTabs: Map<String, List<pt.dweep.sideraven.sync.DesktopTab>> = emptyMap(),
    val desktopTabsExpanded: Boolean = false,
    val desktopTabIcons: Map<String, Bitmap> = emptyMap(),
    val look: SidebarLook = SidebarLook(),
    val animateModeChanges: Boolean = true,
    val syncProblem: Boolean = false,
    val zenSyncOn: Boolean = false,
)

/** The Sidebar's state, recomputed when the tabs, the selection or any [inputs] change. */
@Composable
internal fun rememberSidebarUiState(
    browserState: mozilla.components.browser.state.state.BrowserState,
    inputs: SidebarInputs,
): SidebarUiState =
    remember(browserState.tabs, browserState.selectedTabId, inputs) {
        with(inputs) {
            browserState.toSidebarUiState(
                isPrivate = isPrivate,
                tier = tier,
                screen = screen,
                chosenMode = chosenMode,
                isRevealed = isRevealed,
                swipeZoneEnabled = swipeZoneEnabled,
                essentials = essentials,
                essentialLinks = essentialLinks,
                essentialIcons = essentialIcons,
                inactiveTabsEnabled = inactiveTabsEnabled,
                inactiveExpanded = inactiveExpanded,
                maxActiveTime = maxActiveTime,
                flapEnabled = flapEnabled,
                flapPosition = flapPosition,
                controlPlaces = controlPlaces,
                arranging = arranging,
                arranged = arranged,
                spaces = spaces,
                addressBarLayout = addressBarLayout,
                sidebarWidth = sidebarWidth,
                sidebarSide = sidebarSide,
                sleepingTabIds = sleepingTabIds,
                pinnedLinks = pinnedLinks,
                pinnedIcons = pinnedIcons,
                desktopTabs = desktopTabs,
                desktopTabsExpanded = desktopTabsExpanded,
                desktopTabIcons = desktopTabIcons,
            ).copy(animateModeChanges = animateModeChanges, syncProblem = syncProblem, zenSyncOn = zenSyncOn, look = look)
        }
    }

object SidebarHost {
    fun install(
        activity: HomeActivity,
        contentContainer: View,
    ) {
        if (!SidebarGate.hostsSidebar(activity)) return
        SidebarFullScreen.follow(activity)
        val parent = contentContainer.parent as ViewGroup
        val index = parent.indexOfChild(contentContainer)
        val containerParams = contentContainer.layoutParams
        parent.removeViewAt(index)

        val frame = FrameLayout(activity).apply { layoutParams = containerParams }
        val contentParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val maskParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val density = activity.resources.displayMetrics.density
        val gutterPx = (FRAME_GUTTER_DP * density).toInt()
        val cornerMask = CornerMaskView(activity).apply { layoutParams = maskParams }
        var dockedWidthPx = 0
        var framed = false
        var side = SidebarSide.Left

        fun applyFrame() {
            val insets = frameInsets(dockedWidthPx, gutterPx, framed, side)
            if (contentParams.marginStart != insets.start || contentParams.topMargin != insets.top ||
                contentParams.marginEnd != insets.end || contentParams.bottomMargin != insets.bottom
            ) {
                listOf(contentParams, maskParams).forEach {
                    it.marginStart = insets.start
                    it.topMargin = insets.top
                    it.marginEnd = insets.end
                    it.bottomMargin = insets.bottom
                }
                contentContainer.layoutParams = contentParams
                cornerMask.layoutParams = maskParams
            }
            cornerMask.radius = if (framed) FRAME_CORNER_DP * density else 0f
            SidebarGate.frameLook.value = FrameLook(framed, cornerMask.frameColor, cornerMask.radius)
        }

        val sidebar =
            ComposeView(activity).apply {
                setContent {
                    SidebarRoot(
                        activity = activity,
                        snackbarParent = contentContainer,
                        onDockedWidthChanged = { widthPx ->
                            dockedWidthPx = widthPx
                            applyFrame()
                        },
                        onFrameChanged = { active, top, bottom ->
                            framed = active
                            frame.background = if (active) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)) else null
                            cornerMask.setFrameColors(top, bottom)
                            applyFrame()
                        },
                        // The revealed Auto-Hide Sidebar and its Scrim cover the whole content.
                        onOverlayActiveChanged = { active ->
                            val params = layoutParams
                            val width = if (active) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
                            if (params != null && params.width != width) {
                                params.width = width
                                layoutParams = params
                            }
                        },
                    )
                }
            }
        frame.addView(contentContainer, contentParams)
        // Above the page (painted over its surface), below the Sidebar.
        frame.addView(cornerMask)
        val sidebarParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        frame.addView(sidebar, sidebarParams)
        parent.addView(frame, index)
        // The Sidebar's side (Sideraven Settings): it docks there, and the content's frame leaves room.
        activity.lifecycleScope.launch {
            PreferencesSidebarModeStore.get(activity).sidebarSide.flow.collect { chosen ->
                side = chosen
                (sidebar.layoutParams as FrameLayout.LayoutParams).let { params ->
                    params.gravity = if (chosen == SidebarSide.Right) Gravity.END else Gravity.START
                    sidebar.layoutParams = params
                }
                applyFrame()
            }
        }
    }
}

@Composable
private fun SidebarRoot(
    activity: HomeActivity,
    snackbarParent: View,
    onDockedWidthChanged: (widthPx: Int) -> Unit,
    onOverlayActiveChanged: (Boolean) -> Unit,
    onFrameChanged: (active: Boolean, top: Int, bottom: Int) -> Unit,
) {
    val components = activity.components
    val modeStore = remember(activity) { PreferencesSidebarModeStore.get(activity) }
    val phoneSidebar by modeStore.phoneSidebar.collectAsState()
    val tier = SidebarTier.from(LocalConfiguration.current, phoneSidebar)
    val screen by rememberCurrentScreen(activity)
    // Observe raw state and derive here: the store helper keeps its first mapping lambda, so
    // mapping inside it would never see new tier/screen/mode values.
    val isPrivate by components.appStore.observeAsComposableState { it.mode.isPrivate }
    // Only what the Sidebar shows: a loading page's progress, security and history leave this
    // unchanged, so they don't rebuild the Sidebar. The address bar follows the page alone.
    val browserState by components.core.store.observeAsComposableState { it.sidebarView() }
    val selectedPage by components.core.store.observeAsComposableState { it.selectedPageView() }
    val chosenMode by modeStore.chosenMode.collectAsState()
    val revealed by SidebarGate.revealed.collectAsState()
    val swipeZoneEnabled by modeStore.swipeZone(tier).collectAsState()
    val sidebarWidthChoice by modeStore.sidebarWidth.collectAsState()
    val sidebarSideChoice by modeStore.sidebarSide.collectAsState()
    val sleepingTabIds by SidebarGate.sleepingTabs.collectAsState()
    var animateModeChanges by remember { mutableStateOf(true) }
    val showInactiveSection by modeStore.showInactiveSection.collectAsState()
    val inactiveExpanded by modeStore.inactiveExpanded.collectAsState()
    val flapEnabled by modeStore.flapEnabled.collectAsState()
    val flapPosition by modeStore.flapPosition.collectAsState()
    val controlPlaces = modeStore.collectControlPlaces()
    // Arrange Mode: on until Done, leaving Collapsed Mode, or the app going to the background.
    var arranging by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // The handler outlives rotations (the activity handles them), so it reads the orientation of the moment.
    val currentLandscape by rememberUpdatedState(landscape)
    val arranged = modeStore.collectArrangedShares(landscape)
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) arranging = false }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val addressBarLayout by modeStore.addressBarLayout.collectAsState()
    val essentials = remember(activity) { Essentials(PreferencesEssentialsRepository.get(activity), Essentials.processLinks) }
    val essentialList by essentials.essentials.collectAsState()
    val essentialLinks by essentials.linkedTabs.collectAsState()
    val essentialIcons = rememberItemIcons(activity, essentialList.map { it.url })
    val spaces = remember(activity) { Spaces.get(activity) }
    val spacesData by spaces.flow.collectAsState()
    // Zen Sync: once at start, a few seconds after each change, and alongside Fenix's sync.
    val zenRunner = remember(activity) { pt.dweep.sideraven.sync.ZenSyncRunners.get(activity) }
    val zenStatus by zenRunner.status.collectAsState()
    val zenSettings by remember(activity) { pt.dweep.sideraven.sync.PreferencesZenSyncSettingsStore.get(activity) }.settings.collectAsState()
    // In front: sync now and every few minutes (Sync automatically); the start is covered too.
    DisposableEffect(zenRunner) {
        val lifecycle = activity.lifecycle
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> zenRunner.inForeground(true)
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> zenRunner.inForeground(false)
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            zenRunner.inForeground(false)
        }
    }
    LaunchedEffect(zenRunner) {
        kotlinx.coroutines.flow.combine(spaces.flow, essentials.essentials) { s, e -> pt.dweep.sideraven.sync.SyncedView.of(s, e) }
            .distinctUntilChanged()
            .drop(1)
            .collect { zenRunner.localChanged() }
    }
    DisposableEffect(zenRunner) {
        val observer =
            object : mozilla.components.service.fxa.sync.SyncStatusObserver {
                override fun onStarted() = Unit

                override fun onIdle() {
                    zenRunner.autoSync()
                }

                override fun onError(error: Exception?) = Unit
            }
        components.backgroundServices.accountManager.registerForSyncEvents(observer, activity, autoPause = true)
        onDispose { components.backgroundServices.accountManager.unregisterForSyncEvents(observer) }
    }
    val pinnedLinks by spaces.pinnedLinks.collectAsState()
    val desktopView by remember(activity) { pt.dweep.sideraven.sync.PreferencesDesktopViewStore.get(activity) }.view.collectAsState()
    val desktopTabsExpanded by modeStore.desktopTabsExpanded.collectAsState()
    val desktopTabIcons = rememberItemIcons(activity, desktopView.tabs.values.flatten().map { it.url })
    val tabRows by modeStore.tabRows.collectAsState()
    val closeButtons by modeStore.closeButtons.collectAsState()
    val swipeToClose by modeStore.swipeToClose.collectAsState()
    val addressSwipeNewTab by modeStore.addressSwipeNewTab.collectAsState()
    val tabListDirection by modeStore.tabListDirection.collectAsState()
    val addressSwipeTabs by modeStore.addressSwipeTabs.collectAsState()
    val essentialsPerRow by modeStore.essentialsPerRow.collectAsState()
    val haptics by modeStore.haptics.collectAsState()
    val animations by modeStore.animations.collectAsState()
    val roundedFrame by modeStore.roundedFrame.collectAsState()
    val pinnedIcons = rememberItemIcons(activity, spacesData?.pinned.orEmpty().map { it.url })
    // An open Essential or Pinned Tab's real icon is remembered for when its tab is closed or asleep.
    LaunchedEffect(browserState.tabs, essentialLinks, pinnedLinks) {
        val byId = browserState.tabs.associateBy { it.id }
        val pinUrls = spaces.data.pinned.associate { it.id to it.url }
        // Only while the tab is on the pinned page: browsed elsewhere, its icon is another site's.
        fun iconAt(
            url: String,
            tabId: String,
        ) = byId[tabId]?.content?.takeIf { it.url == url }?.icon
        val seen =
            essentialLinks.mapNotNull { (url, tabId) -> iconAt(url, tabId)?.let { url to it } } +
                pinnedLinks.mapNotNull { (pinId, tabId) -> pinUrls[pinId]?.let { url -> iconAt(url, tabId)?.let { url to it } } }
        if (seen.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val store = IconStore.get(activity)
                seen.forEach { (url, icon) -> store.put(url, icon) }
            }
        }
    }
    // After a restart, re-link restored tabs to Pinned Tabs by exact URL (once per process).
    LaunchedEffect(browserState.restoreComplete) {
        if (browserState.restoreComplete && !Spaces.restoreRelinked) {
            Spaces.restoreRelinked = true
            spaces.relinkPinned(components.core.store.state.tabs, excluded = essentials.linkedTabs.value.values.toSet())
        }
    }
    // After a restart, re-link restored tabs whose URL exactly matches an Essential (once per process).
    LaunchedEffect(browserState.restoreComplete) {
        if (browserState.restoreComplete && !Essentials.restoreRelinked) {
            Essentials.restoreRelinked = true
            essentials.relinkRestoredTabs(components.core.store.state.tabs)
        }
    }
    val listState =
        rememberSidebarUiState(
            browserState,
            SidebarInputs(
                isPrivate = isPrivate,
                tier = tier,
                screen = screen,
                chosenMode = chosenMode,
                isRevealed = revealed,
                swipeZoneEnabled = swipeZoneEnabled,
                essentials = essentialList,
                essentialLinks = essentialLinks,
                essentialIcons = essentialIcons.toMap(),
                inactiveTabsEnabled = components.settings.inactiveTabsAreEnabled && showInactiveSection,
                inactiveExpanded = inactiveExpanded,
                maxActiveTime = maxActiveTime,
                flapEnabled = flapEnabled,
                flapPosition = flapPosition,
                controlPlaces = controlPlaces,
                arranging = arranging,
                arranged = arranged,
                spaces = spaces.data,
                addressBarLayout = addressBarLayout,
                sidebarWidth = sidebarWidthChoice,
                sidebarSide = sidebarSideChoice,
                sleepingTabIds = sleepingTabIds,
                pinnedLinks = pinnedLinks,
                pinnedIcons = pinnedIcons.toMap(),
                desktopTabs = if (zenSettings.enabled && zenSettings.syncSpaces) desktopView.tabs else emptyMap(),
                desktopTabsExpanded = desktopTabsExpanded,
                desktopTabIcons = desktopTabIcons.toMap(),
                look =
                    SidebarLook(
                        tabRows = tabRows,
                        closeButtons = closeButtons,
                        swipeToClose = swipeToClose,
                        tabListDirection = tabListDirection,
                        addressSwipeNewTab = addressSwipeNewTab,
                        addressSwipeTabs = addressSwipeTabs,
                        essentialsPerRow = essentialsPerRow,
                        haptics = haptics,
                        animations = animations,
                        roundedFrame = roundedFrame,
                    ),
                animateModeChanges = animateModeChanges,
                syncProblem = zenStatus.problem != null,
                zenSyncOn = zenSettings.enabled,
            ),
        )
    val live = remember(listState, selectedPage, screen) { listState.withLiveAddress(selectedPage, screen) }
    // A browsing mode switch: the Sidebar changes with the page when Fenix restarts the screen.
    val hold = remember(activity) { ModeSwitchHold<SidebarUiState>(activity.browsingModeManager.mode.isPrivate) }
    val switching = isPrivate != hold.drawnPrivate
    // The restarted Sidebar starts where this one is when the restart comes (a switch may go home first).
    LaunchedEffect(switching, screen) { if (switching) ModeSwitchHold.rememberScreenAtSwitch(screen) }
    LaunchedEffect(switching) {
        if (switching) {
            kotlinx.coroutines.delay(ModeSwitchHold.MAX_HOLD_MS)
            hold.giveUp()
        }
    }
    val state = hold.shown(live, isPrivate)
    // Fenix's toolbar hides while the Sidebar layout is in effect (hooks in the browser and home screens).
    LaunchedEffect(state.isVisible, state.layout) {
        SidebarGate.toolbarHidden.value = state.isVisible && state.layout == AddressBarLayout.Sidebar
    }
    DisposableEffect(Unit) {
        onDispose { SidebarGate.toolbarHidden.value = false }
    }
    // Leaving the overlay-capable modes or the browser/home screens closes a revealed Sidebar.
    LaunchedEffect(state.mode, state.isVisible) {
        if (!state.isVisible || state.mode == SidebarMode.Persistent) SidebarGate.revealed.value = false
    }
    val density = LocalDensity.current
    // The Search Pop-up, when open (from the Sidebar Address Bar or New Tab).
    var searchRequest by remember { mutableStateOf<pt.dweep.sideraven.ui.SearchRequest?>(null) }
    // A new tab handed over for the Search Pop-up (SearchHandoff), possibly from before a mode switch's restart.
    LaunchedEffect(activity, isPrivate) {
        SearchHandoff.pending.collect { wanted ->
            if (wanted == isPrivate && SearchHandoff.take(isPrivate)) searchRequest = searchRequestFor(activity, newTab = true, fromHome = true)
        }
    }

    val currentScreen by rememberUpdatedState(screen)
    val handler =
        remember(activity) {
            SidebarIntentHandler(
                selectTab = { tabId -> components.useCases.tabsUseCases.selectTab(tabId) },
                openNewTab = { private -> openNewTab(activity, private) },
                openSpaceHome = { openNewTab(activity, private = false, focus = false) },
                closeTab = { tabId -> components.useCases.tabsUseCases.removeTab(tabId) },
                onTabClosed = { private, wasLastTab ->
                    activity.lifecycleScope.allowUndo(
                        view = snackbarParent,
                        settings = components.settings,
                        message = activity.tabClosedUndoMessage(private),
                        undoActionTitle = activity.getString(R.string.snackbar_deleted_undo),
                        onCancel = { components.useCases.tabsUseCases.undo.invoke() },
                        operation = {},
                    )
                    if (wasLastTab && navController(activity)?.currentDestination?.id == R.id.browserFragment) {
                        navController(activity)?.navigate(NavGraphDirections.actionGlobalHome())
                    }
                },
                setChosenMode = modeStore.chosenMode::set,
                newTabBehaviour = { modeStore.newTabBehaviour.value },
                setAddressBarLayout = modeStore.addressBarLayout::set,
                setRevealed = { SidebarGate.revealed.value = it },
                duplicateTab = { tabId ->
                    components.core.store.state.findTab(tabId)?.let { tab ->
                        components.useCases.tabsUseCases.duplicateTab.invoke(tab, selectNewTab = true)
                    }
                },
                pinTab = { tabId -> components.core.store.state.findTab(tabId)?.let { essentials.pin(it) } ?: PinResult.PrivateRefused },
                onPinRefused = { result ->
                    val message =
                        when (result) {
                            PinResult.Full -> activity.getString(R.string.sideraven_essentials_full, ESSENTIALS_CAP)
                            PinResult.PrivateRefused -> activity.getString(R.string.sideraven_essentials_private)
                            PinResult.AlreadyPinned -> activity.getString(R.string.sideraven_essentials_already)
                            PinResult.Pinned -> null
                        }
                    message?.let { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
                },
                openEssential = { url, private ->
                    val tabId = components.useCases.tabsUseCases.addTab(url = url, selectTab = true, private = private)
                    // Private tabs never become Essential Tabs; they stay in the private Tab List.
                    if (!private) essentials.link(url, tabId)
                },
                unpinEssential = essentials::unpin,
                loadUrlInTab = { url, tabId -> components.useCases.sessionUseCases.loadUrl(url, tabId) },
                switchBrowsingMode = { toPrivate -> switchBrowsingMode(activity, toPrivate) },
                moveTab = { tabId, targetTabId, placeAfter ->
                    components.useCases.tabsUseCases.moveTabs(listOf(tabId), targetTabId, placeAfter)
                },
                setAnimateModeChanges = { animateModeChanges = it },
                setInactiveExpanded = modeStore.inactiveExpanded::set,
                setDesktopTabsExpanded = modeStore.desktopTabsExpanded::set,
                setFlapPosition = modeStore.flapPosition::set,
                setArranging = { arranging = it },
                setArrangedShare = { section, share -> modeStore.arrange(section, currentLandscape, share) },
                openUrl = { url -> components.useCases.tabsUseCases.addTab(url = url, selectTab = true) },
                // Fenix's home screen doesn't follow the selected tab: leave it for the browser.
                showBrowser = {
                    navController(activity)?.takeIf { it.currentDestination?.id == R.id.homeFragment }?.let {
                        it.openToBrowser()
                    }
                },
                closeTabs = { ids -> components.useCases.tabsUseCases.removeTabs(ids) },
                moveEssential = essentials::move,
                reactivateTab = { tabId -> components.core.store.dispatch(LastAccessAction.UpdateLastAccessAction(tabId)) },
                navigation =
                    AddressNavigation(
                        goBack = { components.useCases.sessionUseCases.goBack() },
                        goForward = { components.useCases.sessionUseCases.goForward() },
                        reload = { components.useCases.sessionUseCases.reload() },
                        stop = { components.useCases.sessionUseCases.stopLoading() },
                        showHistory = { navController(activity)?.navigate(NavGraphDirections.actionGlobalTabHistoryDialogFragment(activeSessionId = null)) },
                        openMenu = { onHome -> SidebarPageActions.openMenu(activity, onHome) },
                        openSitePanel = { SidebarPageActions.openSitePanel(activity) },
                        openSyncedTabs = { SidebarPageActions.openSyncedTabs(activity) },
                        openBlank = { private ->
                            components.useCases.tabsUseCases.addTab(url = "about:blank", selectTab = true, private = private)
                            navController(activity)?.let { if (it.currentDestination?.id != R.id.browserFragment) it.navigate(R.id.browserFragment) }
                        },
                        openSearch = { newTab -> searchRequest = searchRequestFor(activity, newTab, fromHome = currentScreen == SidebarScreen.Home) },
                    ),
                switchSpace = { spaceId ->
                    spaces.switchTo(spaceId)
                    // Essential Tabs are global: switching never lands on one.
                    spaces.data.switchTarget(spaceId, components.core.store.state.tabs, essentials.linkedTabs.value.values.toSet())
                },
                createSpace = { name, icon, accent, local -> spaces.create(name, icon, accent, local) },
                editSpace = spaces::edit,
                pinToSpace = { tabId -> components.core.store.state.findTab(tabId)?.let { spaces.pinToSpace(it) } ?: PinResult.PrivateRefused },
                onSpacePinRefused = { result ->
                    val message =
                        when (result) {
                            PinResult.Full -> activity.getString(R.string.sideraven_space_pins_full, PINNED_TABS_CAP)
                            PinResult.PrivateRefused -> activity.getString(R.string.sideraven_space_pin_private)
                            PinResult.AlreadyPinned -> activity.getString(R.string.sideraven_space_pin_already)
                            PinResult.Pinned -> null
                        }
                    message?.let { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
                },
                openPinnedTab = { pinId, url ->
                    val tabId = components.useCases.tabsUseCases.addTab(url = url, selectTab = true, private = false)
                    spaces.linkPin(pinId, tabId)
                },
                unpinPinnedTab = spaces::unpinFromSpace,
                renameEssential = essentials::rename,
                renamePinnedTab = spaces::renamePin,
                folders = FolderActions.of(spaces),
                movePinnedTab = spaces::movePin,
                pinnedToEssentials = { pinId ->
                    val pin = spaces.data.pinned.firstOrNull { it.id == pinId }
                    if (pin == null) {
                        PinResult.PrivateRefused
                    } else {
                        val tabId = spaces.pinnedLinks.value[pinId]?.takeIf { components.core.store.state.findTab(it) != null }
                        essentials.pinUrl(pin.url, pin.title, tabId, pin.customName).also { if (it == PinResult.Pinned) spaces.unpinFromSpace(pinId) }
                    }
                },
                essentialToSpace = { url ->
                    val essential = essentials.essentials.value.firstOrNull { it.url == url }
                    if (essential == null) {
                        PinResult.PrivateRefused
                    } else {
                        val tabId = essentials.linkedTabId(url, components.core.store.state.tabs)
                        spaces.pinUrl(url, essential.title, tabId, essential.customName).also { if (it == PinResult.Pinned) essentials.unpin(url) }
                    }
                },
                sleepTabs = { ids -> sleepTabs(activity, ids) },
                unloadSpace = { spaceId ->
                    val links = spaces.pinnedLinks.value
                    val selected = components.core.store.state.selectedTabId
                    sleepTabs(activity, spaces.data.pinnedIn(spaceId).mapNotNull { links[it.id] }.filter { it != selected })
                },
                moveTabToSpace = { tabId, spaceId ->
                    val wasSelected = components.core.store.state.selectedTabId == tabId
                    spaces.moveTab(tabId, spaceId)
                    if (wasSelected) {
                        val now = spaces.data
                        now.switchTarget(now.currentSpaceId, components.core.store.state.tabs, essentials.linkedTabs.value.values.toSet())
                    } else {
                        null
                    }
                },
                moveSpace = spaces::move,
                setSpaceLocal = { id, local -> pt.dweep.sideraven.sync.ZenSync.get(activity).markLocal(id, local) },
                deleteSpace = { spaceId -> deleteSpace(activity, snackbarParent, spaces, essentials, spaceId) },
                toggleMute = { tabId ->
                    components.core.store.state.findTab(tabId)?.mediaSessionState?.let { media ->
                        media.controller.muteAudio(!media.muted)
                    }
                },
            )
        }

    // Keyboard shortcuts reach the Sidebar through SidebarKeyboard (hooked in HomeActivity).
    SidebarKeyboard.visibleEssentials = if (state.isVisible) state.essentials.size else -1
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(handler) {
        SidebarKeyboard.shortcuts.collect { intent -> handler.handle(intent, currentState) }
    }
    // This window's Main Menu: it learns what the Sidebar offers, and its Sideraven entries come back here.
    val menuLink = remember(activity) { SidebarMenu.of(activity) }
    val menuInfo =
        if (state.isVisible) {
            SidebarMenuInfo(
                nextMode = state.nextMode,
                layoutMovable = state.layoutMovable,
                chosenLayout = state.chosenLayout,
                mode = state.mode,
                arrangeable = state.essentials.isNotEmpty() || state.pinned.isNotEmpty() || state.folders.isNotEmpty(),
            )
        } else {
            null
        }
    // Leaving Collapsed Mode ends Arrange Mode, so it doesn't come back on its own later.
    LaunchedEffect(state.mode) { if (state.mode != SidebarMode.Collapsed) arranging = false }
    SideEffect { menuLink?.publish(menuInfo) }
    DisposableEffect(menuLink) { onDispose { menuLink?.publish(null) } }
    LaunchedEffect(handler, menuLink) {
        menuLink?.requests?.collect { intent -> handler.handle(intent, currentState) }
    }

    val chosenAccent by modeStore.accent.collectAsState()
    val spaceThemes by modeStore.spaceThemes.collectAsState()
    val pureBlack by modeStore.pureBlack.collectAsState()
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    SideravenTheme(isPrivate = state.isPrivate, spaceAccent = pt.dweep.sideraven.theme.effectiveAccent(state.currentSpace?.accent, chosenAccent), pureBlack = pureBlack) {
        // Space themes: the current Space's Zen gradient, or its accent, tints the frame. Not in Private
        // Mode (Fenix's private colours), nor with pure black, which keeps the frame black.
        val surface = MaterialTheme.colorScheme.surface
        val accent = MaterialTheme.colorScheme.primary
        val currentSpace = spacesData?.current
        val gradient =
            remember(currentSpace, zenSettings.shareColours) {
                currentSpace?.let { pt.dweep.sideraven.sync.ZenColours.gradientOf(it, zenSettings.shareColours) }.orEmpty()
                    .mapNotNull { hex -> runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }
            }
        val tint =
            if (spaceThemes && !state.isPrivate && !(pureBlack && dark) && currentSpace != null) {
                pt.dweep.sideraven.theme.SpaceThemes.tint(surface, accent, gradient, dark)
            } else {
                null
            }
        // Framed content: the frame shares the Sidebar's colours while the Sidebar is shown
        // (unless Rounded frame is off: then content sits right against the Sidebar).
        val frameTop = (tint?.top ?: surface).toArgb()
        val frameBottom = (tint?.bottom ?: surface).toArgb()
        LaunchedEffect(state.framesContent, frameTop, frameBottom) { onFrameChanged(state.framesContent, frameTop, frameBottom) }
        LaunchedEffect(state.isVisible, frameTop, frameBottom) {
            activity.themeManager.setFrameColors(activity, (frameTop to frameBottom).takeIf { state.isVisible })
        }
        // Fenix's toolbar takes the Sidebar's colour while the Sidebar shows (the top of a Space theme's gradient).
        LaunchedEffect(state.isVisible, frameTop) { SidebarGate.sidebarSurface.value = frameTop.takeIf { state.isVisible } }
        CompositionLocalProvider(
            pt.dweep.sideraven.theme.LocalFrameTint provides tint,
            LocalFaviconLoader provides { url, size -> Favicon(url = url, size = size, isPrivate = state.isPrivate) },
        ) {
        // Fenix's toolbar address, tapped, opens the pop-up over the toolbar.
        // An opened tab is awake again.
        val selectedForWake = components.core.store.observeAsComposableState { it.selectedTabId }.value
        LaunchedEffect(selectedForWake) { selectedForWake?.let { SidebarGate.sleepingTabs.value -= it } }
        // Fenix's New tab opens the way New Tab Behaviour says, as the Sidebar's + does.
        val currentState by rememberUpdatedState(state)
        LaunchedEffect(activity) {
            ToolbarSearch.requests.collect { request ->
                when (request) {
                    is ToolbarSearch.Request.Search -> searchRequest = searchRequestFor(activity, newTab = false, request.placement, request.fromHome)
                    is ToolbarSearch.Request.NewTab -> {
                        activity.browsingModeManager.mode = BrowsingMode.fromBoolean(request.private)
                        handler.handle(SidebarIntent.NewTab, currentState.copy(isPrivate = request.private))
                    }
                }
            }
        }
        searchRequest?.let { request ->
            // Allowing suggestions in private browsing rebuilds the sources with the engine's.
            var suggestionsAllowed by remember(request) { mutableStateOf(0) }
            val providers = remember(request, suggestionsAllowed) { SidebarSearch.providers(activity, request) { searchRequest = null } }
            val options =
                remember(request) {
                    pt.dweep.sideraven.ui.SearchOptions(
                        engines = SidebarSearch.engines(activity),
                        clipboardLink = components.clipboardHandler.extractURL(),
                        extras = SearchExtrasFenix(activity),
                        privatePrompt = SidebarSearch.privatePrompt(activity, onAllowed = { suggestionsAllowed++ }, onLearnMore = { searchRequest = null }),
                        autocomplete = { text, engine -> SidebarSearch.autocomplete(activity, text, engine) },
                        onRemoveSuggestion = { item -> SidebarSearch.removeFromHistory(activity, item) },
                        closeOnKeyboardHide = modeStore.popupCloseOnKeyboardHide.value,
                        closeOnTapOutside = modeStore.popupCloseOnTapOutside.value,
                        defaultEngine = components.core.store.state.search.selectedOrDefaultSearchEngine,
                        providersFor = { engine -> SidebarSearch.providers(activity, request, engine) { searchRequest = null } },
                        onSubmitWith = { text, engine ->
                            searchRequest = null
                            SidebarSearch.submit(activity, request, text, engine)
                        },
                        onSearchSettings = {
                            searchRequest = null
                            sidebarNavController(activity)?.navigate(org.mozilla.fenix.NavGraphDirections.actionGlobalSearchEngineFragment())
                        },
                    )
                }
            // The pop-up lives outside the Sidebar's content: give it the Animations setting too.
            CompositionLocalProvider(pt.dweep.sideraven.ui.LocalSidebarMotion provides pt.dweep.sideraven.ui.SidebarMotion(animations)) {
            pt.dweep.sideraven.ui.SearchPopup(
                request = request,
                providers = providers,
                onSubmit = { text ->
                    searchRequest = null
                    SidebarSearch.submit(activity, request, text)
                },
                onSuggestion = { item -> item.onSuggestionClicked?.invoke() },
                onDismiss = { searchRequest = null },
                options = options,
                onOpenHome =
                    if (request.newTab) {
                        {
                            searchRequest = null
                            openNewTab(activity, activity.browsingModeManager.mode.isPrivate)
                        }
                    } else {
                        null
                    },
            )
            }
        }
        SidebarContent(
            state = state,
            onIntent = { handler.handle(it, state) },
            onDockedWidthChanged = { width -> onDockedWidthChanged(with(density) { width.roundToPx() }) },
            onOverlayActiveChanged = onOverlayActiveChanged,
        )
        }
    }
}

/**
 * Deletes a Space: closes its tabs, lands on the neighbouring Space, and offers Undo (which brings
 * back the Space and its tabs).
 */
private fun deleteSpace(
    activity: HomeActivity,
    snackbarParent: View,
    spaces: Spaces,
    essentials: Essentials,
    spaceId: String,
) {
    val components = activity.components
    val store = components.core.store
    val tabIds = store.state.tabs.filter { !it.content.private && spaces.data.spaceOf(it.id) == spaceId }.map { it.id }
    val snapshot = spaces.delete(spaceId) ?: return
    if (tabIds.isNotEmpty()) components.useCases.tabsUseCases.removeTabs(tabIds)
    if (snapshot.currentSpaceId == spaceId) {
        val now = spaces.data
        val target = now.switchTarget(now.currentSpaceId, store.state.tabs, essentials.linkedTabs.value.values.toSet())
        if (target != null) components.useCases.tabsUseCases.selectTab(target) else openNewTab(activity, private = false)
    }
    val name = snapshot.spaces.firstOrNull { it.id == spaceId }?.name.orEmpty()
    activity.lifecycleScope.allowUndo(
        view = snackbarParent,
        settings = components.settings,
        message = activity.getString(R.string.sideraven_space_deleted, name),
        undoActionTitle = activity.getString(R.string.snackbar_deleted_undo),
        onCancel = {
            spaces.restore(snapshot)
            if (tabIds.isNotEmpty()) components.useCases.tabsUseCases.undo.invoke()
        },
        operation = {},
    )
}

/**
 * The Browsing Mode Switch: switch Fenix's mode, then land on the newest tab of that mode, or a
 * new tab when there is none.
 */
private fun switchBrowsingMode(
    activity: HomeActivity,
    toPrivate: Boolean,
) {
    // Fenix restarts the screen for the new mode at once, as Iceraven does; the tab is chosen before then.
    activity.browsingModeManager.mode = BrowsingMode.fromBoolean(toPrivate)
    val newest = activity.components.core.store.state.tabs.lastOrNull { it.content.private == toPrivate }
    if (newest != null) {
        activity.components.useCases.tabsUseCases.selectTab(newest.id)
    } else {
        openNewTab(activity, toPrivate)
    }
}

/** Mirrors the Tab Strip's New Tab behaviour so both open tabs the same way. */
private fun openNewTab(
    activity: HomeActivity,
    private: Boolean,
    // Whether to start typing, as Fenix does for a new tab; not for an empty Space's home page.
    focus: Boolean = true,
) {
    val settings = activity.components.settings
    if (settings.enableHomepageAsNewTab) {
        activity.components.useCases.fenixBrowserUseCases.addNewHomepageTab(private = private)
    } else {
        // In the Sidebar layout Fenix's toolbar is hidden: the Search Pop-up opens instead of a keyboard for it.
        val focus =
            SearchHandoff.focusFenixAddressBar(
                wants = focus && !settings.enableHomepageTrendingRecentSearch,
                toolbarHidden = SidebarGate.toolbarHidden.value,
                private = private,
            )
        navController(activity)?.navigate(NavGraphDirections.actionGlobalHome(focusOnAddressBar = focus))
    }
}

private fun navController(activity: HomeActivity): NavController? = sidebarNavController(activity)

/** A Search Pop-up request: for the current tab, its search words or URL start the field; a new tab starts empty. */
private fun searchRequestFor(
    activity: HomeActivity,
    newTab: Boolean,
    placement: pt.dweep.sideraven.ui.SearchPlacement = pt.dweep.sideraven.ui.SearchPlacement.Centred,
    fromHome: Boolean = false,
): pt.dweep.sideraven.ui.SearchRequest {
    val store = activity.components.core.store
    val selected = store.state.selectedTabId?.let { store.state.findTab(it) }
    val opensNewTab =
        ToolbarSearch.opensNewTab(
            asked = newTab,
            fromHome = fromHome,
            homeIsATab = activity.components.settings.enableHomepageAsNewTab,
            selectedPrivate = selected?.content?.private,
            private = activity.browsingModeManager.mode.isPrivate,
        )
    val current = selected?.takeUnless { opensNewTab }
    val text = pt.dweep.sideraven.ui.searchStartingText(current?.content?.url, current?.content?.searchTerms)
    return pt.dweep.sideraven.ui.SearchRequest(opensNewTab, text, placement)
}

/** Fenix's main navigation controller, for Sideraven code that navigates. */
internal fun sidebarNavController(activity: HomeActivity): NavController? =
    (activity.supportFragmentManager.findFragmentById(R.id.container) as? NavHostFragment)?.navController

/** Unload: the tabs sleep (their engine sessions are suspended); they reload when opened. */
private fun sleepTabs(
    activity: HomeActivity,
    tabIds: List<String>,
) {
    tabIds.forEach { activity.components.core.store.dispatch(EngineAction.SuspendEngineSessionAction(it)) }
    SidebarGate.sleepingTabs.value += tabIds
}

/**
 * Icons for Sidebar items by address: the last real icon known on this device
 * (open tabs, earlier loads, desktop's from Zen Sync), else Fenix's loader, whose generated
 * letter icons are never kept as real ones.
 */
@Composable
private fun rememberItemIcons(
    activity: HomeActivity,
    urls: List<String>,
): SnapshotStateMap<String, Bitmap> {
    val icons = remember(activity) { mutableStateMapOf<String, Bitmap>() }
    val store = remember(activity) { IconStore.get(activity) }
    val version by store.version.collectAsState()
    // What the store knows, refreshed as it learns more.
    LaunchedEffect(urls, version) {
        val wanted = urls.distinct()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { wanted.associateWith { store.get(it) } }
            .forEach { (url, bitmap) -> if (bitmap != null && icons[url] !== bitmap) icons[url] = bitmap }
    }
    // Fenix's loader for addresses the store doesn't know (its own additions don't restart this).
    LaunchedEffect(urls) {
        val wanted = urls.distinct()
        val known = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { wanted.filter { store.get(it) != null }.toSet() }
        wanted.filter { it !in known && it !in icons }.forEach { url ->
            runCatching { activity.components.core.icons.loadIcon(IconRequest(url)).await() }
                .getOrNull()
                ?.let(SidebarIcons::realOrNull)
                ?.let { bitmap ->
                    icons[url] = bitmap
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { store.put(url, bitmap) }
                }
        }
    }
    return icons
}

/**
 * The screen Fenix's navigation is on. A screen restarted by a browsing mode switch starts on the
 * screen the switch left, so its Sidebar shows in the first frame instead of after navigation reports in.
 */
@Composable
private fun rememberCurrentScreen(activity: HomeActivity) =
    remember(activity) { mutableStateOf(ModeSwitchHold.takeScreenAtSwitch() ?: SidebarScreen.Other) }.also { screenState ->
        DisposableEffect(activity) {
            val controller = navController(activity)
            val listener =
                NavController.OnDestinationChangedListener { _, destination, _ ->
                    screenState.value =
                        SidebarScreen.after(
                            previous = screenState.value,
                            isHome = destination.id == R.id.homeFragment,
                            isBrowser = destination.id == R.id.browserFragment,
                            isDialog = destination.navigatorName == "dialog",
                        )
                }
            controller?.addOnDestinationChangedListener(listener)
            onDispose { controller?.removeOnDestinationChangedListener(listener) }
        }
    }
