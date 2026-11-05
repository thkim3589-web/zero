package com.thkim.jijang.data.excel

/**
 * 시트 XML(텍스트) 안의 특정 셀(`r="B3"` 등) 값을 치환하는 문자열 기반 패처.
 *
 * Apache POI처럼 전체 워크북 객체 모델을 만들지 않고, 건드릴 셀만 텍스트로 찾아
 * 바꾼 뒤 나머지 zip 항목은 원본 그대로 복사한다 — 그래서 서식/수식/그림 등
 * 우리가 손대지 않는 모든 것이 원본과 100% 동일하게 유지된다. 실제 값을 바꾸는
 * 셀만 인라인 문자열(`t="inlineStr"`)로 새로 쓰므로 sharedStrings.xml은 건드릴
 * 필요가 없다.
 */
object XlsxCellPatcher {

    fun setText(sheetXml: String, cellRef: String, text: String): String =
        replaceOrInsertCell(sheetXml, cellRef) { styleAttr ->
            "<c r=\"$cellRef\"$styleAttr t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(text)}</t></is></c>"
        }

    fun setNumber(sheetXml: String, cellRef: String, number: Double): String =
        replaceOrInsertCell(sheetXml, cellRef) { styleAttr ->
            val formatted = if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
            "<c r=\"$cellRef\"$styleAttr><v>$formatted</v></c>"
        }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private val rowNumberOf = Regex("\\d+")

    private fun replaceOrInsertCell(sheetXml: String, cellRef: String, buildCell: (styleAttr: String) -> String): String {
        val selfClosing = Regex("<c r=\"$cellRef\"([^>]*?)/>")
        val withContent = Regex("<c r=\"$cellRef\"([^>]*?)>(?:(?!</c>).)*</c>", RegexOption.DOT_MATCHES_ALL)

        selfClosing.find(sheetXml)?.let { match ->
            val newCell = buildCell(styleAttrOf(match.groupValues[1]))
            return sheetXml.replaceRange(match.range, newCell)
        }
        withContent.find(sheetXml)?.let { match ->
            val newCell = buildCell(styleAttrOf(match.groupValues[1]))
            return sheetXml.replaceRange(match.range, newCell)
        }

        // 셀 자체가 행에 없는 경우 — 같은 행 시작 태그 바로 뒤에 삽입한다 (열 정렬은 보장 못 하지만
        // 엑셀은 읽을 때 관대하게 처리한다).
        val rowNum = rowNumberOf.find(cellRef.dropWhile { it.isLetter() })?.value
            ?: throw IllegalArgumentException("잘못된 셀 참조: $cellRef")
        val rowStart = Regex("<row r=\"$rowNum\"[^>]*>").find(sheetXml)
            ?: throw IllegalStateException("행을 찾을 수 없습니다 (템플릿 구조가 예상과 다름): row $rowNum")
        val newCell = buildCell("")
        val insertAt = rowStart.range.last + 1
        return sheetXml.substring(0, insertAt) + newCell + sheetXml.substring(insertAt)
    }

    private fun styleAttrOf(existingAttrs: String): String {
        val styleMatch = Regex("s=\"(\\d+)\"").find(existingAttrs)
        return if (styleMatch != null) " s=\"${styleMatch.groupValues[1]}\"" else ""
    }
}
