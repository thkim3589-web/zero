package com.thkim.jijang.ui.priceupload

import com.thkim.jijang.util.tryExpected
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.thkim.jijang.JijangApp
import com.thkim.jijang.data.model.PriceUploadHistory
import com.thkim.jijang.databinding.ActivityPriceUploadBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 7단계 — SKT 단가 개정 파일을 업로드해서 노무/자재 단가 마스터를 갱신하는 화면. */
class PriceUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriceUploadBinding
    private lateinit var viewModel: PriceUploadViewModel
    private var selectedUri: Uri? = null
    private var selectedFileName: String? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryDisplayName(uri) ?: "선택한 파일"
            binding.textSelectedFile.text = selectedFileName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriceUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as JijangApp
        viewModel = ViewModelProvider(this, PriceUploadViewModel.Factory(app.database))[PriceUploadViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonPickFile.setOnClickListener { pickFileLauncher.launch("*/*") }
        binding.buttonUpload.setOnClickListener { upload() }

        loadHistory()
    }

    private fun upload() {
        val uri = selectedUri
        val fileName = selectedFileName
        if (uri == null || fileName == null) {
            Toast.makeText(this, "먼저 단가 파일을 선택하세요", Toast.LENGTH_SHORT).show()
            return
        }
        val year = binding.editEffectiveYear.text.toString().toIntOrNull()
        if (year == null) {
            Toast.makeText(this, "적용 연도를 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            tryExpected {
                val cacheFile = copyToCache(uri, fileName)
                val result = viewModel.parseAndSave(cacheFile, year, fileName)
                cacheFile.delete()
                Toast.makeText(
                    this@PriceUploadActivity,
                    "노무단가 ${result.laborCount}건, 자재단가 ${result.materialCount}건 저장되었습니다",
                    Toast.LENGTH_LONG
                ).show()
                loadHistory()
            }?.let { e ->
                Toast.makeText(this@PriceUploadActivity, "업로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val history = viewModel.uploadHistory()
            binding.containerHistory.removeAllViews()
            if (history.isEmpty()) {
                binding.containerHistory.addView(historyRow("업로드 이력이 없습니다"))
            } else {
                history.forEach { binding.containerHistory.addView(historyRow(formatHistory(it))) }
            }
        }
    }

    private fun formatHistory(history: PriceUploadHistory): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(history.uploadedAt))
        return "[${history.effectiveYear}년] ${history.fileName}\n" +
            "노무 ${history.laborRowCount}건 · 자재 ${history.materialRowCount}건 · $date"
    }

    private fun historyRow(text: String): TextView = TextView(this).apply {
        this.text = text
        setPadding(28, 22, 28, 22)
        gravity = Gravity.START
        textSize = 13f
        setLineSpacing(6f, 1f)
        setTextColor(resources.getColor(com.thkim.jijang.R.color.text_secondary, theme))
        background = resources.getDrawable(com.thkim.jijang.R.drawable.bg_input_field, theme)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 10 }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        } finally {
            cursor?.close()
        }
    }

    private fun copyToCache(uri: Uri, fileName: String): File {
        val destFile = File(cacheDir, "upload_${System.currentTimeMillis()}_$fileName")
        contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile
    }
}
