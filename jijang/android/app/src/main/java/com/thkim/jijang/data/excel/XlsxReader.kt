package com.thkim.jijang.data.excel

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/**
 * 아주 가벼운(무의존성) OOXML(.xlsx) 리더.
 *
 * xlsx는 사실 zip 안에 XML 파일들(workbook.xml, sharedStrings.xml, worksheets/sheetN.xml)이
 * 들어있는 구조이므로, Apache POI 같은 무거운 라이브러리 없이 [java.util.zip]과 표준 SAX
 * 파서만으로 필요한 시트의 셀 값만 뽑아낼 수 있다. (엑셀 "생성"이 아니라 "읽기"만 하면 되므로
 * 이 정도로 충분 — POI 도입 여부는 8단계 엑셀 생성 엔진에서 별도로 검토한다)
 */
object XlsxReader {

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp")

    /** xlsx 안에 박혀 있는 이미지(개황도를 GIS에서 캡처해 붙여넣은 사진 등) 목록.
     * 엑셀은 삽입된 그림을 전부 `xl/media/imageN.확장자`로 zip 안에 저장해두므로,
     * 시트 구조와 무관하게 그 폴더만 훑으면 된다. BitmapFactory가 못 읽는
     * emf/wmf(벡터 클립아트) 등은 제외한다. */
    fun listMediaEntries(file: File): List<String> {
        ZipFile(file).use { zip ->
            return zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("xl/media/") && it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                .sorted()
                .toList()
        }
    }

    fun readMediaBytes(file: File, entryName: String): ByteArray {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName) ?: throw IllegalArgumentException("항목을 찾을 수 없습니다: $entryName")
            return zip.getInputStream(entry).use { it.readBytes() }
        }
    }

    /** 시트 하나의 행 목록. 각 행은 "열 문자(A, B, O ...) → 셀 값 문자열" 맵. */
    fun readSheet(file: File, sheetName: String): List<Map<String, String>> {
        ZipFile(file).use { zip ->
            val sharedStrings = readSharedStrings(zip)
            val sheetEntryPath = findSheetEntryPath(zip, sheetName)
                ?: throw IllegalArgumentException("시트를 찾을 수 없습니다: $sheetName")
            val entry = zip.getEntry(sheetEntryPath)
                ?: throw IllegalArgumentException("시트 파일을 찾을 수 없습니다: $sheetEntryPath")
            return zip.getInputStream(entry).use { parseSheetRows(it, sharedStrings) }
        }
    }

    fun listSheetNames(file: File): List<String> {
        ZipFile(file).use { zip -> return readWorkbookSheets(zip).map { it.name } }
    }

    /** 시트 이름 → zip 안의 실제 항목 경로(예: `xl/worksheets/sheet3.xml`). 내보내기(쓰기)에서도 재사용. */
    fun sheetEntryPath(file: File, sheetName: String): String? {
        ZipFile(file).use { zip -> return findSheetEntryPath(zip, sheetName) }
    }

    /** 임의의 관계(.rels) 파일을 읽어 관계 ID → Target 경로 맵을 반환한다. */
    fun readRelationships(file: File, relsEntryPath: String): Map<String, String> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(relsEntryPath) ?: return emptyMap()
            return zip.getInputStream(entry).use { parseRelationships(it) }
        }
    }

    private data class SheetRef(val name: String, val relId: String)

    private fun readWorkbookSheets(zip: ZipFile): List<SheetRef> {
        val entry = zip.getEntry("xl/workbook.xml") ?: return emptyList()
        val result = mutableListOf<SheetRef>()
        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        zip.getInputStream(entry).use { stream ->
            parser.parse(stream, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    if (qName == "sheet" || localName == "sheet") {
                        val name = attributes.getValue("name") ?: return
                        val relId = attributes.getValue("r:id") ?: attributes.getValue("id") ?: return
                        result.add(SheetRef(name, relId))
                    }
                }
            })
        }
        return result
    }

    private fun readWorkbookRels(zip: ZipFile): Map<String, String> {
        val entry = zip.getEntry("xl/_rels/workbook.xml.rels") ?: return emptyMap()
        return zip.getInputStream(entry).use { parseRelationships(it) }
    }

    private fun parseRelationships(stream: java.io.InputStream): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(stream, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                if (qName == "Relationship" || localName == "Relationship") {
                    val id = attributes.getValue("Id") ?: return
                    val target = attributes.getValue("Target") ?: return
                    result[id] = target
                }
            }
        })
        return result
    }

    private fun findSheetEntryPath(zip: ZipFile, sheetName: String): String? {
        val sheets = readWorkbookSheets(zip)
        val rels = readWorkbookRels(zip)
        val sheet = sheets.firstOrNull { it.name == sheetName } ?: return null
        val target = rels[sheet.relId] ?: return null
        return if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
    }

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        var capturing = false
        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        zip.getInputStream(entry).use { stream ->
            parser.parse(stream, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    when (qName) {
                        "si" -> { inItem = true; current.setLength(0) }
                        "t" -> capturing = true
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inItem && capturing) current.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (qName) {
                        "t" -> capturing = false
                        "si" -> { strings.add(current.toString()); inItem = false }
                    }
                }
            })
        }
        return strings
    }

    private fun parseSheetRows(stream: java.io.InputStream, sharedStrings: List<String>): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        var currentRow: MutableMap<String, String>? = null
        var currentCellRef: String? = null
        var currentCellType: String? = null
        val currentValue = StringBuilder()
        var capturingValue = false

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(stream, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (qName) {
                    "row" -> currentRow = linkedMapOf()
                    "c" -> {
                        currentCellRef = attributes.getValue("r")
                        currentCellType = attributes.getValue("t")
                    }
                    "v", "t" -> { capturingValue = true; currentValue.setLength(0) }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capturingValue) currentValue.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "v", "t" -> {
                        capturingValue = false
                        val ref = currentCellRef ?: return
                        val col = ref.takeWhile { it.isLetter() }
                        val raw = currentValue.toString()
                        val resolved = if (currentCellType == "s") {
                            raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                        } else raw
                        currentRow?.put(col, resolved)
                    }
                    "row" -> {
                        currentRow?.let { if (it.isNotEmpty()) rows.add(it) }
                        currentRow = null
                    }
                }
            }
        })
        return rows
    }
}
