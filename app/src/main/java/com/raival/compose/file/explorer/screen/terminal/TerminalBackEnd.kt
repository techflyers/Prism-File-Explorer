package com.raival.compose.file.explorer.screen.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.raival.compose.file.explorer.App
import com.raival.compose.file.explorer.screen.terminal.virtualkeys.SpecialButton
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

class TerminalBackEnd : TerminalViewClient, TerminalSessionClient {

    // ── TerminalSessionClient ────────────────────────────────────────────────

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.get()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {}

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = App.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = App.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        val emulator = terminalView.get()?.mEmulator ?: return
        if (clip.isNotBlank()) emulator.paste(clip)
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK

    override fun shouldSupportClipboardKeybindings(): Boolean = true

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: "Terminal", message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: "Terminal", message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "Terminal", message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "Terminal", message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "Terminal", message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "Terminal", message ?: ""); e?.printStackTrace()
    }
    override fun logStackTrace(tag: String?, e: Exception?) { e?.printStackTrace() }

    // ── TerminalViewClient ───────────────────────────────────────────────────

    override fun onScale(scale: Float): Float {
        val clamped = scale.coerceIn(11f, 45f)
        terminalView.get()?.setTextSize(clamped.toInt())
        return clamped
    }

    override fun onSingleTapUp(e: MotionEvent) { showSoftInput() }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            val activity = TerminalActivity.instance ?: return false
            val binder = activity.sessionBinder?.get() ?: return false
            val svc = binder.getService()
            binder.terminateSession(svc.currentSession.value)
            if (svc.sessionList.isEmpty()) {
                activity.finish()
            } else {
                activity.changeSession(svc.sessionList.first())
            }
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.CTRL, true) ?: false

    override fun readAltKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.ALT, true) ?: false

    override fun readShiftKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.SHIFT, true) ?: false

    override fun readFnKey(): Boolean =
        virtualKeysView.get()?.readSpecialButton(SpecialButton.FN, true) ?: false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() { setTerminalCursorBlinkingState(true) }

    private fun setTerminalCursorBlinkingState(start: Boolean) {
        val tv = terminalView.get() ?: return
        if (tv.mEmulator != null) tv.setTerminalCursorBlinkerState(start, true)
    }

    private fun showSoftInput() {
        val tv = terminalView.get() ?: return
        tv.requestFocus()
        val imm = App.appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT)
    }
}
