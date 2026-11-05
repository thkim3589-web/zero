package com.thkim.jijang.ui.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.thkim.jijang.data.db.AppDatabase
import com.thkim.jijang.data.model.SitePhoto
import kotlinx.coroutines.flow.Flow

class PhotoViewModel(
    private val db: AppDatabase,
    private val siteCaseId: Long
) : ViewModel() {

    val photos: Flow<List<SitePhoto>> = db.sitePhotoDao().observePhotos(siteCaseId)

    /** 사진을 지웠다 다시 추가하면, 단순히 개수 기준으로 슬롯을 매길 경우 이미 다른
     * 사진이 쓰고 있는 (blockIndex, slotIndex)와 겹칠 수 있다 — 겹치면 엑셀 내보내기
     * 시 두 사진이 같은 칸에 합성되어 한쪽이 안 보이게 된다. 실제로 비어있는 칸을
     * 찾아서 채운다. */
    suspend fun addPhoto(localFilePath: String) {
        val occupied = db.sitePhotoDao().getPhotos(siteCaseId).map { it.blockIndex to it.slotIndex }.toSet()
        var index = 0
        while ((index / 3 to index % 3) in occupied) index++
        db.sitePhotoDao().insert(
            SitePhoto(
                siteCaseId = siteCaseId,
                blockIndex = index / 3,
                slotIndex = index % 3,
                localFilePath = localFilePath
            )
        )
    }

    suspend fun update(photo: SitePhoto) = db.sitePhotoDao().update(photo)

    suspend fun delete(photo: SitePhoto) = db.sitePhotoDao().delete(photo)

    class Factory(
        private val db: AppDatabase,
        private val siteCaseId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PhotoViewModel(db, siteCaseId) as T
    }
}
