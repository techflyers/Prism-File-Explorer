package com.techflyers.compose.file.explorer.screen.main.tab.files.posix

import android.content.Context
import com.techflyers.compose.file.explorer.App.Companion.appContext

object PosixPrincipalLookup {
    private val WELL_KNOWN_PRINCIPALS = mapOf(
        0 to "root",
        1000 to "system",
        1001 to "radio",
        1002 to "bluetooth",
        1003 to "graphics",
        1004 to "input",
        1005 to "audio",
        1006 to "camera",
        1007 to "log",
        1008 to "compass",
        1009 to "mount",
        1010 to "wifi",
        1011 to "adb",
        1012 to "install",
        1013 to "media",
        1014 to "dhcp",
        1015 to "sdcard_rw",
        1016 to "vpn",
        1017 to "keystore",
        1018 to "usb",
        1019 to "drm",
        1020 to "mdnsr",
        1021 to "gps",
        1023 to "media_rw",
        1024 to "mtp",
        1026 to "drmrpc",
        1027 to "nfc",
        1028 to "sdcard_r",
        1029 to "clat",
        1030 to "loop_radio",
        1031 to "media_drm",
        1032 to "package_info",
        1033 to "sdcard_pics",
        1034 to "sdcard_av",
        1035 to "sdcard_all",
        1036 to "logd",
        1037 to "shared_relro",
        1038 to "dbus",
        1039 to "tlsdate",
        1040 to "media_ex",
        1041 to "audioserver",
        1042 to "metrics_coll",
        1043 to "metricsd",
        1044 to "webserv",
        1045 to "debuggerd",
        1046 to "mediacodec",
        1047 to "cameraserver",
        1048 to "firewall",
        1049 to "trunks",
        1050 to "nvram",
        1051 to "dns",
        1052 to "dns_tether",
        1053 to "webview_zygote",
        1054 to "vehicle_network",
        1055 to "media_audio",
        1056 to "media_video",
        1057 to "media_image",
        1058 to "tombstoned",
        1059 to "media_obb",
        1060 to "ese",
        1061 to "ota_update",
        2000 to "shell",
        2001 to "cache",
        2002 to "diag",
        9997 to "everybody",
        9998 to "misc",
        9999 to "nobody"
    )

    fun getUserName(uid: Int, context: Context = appContext): String {
        WELL_KNOWN_PRINCIPALS[uid]?.let { return it }
        if (uid >= 10000) {
            try {
                val pkg = context.packageManager.getNameForUid(uid)
                if (!pkg.isNullOrEmpty()) return pkg
            } catch (_: Exception) {}

            val userId = uid / 100000
            val appId = (uid % 100000) - 10000
            return "u${userId}_a$appId"
        }
        return uid.toString()
    }

    fun getGroupName(gid: Int, context: Context = appContext): String {
        WELL_KNOWN_PRINCIPALS[gid]?.let { return it }
        if (gid >= 10000) {
            try {
                val pkg = context.packageManager.getNameForUid(gid)
                if (!pkg.isNullOrEmpty()) return pkg
            } catch (_: Exception) {}

            val userId = gid / 100000
            val appId = (gid % 100000) - 10000
            return "u${userId}_a$appId"
        }
        return gid.toString()
    }

    fun formatUser(uid: Int, context: Context = appContext): String {
        val name = getUserName(uid, context)
        return if (name != uid.toString()) "$name ($uid)" else uid.toString()
    }

    fun formatGroup(gid: Int, context: Context = appContext): String {
        val name = getGroupName(gid, context)
        return if (name != gid.toString()) "$name ($gid)" else gid.toString()
    }
}
