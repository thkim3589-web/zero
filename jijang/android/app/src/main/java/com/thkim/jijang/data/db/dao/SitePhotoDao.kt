package com.thkim.jijang.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thkim.jijang.data.model.SitePhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePhotoDao {

    @Query("SELECT * FROM site_photo WHERE siteCaseId = :siteCaseId ORDER BY blockIndex, slotIndex")
    fun observePhotos(siteCaseId: Long): Flow<List<SitePhoto>>

    @Query("SELECT COUNT(*) FROM site_photo WHERE siteCaseId = :siteCaseId")
    suspend fun countBySiteCase(siteCaseId: Long): Int

    @Query("SELECT * FROM site_photo WHERE siteCaseId = :siteCaseId")
    suspend fun getPhotos(siteCaseId: Long): List<SitePhoto>

    @Insert
    suspend fun insert(photo: SitePhoto): Long

    @Update
    suspend fun update(photo: SitePhoto)

    @Delete
    suspend fun delete(photo: SitePhoto)
}
