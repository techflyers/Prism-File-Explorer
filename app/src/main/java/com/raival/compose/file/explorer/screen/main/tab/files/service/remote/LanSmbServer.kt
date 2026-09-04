package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import java.net.InetAddress

data class LanSmbServer(
    val host: String,
    val address: InetAddress,
    val discoveryMethod: String = "LAN"
) : Comparable<LanSmbServer> {
    val displayName: String
        get() = if (host.isNotBlank() && host != address.hostAddress) host else address.hostAddress

    override fun compareTo(other: LanSmbServer): Int =
        compareValuesBy(this, other, { it.address.hostAddress }, { it.host })
}
