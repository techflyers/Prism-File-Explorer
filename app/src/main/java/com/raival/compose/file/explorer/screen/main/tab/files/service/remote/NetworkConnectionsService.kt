package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object NetworkConnectionsService {
    private const val KEY_CONNECTIONS = "network_connections"
    private const val PREFS_NAME = "network_connections_prefs"

    fun getConnections(context: Context): List<NetworkConnectionModel> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_CONNECTIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NetworkConnectionModel>>() {}.type
            Gson().fromJson(str, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveConnection(context: Context, conn: NetworkConnectionModel) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getConnections(context).toMutableList()
        val index = current.indexOfFirst { it.id == conn.id }
        if (index >= 0) {
            current[index] = conn
        } else {
            current.add(conn)
        }
        val str = Gson().toJson(current)
        prefs.edit().putString(KEY_CONNECTIONS, str).apply()
    }

    fun deleteConnection(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getConnections(context).toMutableList()
        current.removeAll { it.id == id }
        val str = Gson().toJson(current)
        prefs.edit().putString(KEY_CONNECTIONS, str).apply()
    }
}
