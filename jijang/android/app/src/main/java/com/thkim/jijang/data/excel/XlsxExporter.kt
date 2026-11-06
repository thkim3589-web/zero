package com.thkim.jijang.data.excel

import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.FacilityPhase
import com.thkim.jijang.data.model.RouteType
import com.thkim.jijang.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val ENG_SHEET_NAME = "ENG시트(Checklist)"
private const val PHOTO_SHEET_NAME = "공문 및 사진"
private const val GIBYEOL_SHEET_NAME = "기별"
private const val DIAGRAM_BEFORE_SHEET_NAME = "개황도(전)"
private const val DIAGRAM_AFTER_SHEET_NAME = "개황도(후)"

// 실제 파일 확인 결과 — 개황도 시트의 GIS 캡처 배경 이미지는 항상 이 좌표(col 0~35, row 20~89,
// xdr 0-indexed)에 있다. 범례 아이콘들은 훨씬 작은 1~2셀짜리 앵커라 이 좌표와 절대 겹치지 않는다.
private const val DIAGRAM_BG_FROM_COL = 0
private const val DIAGRAM_BG_FROM_ROW = 20
private const val DIAGRAM_BG_TO_COL = 35
private const val DIAGRAM_BG_TO_ROW = 89

/**
 * 실제 SKT 정산 엑셀 템플릿(관리자가 한 번 올려둔 파일)을 복제하면서, ENG시트
 * 텍스트 항목/현장 사진/`기별` 구간 데이터/개황도(전·후) 합성 이미지를 채워
 * 넣는다. `기별`을 채우면 `내역서_일반`/`자재내역서_일반`/`원가계산서(일반)`는
 * 이미 그 시트를 참조하는 수식으로 돼 있으므로 엑셀에서 열 때 자동으로
 * 계산된다. 자세한 내용은 docs/09_excel_export.md, docs/11_gibyeol_export.md,
 * docs/12_diagram_export.md 참고.
 *
 * 템플릿 zip에서 우리가 건드리는 항목만 텍스트로 패치하고 나머지는 원본 바이트
 * 그대로 복사한다 — 그래서 수식/서식/다른 시트들은 항상 원본과 100% 동일하게
 * 유지된다.
 */
object XlsxExporter {

