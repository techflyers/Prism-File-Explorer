package com.raival.compose.file.explorer.screen.terminal

import android.content.Context
import android.os.Build
import com.raival.compose.file.explorer.App
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File

typealias SessionId  = String
typealias SessionPwd = String

data class SessionInfo(val id: SessionId, val pwd: SessionPwd, val session: TerminalSession)

object MkSession {

    /**
     * Creates a new [TerminalSession] running inside the Ubuntu proot container.
     *
     * The sandbox.sh bootstrap script is used for the interactive shell, or setup.sh
     * when [isExtraction] is true (first-time rootfs extraction).
     *
     * Storage mounts included: /sdcard, /storage, /mnt, /data, /dev, /proc, /sys,
     * plus all standard Android system partitions.
     */
    fun createSession(
        context: Context,
        sessionClient: TerminalSessionClient,
        sessionId: SessionId,
        isExtraction: Boolean = false,
    ): Pair<TerminalSession, SessionPwd> {
        val ctx = context
        val appInfo = ctx.applicationInfo
        val nativeLibDir = appInfo.nativeLibraryDir

        // ── Environment variables ──────────────────────────────────────────────────
        val env = mutableListOf(
            "PROOT=$nativeLibDir/libproot.so",
            "PROOT_LOADER=$nativeLibDir/libloader.so",
            "PROOT_TMP_DIR=${buildProotTmpDir(context, sessionId).absolutePath}",
            "COLORTERM=truecolor",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LOCAL=${localDir(context).absolutePath}",
            "PRIVATE_DIR=${ctx.filesDir.parentFile!!.absolutePath}",
            "LD_LIBRARY_PATH=${localLibDir(context).absolutePath}",
            "EXT_HOME=${sandboxHomeDir(context).absolutePath}",
            "HOME=/home",
            "PROMPT_DIRTRIM=2",
            "LINKER=${linkerPath()}",
            "NATIVE_LIB_DIR=$nativeLibDir",
            "SANDBOX=true",
            "TMP_DIR=${getTempDir(context).absolutePath}",
            "TMPDIR=${getTempDir(context).absolutePath}",
            "TZ=UTC",
            "DOTNET_GCHeapHardLimit=1C0000000",
            "SOURCE_DIR=${appInfo.sourceDir}",
            "DISPLAY=:0",
            // External storage path exposed inside the container as /mnt/prism
            "PUBLIC_HOME=${ctx.getExternalFilesDir(null)?.absolutePath ?: ""}",
        )

        // Include 32-bit proot loader if present
        val loader32 = "$nativeLibDir/libloader32.so"
        if (File(loader32).exists()) env.add("PROOT_LOADER_32=$loader32")

        // Carry over Android system env vars
        val systemEnvKeys = listOf(
            "ANDROID_ART_ROOT", "ANDROID_DATA", "ANDROID_I18N_ROOT",
            "ANDROID_ROOT", "ANDROID_RUNTIME_ROOT", "ANDROID_TZDATA_ROOT",
            "BOOTCLASSPATH", "DEX2OATBOOTCLASSPATH", "EXTERNAL_STORAGE",
        )
        systemEnvKeys.forEach { key ->
            System.getenv(key)?.let { env.add("$key=$it") }
        }

        val binPath = "${System.getenv("PATH") ?: ""}:${localBinDir(context).absolutePath}"
        env.add("PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin:$binPath")

        // Merge any caller-supplied env vars from a pending command
        pendingTerminalCommand?.env?.let { env.addAll(it) }

        // ── Working directory ──────────────────────────────────────────────────────
        val workingDir = pendingTerminalCommand?.workingDir ?: "/home"

        env.add("WKDIR=$workingDir")

        // ── Setup terminal asset files ─────────────────────────────────────────────
        setupTerminalFiles(context)

        // ── Shell + arguments ──────────────────────────────────────────────────────
        val sandboxSH = localBinDir(context).child("sandbox")
        val setupSH   = localBinDir(context).child("setup")

        val shell: String
        val args: Array<String>

        when {
            isExtraction -> {
                // Run setup.sh to extract the tar and configure the container
                shell = "/system/bin/sh"
                args  = arrayOf("-c", setupSH.absolutePath)
            }
            pendingTerminalCommand == null || (pendingTerminalCommand!!.sandbox && pendingTerminalCommand!!.exe.isEmpty()) -> {
                // Normal interactive sandbox session
                shell = "/system/bin/sh"
                args  = arrayOf("-c", sandboxSH.absolutePath)
            }
            pendingTerminalCommand!!.sandbox -> {
                // Sandbox + custom command
                shell = "/system/bin/sh"
                args  = mutableListOf(sandboxSH.absolutePath, pendingTerminalCommand!!.exe,
                    *pendingTerminalCommand!!.args).toTypedArray()
            }
            else -> {
                // Raw (no sandbox) custom command
                shell = pendingTerminalCommand!!.exe
                args  = pendingTerminalCommand!!.args
            }
        }

        pendingTerminalCommand = null

        val session = TerminalSession(
            /* mShellPath  = */ shell,
            /* mCwd        = */ localDir(context).absolutePath,
            /* mArgs       = */ args,
            /* mEnv        = */ env.toTypedArray(),
            /* mTranscriptRows = */ 5000,
            /* mClient     = */ sessionClient,
        )

        return session to workingDir
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private fun linkerPath(): String =
        if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

    private fun buildProotTmpDir(context: Context, sessionId: SessionId): File {
        val dir = File(getTempDir(context), "terminal/$sessionId")
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    /**
     * Writes shell scripts from assets into the local bin directory,
     * and creates the empty stat/vmstat stub files used by StatUpdater.
     */
    private fun setupTerminalFiles(context: Context) {
        if (!sandboxDir(context).exists() || !localBinDir(context).exists()) return

        // Create /proc/stat and /proc/vmstat stubs
        localDir(context).child("stat").createFileIfNot()
        localDir(context).child("vmstat").createFileIfNot()

        // Extract asset scripts
        val scripts = listOf("init", "sandbox", "setup", "utils")
        scripts.forEach { name ->
            val dest = localBinDir(context).child(name)
            if (!dest.exists()) {
                dest.createFileIfNot()
                dest.writeText(
                    context.assets.open("terminal/$name.sh").bufferedReader().use { it.readText() }
                )
                dest.setExecutable(true)
            }
        }
    }
}

/** Opens a folder path in the terminal. */
fun openFolderInTerminal(context: Context, folderPath: String) {
    val folderName = File(folderPath).name.ifBlank { "prism" }
    pendingTerminalCommand = TerminalCommand(
        sandbox = true,
        exe = "",
        args = arrayOf(),
        id = folderName,
        workingDir = folderPath,
    )
}
