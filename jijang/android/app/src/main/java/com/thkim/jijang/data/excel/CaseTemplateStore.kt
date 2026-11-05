package com.thkim.jijang.data.excel

import android.content.Context
import java.io.File

/** 관리자가 한 번 올려두는 "빈 정산 엑셀 템플릿" 파일의 고정 저장 위치. */
object CaseTemplateStore {
    private const val FILE_NAME = "case_template.xlsx"

    fun templateFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun isAvailable(context: Context): Boolean = templateFile(context).exists()
}
