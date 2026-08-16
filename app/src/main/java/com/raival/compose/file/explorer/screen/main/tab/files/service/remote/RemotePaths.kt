package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

object RemotePaths {
    fun normalize(path: String): String {
        if (path.isEmpty()) return "/"
        val withSlash = if (path.startsWith("/")) path else "/$path"
        return withSlash.trimEnd('/').ifEmpty { "/" }
    }

    fun join(parent: String, child: String): String {
        val p = normalize(parent)
        val c = child.trim('/').replace('\\', '/')
        if (c.isEmpty()) return p
        return if (p == "/") "/$c" else "$p/$c"
    }

    fun parent(path: String): String? {
        val n = normalize(path)
        if (n == "/") return null
        val idx = n.lastIndexOf('/')
        return if (idx <= 0) "/" else n.substring(0, idx)
    }

    fun name(path: String): String {
        val n = normalize(path)
        if (n == "/") return "/"
        return n.substringAfterLast('/')
    }

    fun isRootOf(path: String, rootPath: String): Boolean {
        return normalize(path) == normalize(rootPath)
    }
}
