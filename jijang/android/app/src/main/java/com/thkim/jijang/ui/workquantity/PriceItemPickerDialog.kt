package com.thkim.jijang.ui.workquantity

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.thkim.jijang.databinding.DialogPickPriceItemBinding
import com.thkim.jijang.ui.priceupload.PriceUploadActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 노무/자재 단가 마스터에서 검색으로 항목 하나를 골라 [WorkQuantity] 행을 추가할 때 쓰는 다이얼로그. */
object PriceItemPickerDialog {

    fun show(
        context: Context,
        scope: CoroutineScope,
        searchFn: suspend (String) -> List<PriceItemOption>,
        onSelected: (PriceItemOption) -> Unit
    ) {
        val binding = DialogPickPriceItemBinding.inflate(LayoutInflater.from(context))
        binding.recyclerResults.layoutManager = LinearLayoutManager(context)

        val dialog = AlertDialog.Builder(context)
            .setTitle("항목 검색")
            .setView(binding.root)
            .setNegativeButton("닫기", null)
            .create()

        binding.recyclerResults.adapter = PriceItemOptionAdapter { option ->
            onSelected(option)
            dialog.dismiss()
        }

        // 첫 검색(빈 검색어)에서도 결과가 없으면 "단가표 자체가 없다"는 뜻 —
        // 이후 검색이 비어도 계속 이 안내를 보여준다. 반대로 단가표는 있는데
        // 특정 검색어만 안 맞으면 "검색 결과 없음"으로 다르게 안내한다.
        var masterIsEmpty = false

        fun updateEmptyState(resultCount: Int, query: String) {
            binding.emptyState.visibility = if (resultCount == 0) View.VISIBLE else View.GONE
            if (resultCount == 0) {
                if (masterIsEmpty) {
                    binding.textEmptyMessage.text = "아직 단가표가 업로드되지 않았습니다.\n먼저 단가표(xlsx)를 업로드해야 항목을 고를 수 있습니다."
                    binding.buttonGoUpload.visibility = View.VISIBLE
                } else {
                    binding.textEmptyMessage.text = "\"$query\"에 대한 검색 결과가 없습니다."
                    binding.buttonGoUpload.visibility = View.GONE
                }
            }
        }

        binding.buttonGoUpload.setOnClickListener {
            context.startActivity(Intent(context, PriceUploadActivity::class.java))
            dialog.dismiss()
        }

        var searchJob: Job? = null
        fun runSearch(query: String) {
            searchJob?.cancel()
            searchJob = scope.launch {
                delay(200)
                val results = searchFn(query)
                if (query.isBlank()) masterIsEmpty = results.isEmpty()
                (binding.recyclerResults.adapter as PriceItemOptionAdapter).submitList(results)
                updateEmptyState(results.size, query)
            }
        }

        binding.editSearchQuery.doAfterTextChanged { text -> runSearch(text?.toString().orEmpty()) }
        dialog.show()
        runSearch("")
    }
}
