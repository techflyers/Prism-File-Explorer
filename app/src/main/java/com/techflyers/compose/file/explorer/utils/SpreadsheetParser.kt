package com.techflyers.compose.file.explorer.utils

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

object SpreadsheetParser {

    private const val MaxColumns = 1024

    data class Sheet(
        val name: String,
        val rows: List<List<String>>,
        val columnLabels: List<String> = emptyList(),
        val rowNumbers: List<Int> = emptyList()
    ) {
        val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
        fun labelAt(index: Int): String = columnLabels.getOrNull(index) ?: colLetter(index)
        fun rowNumberAt(index: Int): Int = rowNumbers.getOrNull(index) ?: (index + 1)
    }

    fun parse(context: Context, uri: Uri): List<Sheet> = runCatching {
        val name = queryName(context, uri).lowercase()
        when {
            name.endsWith(".xls") -> {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) emptyList() else parseXls(bytes)
            }
            name.endsWith(".csv") || name.endsWith(".tsv") -> {
                val delim = if (name.endsWith(".tsv")) '\t' else ','
                context.contentResolver.openInputStream(uri)?.use { parseCsv(it, delim) } ?: emptyList()
            }
            else -> {
                context.contentResolver.openInputStream(uri)?.use { parseXlsx(it) } ?: emptyList()
            }
        }
    }.getOrDefault(emptyList())

    fun parseCsv(stream: InputStream, delimiter: Char = ','): List<Sheet> {
        val reader = java.io.BufferedReader(java.io.InputStreamReader(stream))
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false

        reader.use {
            var chInt = it.read()
            while (chInt != -1) {
                val ch = chInt.toChar()
                when {
                    ch == '"' -> {
                        if (inQuotes) {
                            it.mark(1)
                            val next = it.read()
                            if (next != -1 && next.toChar() == '"') {
                                currentField.append('"')
                            } else {
                                inQuotes = false
                                it.reset()
                            }
                        } else {
                            inQuotes = true
                        }
                    }
                    ch == delimiter && !inQuotes -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                    }
                    (ch == '\n' || ch == '\r') && !inQuotes -> {
                        if (ch == '\r') {
                            it.mark(1)
                            val next = it.read()
                            if (next != -1 && next.toChar() != '\n') {
                                it.reset()
                            }
                        }
                        currentRow.add(currentField.toString().trim())
                        currentField.clear()
                        if (currentRow.any { f -> f.isNotEmpty() }) {
                            rows.add(currentRow)
                        }
                        currentRow = mutableListOf()
                    }
                    else -> {
                        currentField.append(ch)
                    }
                }
                chInt = it.read()
            }
            if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
                currentRow.add(currentField.toString().trim())
                if (currentRow.any { f -> f.isNotEmpty() }) {
                    rows.add(currentRow)
                }
            }
        }
        if (rows.isEmpty()) return emptyList()
        val maxCols = rows.maxOf { it.size }
        return listOf(
            Sheet(
                name = "Sheet 1",
                rows = rows,
                columnLabels = (0 until maxCols).map { colLetter(it) },
                rowNumbers = (1..rows.size).toList()
            )
        )
    }

    fun colLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) { sb.insert(0, 'A' + (i % 26)); i = i / 26 - 1 }
        return sb.toString()
    }

    fun parseXlsx(bytes: ByteArray): List<Sheet> = bytes.inputStream().use { parseXlsx(it) }

    fun parseXlsx(source: InputStream): List<Sheet> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(source).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (isNeeded(entry.name)) entries[entry.name] = zip.readBytes()
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
        val formatCodes = parseStyles(entries["xl/styles.xml"])
        val workbookSheets = parseWorkbookSheets(entries["xl/workbook.xml"])
        val rels = parseRels(entries["xl/_rels/workbook.xml.rels"])

        val ordered: List<Pair<String, ByteArray>> = if (workbookSheets.isNotEmpty()) {
            workbookSheets.mapNotNull { (name, rId) ->
                val target = rels[rId] ?: return@mapNotNull null
                val path = if (target.startsWith("/")) target.drop(1) else "xl/${target.removePrefix("/")}"
                entries[path]?.let { name to it }
            }
        } else {
            entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sortedBy { Regex("sheet(\\d+)\\.xml").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE }
                .map { (Regex("sheet(\\d+)").find(it)?.groupValues?.getOrNull(1)?.let { n -> "Sheet $n" } ?: "Sheet") to entries[it]!! }
        }

        return ordered.map { (name, xml) ->
            buildSheet(name, parseWorksheet(xml.inputStream(), sharedStrings, formatCodes))
        }.filter { it.rows.isNotEmpty() }
    }

    private class RawSheet {
        val rows = mutableListOf<List<String>>()
        val rowNumbers = mutableListOf<Int>()
        val hiddenCols = HashSet<Int>()
    }

    private fun buildSheet(name: String, raw: RawSheet): Sheet {
        val width = raw.rows.maxOfOrNull { it.size } ?: 0
        val visible = (0 until width).filter { it !in raw.hiddenCols }
        if (visible.isEmpty() || visible.size == width) {
            return Sheet(name, raw.rows, (0 until width).map { colLetter(it) }, raw.rowNumbers)
        }
        val rows = raw.rows.map { row -> visible.map { row.getOrElse(it) { "" } } }
        return Sheet(name, rows, visible.map { colLetter(it) }, raw.rowNumbers)
    }

    private fun parseStyles(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val custom = HashMap<Int, String>()
        val numFmtIds = mutableListOf<Int>()
        return runCatching {
            val parser = newParser(bytes.inputStream())
            var inCellXfs = false
            var inDxfs = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "dxfs" -> inDxfs = true
                        "numFmt" -> if (!inDxfs) {
                            val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                            val code = parser.getAttributeValue(null, "formatCode")
                            if (id != null && code != null) custom[id] = code
                        }
                        "cellXfs" -> inCellXfs = true
                        "xf" -> if (inCellXfs) {
                            numFmtIds.add(parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0)
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "cellXfs" -> inCellXfs = false
                        "dxfs" -> inDxfs = false
                    }
                }
                event = parser.next()
            }
            ExcelCellFormat.formatCodesFor(numFmtIds, custom)
        }.getOrDefault(emptyList())
    }

    private fun parseWorkbookSheets(bytes: ByteArray?): List<Pair<String, String>> {
        if (bytes == null) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        val parser = newParser(bytes.inputStream())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val rId = parser.getAttributeValue(null, "r:id") ?: parser.getAttributeValue(null, "id") ?: ""
                out.add(name to rId)
            }
            event = parser.next()
        }
        return out
    }

    private fun parseRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val out = HashMap<String, String>()
        val parser = newParser(bytes.inputStream())
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id") ?: ""
                val target = parser.getAttributeValue(null, "Target") ?: ""
                if (id.isNotEmpty()) out[id] = target
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = newParser(stream)
        var inT = false
        var inPhonetic = false
        val buf = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> buf.clear()
                    "rPh" -> inPhonetic = true
                    "t" -> inT = !inPhonetic
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "si" -> { strings.add(buf.toString()); inT = false; inPhonetic = false }
                    "rPh" -> inPhonetic = false
                    "t" -> inT = false
                }
                XmlPullParser.TEXT -> if (inT) buf.append(parser.text)
            }
            event = parser.next()
        }
        return strings
    }

    private fun colIndexFromRef(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var idx = 0
        var sawLetter = false
        for (ch in ref) {
            val up = ch.uppercaseChar()
            if (up in 'A'..'Z') { idx = idx * 26 + (up - 'A' + 1); sawLetter = true } else break
        }
        return if (sawLetter) idx - 1 else -1
    }

    private fun parseWorksheet(
        stream: InputStream,
        sharedStrings: List<String>,
        formatCodes: List<String>
    ): RawSheet {
        val out = RawSheet()
        val parser = newParser(stream)
        var rowCells = sortedMapOf<Int, String>()
        var cellType = ""
        var cellStyle = -1
        var cellCol = 0
        var nextAutoCol = 0
        var rowNumber = 0
        var rowHidden = false
        var inVal = false
        val cellBuf = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "col" -> {
                        if (parser.getAttributeValue(null, "hidden") == "1") {
                            val min = parser.getAttributeValue(null, "min")?.toIntOrNull() ?: 0
                            val max = parser.getAttributeValue(null, "max")?.toIntOrNull() ?: min
                            for (c in min..max.coerceAtMost(MaxColumns)) if (c >= 1) out.hiddenCols.add(c - 1)
                        }
                    }
                    "row" -> {
                        rowCells = sortedMapOf()
                        nextAutoCol = 0
                        rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (out.rows.size + 1)
                        rowHidden = parser.getAttributeValue(null, "hidden") == "1"
                    }
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                        cellStyle = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                        cellCol = colIndexFromRef(parser.getAttributeValue(null, "r")).let { if (it >= 0) it else nextAutoCol }
                        inVal = false; cellBuf.clear()
                    }
                    "v", "t" -> inVal = true
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "row" -> if (!rowHidden) {
                        if (rowCells.isNotEmpty()) {
                            val maxC = rowCells.lastKey()
                            out.rows.add((0..maxC).map { rowCells[it] ?: "" })
                        } else out.rows.add(emptyList())
                        out.rowNumbers.add(rowNumber)
                    }
                    "c" -> {
                        val raw = cellBuf.toString()
                        val value = when (cellType) {
                            "s" -> sharedStrings.getOrElse(raw.trim().toIntOrNull() ?: -1) { raw }
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            "str", "inlineStr", "e" -> raw
                            else -> ExcelCellFormat.apply(raw, formatCodes.getOrNull(cellStyle))
                        }
                        if (value.isNotEmpty() && cellCol in 0 until MaxColumns) rowCells[cellCol] = value
                        nextAutoCol = cellCol + 1
                        inVal = false
                    }
                    "v", "t" -> inVal = false
                }
                XmlPullParser.TEXT -> if (inVal) cellBuf.append(parser.text)
            }
            event = parser.next()
            if (out.rows.size > 20000) break
        }
        while (out.rows.isNotEmpty() && out.rows.last().all { it.isBlank() }) {
            out.rows.removeAt(out.rows.lastIndex)
            if (out.rowNumbers.isNotEmpty()) out.rowNumbers.removeAt(out.rowNumbers.lastIndex)
        }
        return out
    }

    private fun parseXls(bytes: ByteArray): List<Sheet> {
        HSSFWorkbook(bytes.inputStream()).use { wb ->
            return (0 until wb.numberOfSheets).map { si ->
                val sheet = wb.getSheetAt(si)
                val width = sheet.maxOfOrNull { it.lastCellNum.toInt().coerceAtLeast(0) } ?: 0
                val visible = (0 until width).filter { !sheet.isColumnHidden(it) }
                    .ifEmpty { (0 until width).toList() }
                val kept = sheet.filter { !it.zeroHeight }
                val rows = kept.map { row -> visible.map { c -> row.getCell(c)?.toString()?.trim() ?: "" } }
                Sheet(
                    name = wb.getSheetName(si) ?: "Sheet ${si + 1}",
                    rows = rows,
                    columnLabels = visible.map { colLetter(it) },
                    rowNumbers = kept.map { it.rowNum + 1 }
                )
            }.filter { it.rows.isNotEmpty() }
        }
    }

    private fun isNeeded(entryName: String): Boolean {
        val n = entryName.removePrefix("/")
        return n.endsWith("workbook.xml") ||
            n.endsWith("workbook.xml.rels") ||
            n.endsWith("sharedStrings.xml") ||
            n.endsWith("styles.xml") ||
            (n.contains("worksheets/") && n.endsWith(".xml"))
    }

    private fun newParser(stream: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(stream, "UTF-8")
    }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i != -1 && c.moveToFirst()) c.getString(i) else null
        } ?: uri.lastPathSegment ?: "sheet"
}