    suspend fun export(db: AppDatabase, siteCaseId: Long, templateFile: File, outputFile: File) {
        withContext(Dispatchers.IO) {
            val siteCase = db.siteCaseDao().getById(siteCaseId)
                ?: throw IllegalArgumentException("현장을 찾을 수 없습니다: $siteCaseId")
            val checklistDao = db.checklistDao()
            val checklist = checklistDao.getChecklist(siteCaseId) ?: com.thkim.jijang.data.model.ChecklistDetail(siteCaseId = siteCaseId)
            val existingCables = checklistDao.getCableItems(siteCaseId, FacilityPhase.EXISTING)
            val newCables = checklistDao.getCableItems(siteCaseId, FacilityPhase.NEW)
            val existingEnclosures = checklistDao.getEnclosureItems(siteCaseId, FacilityPhase.EXISTING)
            val newEnclosures = checklistDao.getEnclosureItems(siteCaseId, FacilityPhase.NEW)
            val poleInfo = checklistDao.getPoleInfo(siteCaseId)
            val photoList = db.sitePhotoDao().observePhotos(siteCaseId).first()
            val routeSegments = db.routeSegmentDao().getAll(siteCaseId)

            val engCells = EngSheetComposer.compose(
                siteCase, checklist, existingCables, newCables, existingEnclosures, newEnclosures, poleInfo
            )
            val gibyeolCells = GibyeolSheetComposer.compose(routeSegments)

            ZipFile(templateFile).use { zip ->
                val engSheetPath = requireSheetPath(templateFile, ENG_SHEET_NAME)
                val photoSheetPath = requireSheetPath(templateFile, PHOTO_SHEET_NAME)
                val gibyeolSheetPath = requireSheetPath(templateFile, GIBYEOL_SHEET_NAME)

                var engSheetXml = readEntryText(zip, engSheetPath)
                engCells.forEach { (ref, value) -> engSheetXml = XlsxCellPatcher.setText(engSheetXml, ref, value) }

                var photoSheetXml = readEntryText(zip, photoSheetPath)
                photoList.forEach { photo ->
                    PhotoSheetComposer.textCells(photo).forEach { (ref, value) ->
                        photoSheetXml = XlsxCellPatcher.setText(photoSheetXml, ref, value)
                    }
                }

                var gibyeolSheetXml = readEntryText(zip, gibyeolSheetPath)
                gibyeolCells.textCells.forEach { (ref, value) -> gibyeolSheetXml = XlsxCellPatcher.setText(gibyeolSheetXml, ref, value) }
                gibyeolCells.numberCells.forEach { (ref, value) -> gibyeolSheetXml = XlsxCellPatcher.setNumber(gibyeolSheetXml, ref, value) }

                // 사진/개황도 이미지를 담을 drawing 파트별 누적 상태 (같은 drawing에 여러 장 추가될 수 있음).
                var nextMediaIndex = maxExistingMediaIndex(zip) + 1
                val newMediaEntries = mutableListOf<Pair<String, ByteArray>>()
                val drawingXmlByPath = mutableMapOf<String, String>()
                val drawingRelsXmlByPath = mutableMapOf<String, String>()
                val drawingRelsMapByPath = mutableMapOf<String, MutableMap<String, String>>()

                fun drawingPathsFor(sheetPath: String): Pair<String, String> {
                    val sheetRelsPath = relsPathFor(sheetPath)
                    val sheetRels = XlsxReader.readRelationships(templateFile, sheetRelsPath)
                    val drawingRelTarget = sheetRels.values.firstOrNull { it.contains("drawing") }
                        ?: throw IllegalStateException("시트에 drawing 관계가 없습니다: $sheetPath")
                    val drawingPath = resolveRelative(dirOf(sheetPath), drawingRelTarget)
                    return drawingPath to relsPathFor(drawingPath)
                }

                fun addImageAnchor(sheetPath: String, anchor: PhotoAnchor, jpegBytes: ByteArray) {
                    val (drawingPath, drawingRelsPath) = drawingPathsFor(sheetPath)
                    val relsMap = drawingRelsMapByPath.getOrPut(drawingPath) {
                        XlsxReader.readRelationships(templateFile, drawingRelsPath).toMutableMap()
                    }
                    val mediaFileName = "image${nextMediaIndex++}.jpeg"
                    newMediaEntries.add("xl/media/$mediaFileName" to jpegBytes)
                    val relId = XlsxDrawingPatcher.nextRelId(relsMap.keys)
                    relsMap[relId] = "../media/$mediaFileName"

                    val currentDrawingXml = drawingXmlByPath.getOrPut(drawingPath) { readEntryText(zip, drawingPath) }
                    drawingXmlByPath[drawingPath] = XlsxDrawingPatcher.addAnchors(currentDrawingXml, listOf(anchor to relId))

                    val currentRelsXml = drawingRelsXmlByPath.getOrPut(drawingPath) { readEntryText(zip, drawingRelsPath) }
                    drawingRelsXmlByPath[drawingPath] =
                        XlsxDrawingPatcher.addRelationships(currentRelsXml, mapOf(relId to "../media/$mediaFileName"))
                }

                photoList.forEach { photo ->
                    val anchor = PhotoSheetComposer.anchor(photo) ?: return@forEach
                    val jpegBytes = ImageUtil.toJpegBytes(photo.localFilePath)
                    if (jpegBytes.isNotEmpty()) addImageAnchor(photoSheetPath, anchor, jpegBytes)
                }

                exportDiagram(db, siteCaseId, RouteType.BEFORE)?.let { bytes ->
                    val sheetPath = requireSheetPath(templateFile, DIAGRAM_BEFORE_SHEET_NAME)
                    val (drawingPath, _) = drawingPathsFor(sheetPath)
                    drawingXmlByPath.getOrPut(drawingPath) {
                        XlsxDrawingPatcher.removeAnchorMatchingFrom(readEntryText(zip, drawingPath), DIAGRAM_BG_FROM_COL, DIAGRAM_BG_FROM_ROW)
                    }
                    addImageAnchor(sheetPath, diagramBackgroundAnchor(), bytes)
                }
                exportDiagram(db, siteCaseId, RouteType.AFTER)?.let { bytes ->
                    val sheetPath = requireSheetPath(templateFile, DIAGRAM_AFTER_SHEET_NAME)
                    val (drawingPath, _) = drawingPathsFor(sheetPath)
                    drawingXmlByPath.getOrPut(drawingPath) {
                        XlsxDrawingPatcher.removeAnchorMatchingFrom(readEntryText(zip, drawingPath), DIAGRAM_BG_FROM_COL, DIAGRAM_BG_FROM_ROW)
                    }
                    addImageAnchor(sheetPath, diagramBackgroundAnchor(), bytes)
                }

                val workbookPath = "xl/workbook.xml"
                var workbookXml = readEntryText(zip, workbookPath)
                workbookXml = ensureFullCalcOnLoad(workbookXml)

                val patched = mutableMapOf(
                    engSheetPath to engSheetXml,
                    photoSheetPath to photoSheetXml,
                    gibyeolSheetPath to gibyeolSheetXml,
                    workbookPath to workbookXml
                )
                patched.putAll(drawingXmlByPath)
                patched.putAll(drawingRelsXmlByPath)

                writeOutputZip(zip, patched, newMediaEntries, outputFile)
            }
        }
    }

