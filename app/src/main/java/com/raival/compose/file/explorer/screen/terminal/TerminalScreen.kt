package com.raival.compose.file.explorer.screen.terminal

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.raival.compose.file.explorer.screen.terminal.virtualkeys.VirtualKeysConstants
import com.raival.compose.file.explorer.screen.terminal.virtualkeys.VirtualKeysInfo
import com.raival.compose.file.explorer.screen.terminal.virtualkeys.VirtualKeysListener
import com.raival.compose.file.explorer.screen.terminal.virtualkeys.VirtualKeysView
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(activity: TerminalActivity) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp * 0.84).dp
    val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
    val surface = MaterialTheme.colorScheme.surface.toArgb()

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    Box(modifier = Modifier.imePadding()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = { SessionDrawer(drawerWidth, activity) },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Terminal") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Sessions")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    // ── Terminal view ──────────────────────────────────────
                    AndroidView(
                        factory = { context ->
                            TerminalView(context, null).apply {
                                applyTerminalColors(surface, onSurface)
                                terminalView = WeakReference(this)
                                setTextSize(42) // ~14sp in px

                                val client = TerminalBackEnd()
                                val binder = activity.sessionBinder?.get()!!
                                val svc = binder.getService()
                                val targetSessionId = if (pendingTerminalCommand != null) {
                                    val cmdId = pendingTerminalCommand!!.id
                                    svc.currentSession.value = cmdId
                                    cmdId
                                } else {
                                    svc.currentSession.value
                                }
                                val existingSession = binder.getSession(targetSessionId)
                                val session = if (existingSession != null) {
                                    if (pendingTerminalCommand != null) {
                                        existingSession.write("cd '${pendingTerminalCommand!!.workingDir}'\n")
                                        pendingTerminalCommand = null
                                    }
                                    existingSession
                                } else {
                                    binder.createSession(targetSessionId, client, activity).session
                                }
                                session.updateTerminalSessionClient(client)
                                attachSession(session)
                                setTerminalViewClient(client)

                                post {
                                    keepScreenOn = true
                                    isFocusableInTouchMode = true
                                    requestFocus()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        update = { tv -> tv.applyTerminalColors(surface, onSurface) }
                    )

                    // ── Virtual keys / command bar pager ──────────────────
                    val pagerState = rememberPagerState(pageCount = { 2 })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(75.dp)
                    ) { page ->
                        when (page) {
                            0 -> {
                                terminalView.get()?.requestFocus()
                                AndroidView(
                                    factory = { ctx ->
                                        VirtualKeysView(ctx, null).apply {
                                            virtualKeysView = WeakReference(this)
                                            buttonTextColor = onSurface
                                            virtualKeysViewClient =
                                                terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }
                                            runCatching {
                                                reload(VirtualKeysInfo(
                                                    DEFAULT_TERMINAL_EXTRA_KEYS, "",
                                                    VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                                ))
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(75.dp)
                                )
                            }
                            1 -> {
                                var text by rememberSaveable { mutableStateOf("") }
                                val focusRequester = remember { FocusRequester() }
                                TextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    maxLines = 1,
                                    placeholder = { Text("Command…") },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (text.isEmpty()) {
                                                terminalView.get()?.dispatchKeyEvent(
                                                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                                                )
                                                terminalView.get()?.dispatchKeyEvent(
                                                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                                                )
                                            } else {
                                                terminalView.get()?.currentSession?.write(text)
                                                text = ""
                                            }
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(75.dp)
                                        .focusRequester(focusRequester)
                                )
                                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Session drawer ────────────────────────────────────────────────────────────

@Composable
private fun SessionDrawer(drawerWidth: Dp, activity: TerminalActivity) {
    ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sessions", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = {
                    terminalView.get()?.let {
                        val client = TerminalBackEnd()
                        val existingIds = activity.sessionBinder?.get()?.getService()?.sessionList ?: listOf()
                        val newId = generateUniqueId(existingIds)
                        activity.sessionBinder?.get()?.createSession(newId, client, activity)
                        activity.changeSession(newId)
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "New session")
                }
            }

            val service = activity.sessionBinder?.get()?.getService()
            service?.sessionList?.let { list ->
                LazyColumn {
                    items(list) { sessionId ->
                        val isSelected = sessionId == service.currentSession.value
                        NavigationDrawerItem(
                            label = { Text(sessionId) },
                            selected = isSelected,
                            onClick = { activity.changeSession(sessionId) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            badge = {
                                IconButton(
                                    onClick = {
                                        if (isSelected) {
                                            val idx = service.sessionList.indexOf(sessionId)
                                            val neighbor = service.sessionList.getOrNull(idx - 1)
                                                ?: service.sessionList.getOrNull(idx + 1)
                                            neighbor?.let { activity.changeSession(it) }
                                        }
                                        activity.sessionBinder?.get()?.terminateSession(sessionId)
                                        if (service.sessionList.isEmpty()) {
                                            activity.finish()
                                            service.actionExit()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete, contentDescription = "Close",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Session switching ─────────────────────────────────────────────────────────

fun TerminalActivity.changeSession(sessionId: String) {
    val tv = terminalView.get() ?: return
    val binder = sessionBinder?.get() ?: return
    val client = TerminalBackEnd()
    val existingSession = binder.getSession(sessionId)
    val session = if (existingSession != null) {
        if (pendingTerminalCommand != null) {
            existingSession.write("cd '${pendingTerminalCommand!!.workingDir}'\n")
            pendingTerminalCommand = null
        }
        existingSession
    } else {
        binder.createSession(sessionId, client, this).session
    }
    session.updateTerminalSessionClient(client)
    tv.attachSession(session)
    tv.setTerminalViewClient(client)
    tv.post { tv.keepScreenOn = true; tv.isFocusableInTouchMode = true; tv.requestFocus() }
    virtualKeysView.get()?.apply { virtualKeysViewClient = VirtualKeysListener(tv.mTermSession) }
    binder.getService().currentSession.value = sessionId
}

// ── Color helpers ─────────────────────────────────────────────────────────────

private fun TerminalView.applyTerminalColors(surfaceColor: Int, onSurfaceColor: Int) {
    onScreenUpdated()
    mEmulator?.mColors?.reset()
    TerminalColors.COLOR_SCHEME.updateWith(java.util.Properties())
    mEmulator?.mColors?.mCurrentColors?.apply {
        set(TextStyle.COLOR_INDEX_FOREGROUND, onSurfaceColor)
        set(TextStyle.COLOR_INDEX_BACKGROUND, surfaceColor)
        set(TextStyle.COLOR_INDEX_CURSOR, onSurfaceColor)
    }
    invalidate()
}

private fun generateUniqueId(existing: List<String>): String {
    var idx = 1
    while ("session #$idx" in existing) idx++
    return "session #$idx"
}
