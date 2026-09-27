package com.techflyers.compose.file.explorer.screen.main.tab.files.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

enum class FolderLockType { PIN, PATTERN }

data class FolderLockEntry(
    val path: String,
    val salt: String,
    val hash: String,
    val type: FolderLockType
)

object FolderLockStore {
    private const val PREFS = "folder_lock_prefs"
    private const val KEY = "locks"
    private val gson = Gson()
    private val sessionUnlocked = ConcurrentHashMap.newKeySet<String>()

    fun isLocked(path: String): Boolean {
        val entry = matchingLock(path) ?: return false
        return !sessionUnlocked.contains(entry.path)
    }

    fun hasLock(path: String): Boolean = matchingLock(path) != null

    fun matchingLock(path: String): FolderLockEntry? {
        val normalized = path.trimEnd('/')
        return load().firstOrNull { lock ->
            val lockNorm = lock.path.trimEnd('/')
            normalized == lockNorm || normalized.startsWith("$lockNorm/")
        }
    }

    fun lock(path: String, secret: String, type: FolderLockType) {
        val normalized = path.trimEnd('/')
        val salt = System.currentTimeMillis().toString()
        val entry = FolderLockEntry(normalized, salt, hash(secret, salt), type)
        val next = load().filter { it.path.trimEnd('/') != normalized } + entry
        save(next)
        sessionUnlocked.remove(normalized)
    }

    fun unlock(path: String, secret: String): Boolean {
        val entry = matchingLock(path) ?: return false
        if (entry.hash != hash(secret, entry.salt)) return false
        sessionUnlocked.add(entry.path)
        return true
    }

    fun verifyAnyLock(secret: String): Boolean {
        val locks = load()
        if (locks.isEmpty()) return true
        return locks.any { it.hash == hash(secret, it.salt) }
    }

    fun remove(path: String) {
        val normalized = path.trimEnd('/')
        save(load().filter { it.path.trimEnd('/') != normalized })
        sessionUnlocked.remove(normalized)
    }

    fun reset() {
        save(emptyList())
        sessionUnlocked.clear()
    }

    fun hasAnyLocks(): Boolean = load().isNotEmpty()

    private fun load(): List<FolderLockEntry> {
        val json = globalPrefs().getString(KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<FolderLockEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<FolderLockEntry>) {
        globalPrefs().edit().putString(KEY, gson.toJson(entries)).apply()
    }

    private fun globalPrefs() =
        com.techflyers.compose.file.explorer.App.globalClass
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(secret: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((secret + salt).toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
