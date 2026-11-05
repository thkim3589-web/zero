package com.thkim.jijang.data.excel

/** [XlsxCellPatcher]와 같은 방식(문자열 치환)으로 drawing 파트에 사진 앵커를 추가한다. */
object XlsxDrawingPatcher {

    /** `xl/drawings/drawingN.xml`의 `</xdr:wsDr>` 앞에 새 twoCellAnchor들을 삽입한다. */
    fun addAnchors(drawingXml: String, anchors: List<Pair<PhotoAnchor, String>>): String {
        if (anchors.isEmpty()) return drawingXml
        val insertAt = drawingXml.lastIndexOf("</xdr:wsDr>")
        require(insertAt >= 0) { "drawing.xml 구조가 예상과 다릅니다 (</xdr:wsDr> 없음)" }
        val sb = StringBuilder()
        anchors.forEachIndexed { i, (anchor, relId) -> sb.append(buildAnchorXml(anchor, relId, shapeId = 90000 + i)) }
        return drawingXml.substring(0, insertAt) + sb + drawingXml.substring(insertAt)
    }

    private fun buildAnchorXml(a: PhotoAnchor, relId: String, shapeId: Int): String =
        "<xdr:twoCellAnchor editAs=\"oneCell\">" +
            "<xdr:from><xdr:col>${a.fromCol}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${a.fromRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>" +
            "<xdr:to><xdr:col>${a.toCol}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${a.toRow}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>" +
            "<xdr:pic>" +
            "<xdr:nvPicPr><xdr:cNvPr id=\"$shapeId\" name=\"jijang_photo_$shapeId\" /><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\" /></xdr:cNvPicPr></xdr:nvPicPr>" +
            "<xdr:blipFill><a:blip xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:embed=\"$relId\" /><a:stretch><a:fillRect /></a:stretch></xdr:blipFill>" +
            "<xdr:spPr><a:prstGeom prst=\"rect\"><a:avLst /></a:prstGeom></xdr:spPr>" +
            "</xdr:pic><xdr:clientData /></xdr:twoCellAnchor>"

    /** `xl/drawings/_rels/drawingN.xml.rels`의 `</Relationships>` 앞에 새 이미지 관계를 추가한다. */
    fun addRelationships(relsXml: String, relIdToMediaTarget: Map<String, String>): String {
        if (relIdToMediaTarget.isEmpty()) return relsXml
        val insertAt = relsXml.lastIndexOf("</Relationships>")
        require(insertAt >= 0) { "drawing rels 구조가 예상과 다릅니다 (</Relationships> 없음)" }
        val sb = StringBuilder()
        relIdToMediaTarget.forEach { (id, target) ->
            sb.append("<Relationship Id=\"$id\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"$target\" />")
        }
        return relsXml.substring(0, insertAt) + sb + relsXml.substring(insertAt)
    }

    /**
     * `from` 좌표가 정확히 일치하는 twoCellAnchor 하나를 통째로 제거한다 — 개황도
     * 시트의 "배경 이미지" 앵커(예: col=0,row=20)만 정확히 골라내서 새 합성
     * 이미지로 바꿔치기할 때 쓴다. 범례 아이콘 등 다른 작은 앵커들은 좌표가
     * 달라서 영향받지 않는다. 일치하는 게 없으면 원본을 그대로 반환한다
     * (템플릿에 배경 이미지가 아직 없는 빈 캔버스인 경우).
     */
    fun removeAnchorMatchingFrom(drawingXml: String, fromCol: Int, fromRow: Int): String {
        val anchorPattern = Regex("<xdr:twoCellAnchor.*?</xdr:twoCellAnchor>", RegexOption.DOT_MATCHES_ALL)
        val fromPattern = Regex("<xdr:from><xdr:col>$fromCol</xdr:col><xdr:colOff>\\d+</xdr:colOff><xdr:row>$fromRow</xdr:row>")
        val match = anchorPattern.findAll(drawingXml).firstOrNull { fromPattern.containsMatchIn(it.value) }
        return if (match != null) drawingXml.removeRange(match.range) else drawingXml
    }

    fun nextRelId(existingIds: Collection<String>): String {
        val next = (existingIds.mapNotNull { it.removePrefix("rId").toIntOrNull() }.maxOrNull() ?: 0) + 1
        return "rId$next"
    }
}