    private fun diagramBackgroundAnchor() = PhotoAnchor(
        fromCol = DIAGRAM_BG_FROM_COL,
        fromRow = DIAGRAM_BG_FROM_ROW,
        toCol = DIAGRAM_BG_TO_COL,
        toRow = DIAGRAM_BG_TO_ROW
    )

    /** 배경 이미지 + 저장된 경로(점) 목록을 합성해서 JPEG 바이트로 반환한다. 그릴 배경이 없으면 null. */
    private suspend fun exportDiagram(db: AppDatabase, siteCaseId: Long, routeType: RouteType): ByteArray? {
        val diagramDao = db.diagramDao()
        val baseMapImage = diagramDao.getBaseMapImage(siteCaseId, routeType) ?: return null
        val bitmap = ImageUtil.decodeSampledBitmap(baseMapImage.localFilePath) ?: return null
        val lines = diagramDao.getLines(baseMapImage.id).map { line ->
            LineRender(diagramDao.getPoints(line.id), line.colorHex, line.dashed, line.styleId)
        }
        val legendBitmap = baseMapImage.legendImagePath?.let { ImageUtil.decodeSampledBitmap(it) }
        val composited = DiagramCompositor.composite(bitmap, lines, legendBitmap)
        bitmap.recycle()
        legendBitmap?.recycle()
        val bytes = ImageUtil.bitmapToJpegBytes(composited)
        composited.recycle()
        return bytes
    }

    private fun requireSheetPath(templateFile: File, sheetName: String): String =
        XlsxReader.sheetEntryPath(templateFile, sheetName)
            ?: throw IllegalStateException("템플릿에서 '$sheetName' 시트를 찾을 수 없습니다")

    private fun readEntryText(zip: ZipFile, path: String): String {
        val entry = zip.getEntry(path) ?: throw IllegalStateException("템플릿에 없는 항목: $path")
        return zip.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }

    private fun dirOf(path: String): String = path.substringBeforeLast('/', "")

    private fun relsPathFor(path: String): String {
        val dir = dirOf(path)
        val name = path.substringAfterLast('/')
        return if (dir.isEmpty()) "_rels/$name.rels" else "$dir/_rels/$name.rels"
    }

    private fun resolveRelative(baseDir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = baseDir.split("/").filter { it.isNotEmpty() }.toMutableList()
        target.split("/").forEach { part ->
            when (part) {
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                "." -> {}
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun maxExistingMediaIndex(zip: ZipFile): Int {
        val regex = Regex("xl/media/image(\\d+)\\.")
        var max = 0
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            regex.find(e.name)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it > max) max = it }
        }
        return max
    }

    private fun ensureFullCalcOnLoad(workbookXml: String): String {
        val calcPr = Regex("<calcPr[^/]*/>").find(workbookXml)
        return when {
            calcPr == null -> workbookXml.replace("</workbook>", "<calcPr fullCalcOnLoad=\"1\" /></workbook>")
            calcPr.value.contains("fullCalcOnLoad") -> workbookXml
            else -> workbookXml.replaceRange(
                calcPr.range,
                calcPr.value.replace("<calcPr", "<calcPr fullCalcOnLoad=\"1\"")
            )
        }
    }

    private fun writeOutputZip(
        source: ZipFile,
        patchedText: Map<String, String>,
        newMedia: List<Pair<String, ByteArray>>,
        outputFile: File
    ) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val entries = source.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                zos.putNextEntry(ZipEntry(entry.name))
                val overrideText = patchedText[entry.name]
                if (overrideText != null) {
                    zos.write(overrideText.toByteArray(StandardCharsets.UTF_8))
                } else {
                    source.getInputStream(entry).use { it.copyTo(zos) }
                }
                zos.closeEntry()
            }
            newMedia.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }
}
