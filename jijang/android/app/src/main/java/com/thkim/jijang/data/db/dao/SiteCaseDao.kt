package com.thkim.jijang.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thkim.jijang.data.model.SiteCase
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteCaseDao {
    @Query("SELECT * FROM site_case ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SiteCase>>

    @Query("SELECT * FROM site_case WHERE id = :id")
    suspend fun getById(id: Long): SiteCase?

    @Insert
    suspend fun insert(siteCase: SiteCase): Long

    @Update
    suspend fun update(siteCase: SiteCase)

    @Delete
    suspend fun delete(siteCase: SiteCase)
}
