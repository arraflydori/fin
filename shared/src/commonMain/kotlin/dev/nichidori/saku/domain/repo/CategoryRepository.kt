package dev.nichidori.saku.domain.repo

import dev.nichidori.saku.domain.model.Category
import dev.nichidori.saku.domain.model.TrxType

interface CategoryRepository {
    suspend fun addCategory(name: String, type: TrxType, icon: String?, parent: Category?)
    suspend fun getCategoryById(id: String): Category?
    suspend fun getAllCategories(): List<Category>
    suspend fun getRootCategories(): List<Category>
    suspend fun getSubcategories(parentId: String): List<Category>
    suspend fun updateCategory(id: String, name: String, type: TrxType, icon: String?, parent: Category?)
    suspend fun deleteCategory(id: String)
    suspend fun reorderCategories(parentId: String?, orderedIds: List<String>)
}