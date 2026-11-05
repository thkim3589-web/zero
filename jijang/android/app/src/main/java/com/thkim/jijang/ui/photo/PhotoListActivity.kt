package com.thkim.jijang.ui.photo

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.thkim.jijang.JijangApp
import com.thkim.jijang.R
import com.thkim.jijang.databinding.ActivityPhotoListBinding
import com.thkim.jijang.util.ImageUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/** "공문 및 사진" 시트 대응 — 현장 사진을 촬영/첨부하고 유형·설명을 입력하는 화면. */
class PhotoListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SITE_CASE_ID = "extra_site_case_id"
    }

    private lateinit var binding: ActivityPhotoListBinding
    private lateinit var viewModel: PhotoViewModel
    private var pendingCaptureFile: File? = null

    private val adapter = SitePhotoAdapter(
        onFieldChanged = { photo -> lifecycleScope.launch { viewModel.update(photo) } },
        onDelete = { photo -> lifecycleScope.launch { viewModel.delete(photo) } }
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) addFromGallery(uri)
    }

    private val captureImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCaptureFile
        pendingCaptureFile = null
        if (success && file != null) {
            lifecycleScope.launch { viewModel.addPhoto(file.absolutePath) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
        require(siteCaseId >= 0) { "EXTRA_SITE_CASE_ID required" }

        val app = application as JijangApp
        viewModel = ViewModelProvider(
            this,
            PhotoViewModel.Factory(app.database, siteCaseId)
        )[PhotoViewModel::class.java]

        binding.recyclerPhotos.layoutManager = LinearLayoutManager(this)
        binding.recyclerPhotos.adapter = adapter
        binding.fabAddPhoto.setOnClickListener { showAddPhotoDialog(siteCaseId) }

        lifecycleScope.launch {
            viewModel.photos.collectLatest { photos ->
                adapter.submitList(photos)
                binding.emptyState.visibility = if (photos.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun showAddPhotoDialog(siteCaseId: Long) {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.pick_from_gallery))
        AlertDialog.Builder(this)
            .setTitle(R.string.add_photo)
            .setItems(options) { _, which ->
                if (which == 0) launchCamera(siteCaseId) else pickImageLauncher.launch("image/*")
            }
            .show()
    }

    private fun launchCamera(siteCaseId: Long) {
        val fileName = "photo_${siteCaseId}_${System.currentTimeMillis()}.jpg"
        val (file, uri) = ImageUtil.createCaptureFile(this, fileName)
        pendingCaptureFile = file
        captureImageLauncher.launch(uri)
    }

    private fun addFromGallery(uri: Uri) {
        val siteCaseId = intent.getLongExtra(EXTRA_SITE_CASE_ID, -1)
        val fileName = "photo_${siteCaseId}_${System.currentTimeMillis()}.jpg"
        lifecycleScope.launch {
            val path = ImageUtil.copyToInternalStorage(this@PhotoListActivity, uri, fileName)
            viewModel.addPhoto(path)
        }
    }
}
