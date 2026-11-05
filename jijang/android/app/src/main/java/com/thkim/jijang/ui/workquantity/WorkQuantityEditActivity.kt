package com.thkim.jijang.ui.workquantity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.thkim.jijang.JijangApp
import com.thkim.jijang.data.model.WorkQuantity
import com.thkim.jijang.databinding.ActivityWorkQuantityBinding
import com.thkim.jijang.databinding.ItemWorkQuantityRowBinding
import kotlinx.coroutines.launch

/**
 * 8단계 — 내역서/자재내역서에 들어갈 노무·자재 수량을 입력하는 화면.
 * 단가 마스터(7단계에서 업로드한 값)에서 항목을 검색해서 골라 담고, 수량만 입력한다.
 */
class WorkQuantityEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_CASE_ID = "extra_site_case_id"
        const val EXTRA_EFFECTIVE_YEAR = "extra_effective_year"
    }

    private lateinit var binding: ActivityWorkQuantityBinding
    private lateinit var viewModel: WorkQuantityViewModel
    private var siteCaseId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkQuantityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
        val effectiveYear = intent.getIntExtra(EXTRA_EFFECTIVE_YEAR, -1)
        require(siteCaseId >= 0) { "EXTRA_SITE_CASE_ID required" }

        val app = application as JijangApp
        viewModel = ViewModelProvider(
            this,
            WorkQuantityViewModel.Factory(app.database, siteCaseId, effectiveYear)
        )[WorkQuantityViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonAddLabor.setOnClickListener {
            PriceItemPickerDialog.show(this, lifecycleScope, { q -> viewModel.searchLabor(q) }) { option ->
                addRow(binding.containerLabor, option, WorkQuantity(siteCaseId = siteCaseId, masterType = WorkQuantityViewModel.MASTER_TYPE_LABOR, code = option.code, quantity = 0.0))
            }
        }
        binding.buttonAddMaterial.setOnClickListener {
            PriceItemPickerDialog.show(this, lifecycleScope, { q -> viewModel.searchMaterial(q) }) { option ->
                addRow(binding.containerMaterial, option, WorkQuantity(siteCaseId = siteCaseId, masterType = WorkQuantityViewModel.MASTER_TYPE_MATERIAL, code = option.code, quantity = 0.0))
            }
        }
        binding.buttonSave.setOnClickListener { save() }

        lifecycleScope.launch {
            viewModel.loadRows(WorkQuantityViewModel.MASTER_TYPE_LABOR).forEach {
                addRow(binding.containerLabor, PriceItemOption(it.workQuantity.code, it.displayName, it.unit), it.workQuantity)
            }
            viewModel.loadRows(WorkQuantityViewModel.MASTER_TYPE_MATERIAL).forEach {
                addRow(binding.containerMaterial, PriceItemOption(it.workQuantity.code, it.displayName, it.unit), it.workQuantity)
            }
        }
    }

    private fun addRow(container: ViewGroup, option: PriceItemOption, workQuantity: WorkQuantity) {
        val row = ItemWorkQuantityRowBinding.inflate(LayoutInflater.from(this), container, false)
        row.root.tag = option.code
        row.textItemName.text = option.label
        row.textUnit.text = option.unit
        if (workQuantity.quantity != 0.0) row.editQuantity.setText(workQuantity.quantity.toString())
        row.buttonDelete.setOnClickListener { container.removeView(row.root) }
        container.addView(row.root)
    }

    private fun readRows(container: ViewGroup, masterType: String): List<WorkQuantity> =
        (0 until container.childCount).map { i ->
            val child = container.getChildAt(i)
            val row = ItemWorkQuantityRowBinding.bind(child)
            WorkQuantity(
                siteCaseId = siteCaseId,
                masterType = masterType,
                code = child.tag as String,
                quantity = row.editQuantity.text.toString().toDoubleOrNull() ?: 0.0
            )
        }

    private fun save() {
        val laborRows = readRows(binding.containerLabor, WorkQuantityViewModel.MASTER_TYPE_LABOR)
        val materialRows = readRows(binding.containerMaterial, WorkQuantityViewModel.MASTER_TYPE_MATERIAL)
        lifecycleScope.launch {
            viewModel.save(laborRows, materialRows)
            Toast.makeText(this@WorkQuantityEditActivity, "저장되었습니다", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
