package com.techflyers.compose.file.explorer.utils

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * A minimal in-memory tree for one OOXML part.
 */
internal class OoxmlNode(val name: String, val attrs: Map<String, String>) {

    val children = mutableListOf<OoxmlNode>()
    var text: String = ""

    fun attr(key: String): String? = attrs[key]

    /** Direct child by tag name. */
    fun child(tag: String): OoxmlNode? = children.firstOrNull { it.name == tag }

    /** Direct children by tag name. */
    fun childrenNamed(tag: String): List<OoxmlNode> = children.filter { it.name == tag }

    /** First descendant by tag name, depth-first, this node included. */
    fun find(tag: String): OoxmlNode? {
        if (name == tag) return this
        for (c in children) c.find(tag)?.let { return it }
        return null
    }

    /** All text under this node, in document order — the flattened value of a `<a:t>` run group. */
    fun textContent(): String {
        if (children.isEmpty()) return text
        val sb = StringBuilder(text)
        for (c in children) sb.append(c.textContent())
        return sb.toString()
    }

    companion object {
        fun parse(stream: InputStream): OoxmlNode? = runCatching {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(stream, "UTF-8")
            }
            var root: OoxmlNode? = null
            val stack = ArrayDeque<OoxmlNode>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val attrs = HashMap<String, String>(parser.attributeCount)
                        for (i in 0 until parser.attributeCount) {
                            attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        }
                        val node = OoxmlNode(parser.name, attrs)
                        stack.lastOrNull()?.children?.add(node)
                        if (root == null) root = node
                        stack.addLast(node)
                    }
                    XmlPullParser.TEXT -> stack.lastOrNull()?.let { it.text += parser.text }
                    XmlPullParser.END_TAG -> stack.removeLastOrNull()
                }
                event = parser.next()
            }
            root
        }.getOrNull()
    }
}
