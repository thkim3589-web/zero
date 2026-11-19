package com.thkim.jijang.data.excel

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.zip.ZipException

/** 관리자가 한 번 올려두는 "빈 정산 엑셀 템플릿" 파일의 고정 저장 위치. */
object CaseTemplateStore {
    private const val FILE_NAME = "case_template.xlsx"

    // 내보내기가 반드시 찾는 시트들 — 하나라도 없으면 내보내기 도중에 실패하므로 등록 단계에서 막는다.
    private val REQUIRED_SHEETS = listOf(
        ENG_SHEET_NAME, PHOTO_SHEET_NAME, GIBYEOL_SHEET_NAME,
        DIAGRAM_BEFORE_SHEET_NAME, DIAGRAM_AFTER_SHEET_NAME
    )

    fun templateFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun isAvailable(context: Context): Boolean = templateFile(context).exists()

    /**
     * [uri]의 파일을 템플릿으로 등록한다. 임시 파일로 먼저 받아 검증하고, 통과해야만 기존 템플릿을 교체하므로
     * 잘못된 파일을 골라도 이미 등록된 템플릿은 그대로 유지된다.
     * 올바른 xlsx가 아니면 사용자에게 보여줄 한국어 사유를 담은 [IllegalArgumentException]을 던진다.
     */
    fun saveFrom(context: Context, uri: Uri) {
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("파일을 열 수 없습니다")
            input.use { src -> temp.outputStream().use { dst -> src.copyTo(dst) } }
            validate(temp)
            val target = templateFile(context)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }
        } finally {
            temp.delete()
        }
    }

    private fun validate(file: File) {
        val missing = try {
            REQUIRED_SHEETS.filter { XlsxReader.sheetEntryPath(file, it) == null }
        } catch (e: ZipException) {
            throw IllegalArgumentException(NOT_XLSX_MESSAGE)
        } catch (e: IOException) {
            // ZipFile은 zip이 아닌 파일에 대해 "error in opening zip file" 같은 IOException을 던진다.
            throw IllegalArgumentException(NOT_XLSX_MESSAGE)
        }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException(
                "표준 양식이 아닙니다. 없는 시트: ${missing.joinToString(", ")}"
            )
        }
    }

    private const val NOT_XLSX_MESSAGE =
        "xlsx 파일이 아닙니다 (문서보안 암호화는 해제 필요)"
}
