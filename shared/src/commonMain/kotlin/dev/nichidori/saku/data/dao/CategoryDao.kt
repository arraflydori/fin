package dev.nichidori.saku.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.nichidori.saku.data.entity.CategoryEntity

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM category ORDER BY sort_order ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE parent_id IS NULL ORDER BY sort_order ASC, name ASC")
    suspend fun getRootCategories(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE parent_id = :parentId ORDER BY sort_order ASC, name ASC")
    suspend fun getSubcategories(parentId: String): List<CategoryEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM category WHERE parent_id IS NULL")
    suspend fun getMaxSortOrderForRoots(): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM category WHERE parent_id = :parentId")
    suspend fun getMaxSortOrderForParent(parentId: String): Int

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteById(id: String)
}
