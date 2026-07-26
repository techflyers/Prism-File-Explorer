package com.raival.compose.file.explorer.screen.terminal

// Ubuntu 24.04 rootfs archives from Xed-Editor's release CDN
private const val ROOTFS_BASE =
    "https://github.com/Xed-Editor/Karbon-PackagesX/releases/download/ubuntu"

const val ROOTFS_ARM   = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-armhf.tar.gz"
const val ROOTFS_ARM64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-arm64.tar.gz"
const val ROOTFS_X64   = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-amd64.tar.gz"

const val TERMINAL_NOTIFICATION_CHANNEL_ID = "prism_terminal_channel"

const val DEFAULT_TERMINAL_EXTRA_KEYS =
    "[\n  [\n    \"ESC\",\n    {\n      \"key\": \"/\",\n      \"popup\": \"\\\\\"\n    },\n    {\n      \"key\": \"-\",\n      \"popup\": \"|\"\n    },\n    \"HOME\",\n    \"UP\",\n    \"END\",\n    \"PGUP\"\n  ],\n  [\n    \"TAB\",\n    \"CTRL\",\n    \"ALT\",\n    \"LEFT\",\n    \"DOWN\",\n    \"RIGHT\",\n    \"PGDN\"\n  ]\n]"
