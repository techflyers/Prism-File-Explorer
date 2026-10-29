package com.techflyers.compose.file.explorer.screen.preferences.misc

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.techflyers.compose.file.explorer.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

interface ReloadablePref {
    fun reload(preferences: Preferences)
}

val reloadablePrefs = java.util.Collections.synchronizedList(mutableListOf<ReloadablePref>())

val prefWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Volatile
var cachedPreferencesSnapshot: Preferences? = null

private val snapshotInitLock = Any()

fun getInitialPreferencesSnapshot(): Preferences {
    cachedPreferencesSnapshot?.let { return it }
    synchronized(snapshotInitLock) {
        cachedPreferencesSnapshot?.let { return it }
        val initial = try {
            runBlocking(Dispatchers.IO) {
                App.globalClass.prefDataStore.data
                    .catch { e ->
                        if (e is IOException) emit(emptyPreferences()) else throw e
                    }
                    .first()
            }
        } catch (_: Exception) {
            emptyPreferences()
        }
        cachedPreferencesSnapshot = initial
        prefWriteScope.launch {
            try {
                App.globalClass.prefDataStore.data
                    .catch { e ->
                        if (e is IOException) emit(emptyPreferences()) else throw e
                    }
                    .collect { prefs ->
                        cachedPreferencesSnapshot = prefs
                    }
            } catch (_: Exception) {
            }
        }
        return initial
    }
}

fun reloadAllPrefMutableStates(preferences: Preferences) {
    cachedPreferencesSnapshot = preferences
    synchronized(reloadablePrefs) {
        reloadablePrefs.forEach { it.reload(preferences) }
    }
}

inline fun <reified A> prefMutableState(
    keyName: String,
    defaultValue: A,
    getPreferencesKey: (keyName: String) -> Preferences.Key<A>,
): MutableState<A> {
    val key: Preferences.Key<A> = getPreferencesKey(keyName)
    val snapshotMutableState: MutableState<A> = mutableStateOf(
        getInitialPreferencesSnapshot()[key] ?: defaultValue
    )

    val reloadable = object : ReloadablePref {
        override fun reload(preferences: Preferences) {
            snapshotMutableState.value = preferences[key] ?: defaultValue
        }
    }
    reloadablePrefs.add(reloadable)

    return object : MutableState<A> {
        override var value: A
            get() = snapshotMutableState.value
            set(value) {
                val rollbackValue = snapshotMutableState.value
                snapshotMutableState.value = value
                prefWriteScope.launch {
                    try {
                        App.globalClass.prefDataStore.edit {
                            if (value != null) {
                                it[key] = value as A
                            } else {
                                it.remove(key)
                            }
                        }
                    } catch (_: Exception) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            snapshotMutableState.value = rollbackValue
                        }
                    }
                }
            }

        override fun component1() = value
        override fun component2(): (A) -> Unit = { value = it }
    }
}
