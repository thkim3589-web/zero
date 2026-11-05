package com.thkim.jijang.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

object ImageUtil {

    /** picker에서 받은 이미지를 앱 내부 저장소로 복사하고, 저장된 경로를 반환한다. */
    fun copyToInternalStorage(context: Context, uri: Uri, destFileName: String): String {
        val destFile = File(context.filesDir, destFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile.absolutePath
    }

    /** 이미지를 다운샘플링해서 JPEG 바이트로 인코딩한다 (엑셀 내보내기용 — 원본 형식이
     * 무엇이든 항상 JPEG로 통일해서 [Content_Types].xml을 건드릴 필요가 없게 한다). */
    fun toJpegBytes(path: String, maxDimensionPx: Int = 1600, quality: Int = 82): ByteArray {
        val bitmap = decodeSampledBitmap(path, maxDimensionPx) ?: return ByteArray(0)
        val bytes = bitmapToJpegBytes(bitmap, quality)
        bitmap.recycle()
        return bytes
    }

    /** 이미 메모리에 있는 비트맵(예: 개황도 합성 결과)을 JPEG 바이트로 인코딩한다. */
    fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    /** 카메라 촬영본을 바로 저장할 내부 저장소 파일과, 카메라 인텐트에 넘길 content:// Uri를 만든다. */
    fun createCaptureFile(context: Context, destFileName: String): Pair<File, Uri> {
        val destFile = File(context.filesDir, destFileName)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", destFile
        )
        return destFile to uri
    }

    /** 큰 GIS 캡처 이미지를 메모리 안전하게(다운샘플링) 불러온다. */
    fun decodeSampledBitmap(path: String, maxDimensionPx: Int = 2048): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOptions)
        var sample = 1
        while (boundsOptions.outWidth / (sample * 2) >= maxDimensionPx ||
            boundsOptions.outHeight / (sample * 2) >= maxDimensionPx
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, options)
    }
}
