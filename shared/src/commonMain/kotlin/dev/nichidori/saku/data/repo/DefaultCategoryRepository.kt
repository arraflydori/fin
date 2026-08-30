package dev.nichidori.saku.data.repo

import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import dev.nichidori.saku.data.AppDatabase
import dev.nichidori.saku.data.entity.toDomain
import dev.nichidori.saku.data.entity.toEntity
import dev.nichidori.saku.domain.model.Category
import dev.nichidori.saku.domain.model.TrxType
import dev.nichidori.saku.domain.repo.CategoryRepository
import java.util.UUID
import kotlin.time.Clock

class DefaultCategoryRepository(
    private val db: AppDatabase,
) : CategoryRepository {
    override suspend fun addCategory(name: String, type: TrxType, icon: String?, parent: Category?) {
        db.useWriterConnection {
            it.immediateTransaction {
                val parentId = parent?.id
                if (parentId != null) {
                    val parentEntity = db.categoryDao().getById(parentId)
                        ?: throw NoSuchElementException("Parent category not found")
                    if (parentEntity.parentId != null) {
                        throw IllegalArgumentException("Nested categories beyond one level are not allowed")
                    }
                }

                val nextSortOrder = if (parentId == null) {
                    db.categoryDao().getMaxSortOrderForRoots() + 1
                } else {
                    db.categoryDao().getMaxSortOrderForParent(parentId) + 1
                }

                val category = Category(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    icon = icon,
                    parent = parent,
                    type = type,
                    createdAt = Clock.System.now(),
                    updatedAt = null,
                    sortOrder = nextSortOrder
                )
                db.categoryDao().insert(category.toEntity())
            }
        }
    }

    override suspend fun getCategoryById(id: String): Category? {
        return db.useReaderConnection {
            val entity = db.categoryDao().getById(id) ?: return@useReaderConnection null
            val parent = entity.parentId?.let {
                db.categoryDao().getById(it)?.toDomain()
            }
            entity.toDomain(parent)
        }
    }

    override suspend fun getAllCategories(): List<Category> {
        return db.useReaderConnection {
            val entities = db.categoryDao().getAll()
            val byId = entities.associateBy { it.id }

            entities.map { entity ->
                val parent = entity.parentId?.let { byId[it]?.toDomain(null) }
                entity.toDomain(parent)
            }
        }
    }

    override suspend fun getRootCategories(): List<Category> {
        return db.useReaderConnection {
            db.categoryDao().getRootCategories().map { it.toDomain(null) }
        }
    }

    override suspend fun getSubcategories(parentId: String): List<Category> {
        return db.useReaderConnection {
            val parent = db.categoryDao().getById(parentId)
                ?: throw NoSuchElementException("Parent not found")
            db.categoryDao().getSubcategories(parentId).map {
                it.toDomain(parent = parent.toDomain())
            }
        }
    }

    override suspend fun updateCategory(
        id: String,
        name: String,
        type: TrxType,
        icon: String?,
        parent: Category?
    ) {
        db.useWriterConnection {
            it.immediateTransaction {
                val parentId = parent?.id
                val parentEntity = if (parentId != null) {
                    if (parentId == id) {
                        throw IllegalArgumentException("Category cannot be its own parent")
                    }
                    val entity = db.categoryDao().getById(parentId)
                        ?: throw NoSuchElementException("Parent category not found")
                    if (entity.parentId != null) {
                        throw IllegalArgumentException("Nested categories beyond one level are not allowed")
                    }
                    entity
                } else {
                    null
                }

                val existing = db.categoryDao().getById(id)
                    ?: throw NoSuchElementException("Category not found")
                val oldParentId = existing.parentId
                val newParentId = parentEntity?.id

                val parent = parentEntity?.toDomain(parent = null)
                val category = existing.toDomain(parent = parent)

                val newSortOrder = if (oldParentId != newParentId) {
                    // Move to tail of new partition
                    val max = if (newParentId == null) {
                        db.categoryDao().getMaxSortOrderForRoots()
                    } else {
                        db.categoryDao().getMaxSortOrderForParent(newParentId)
                    }
                    max + 1
                } else {
                    existing.sortOrder
                }

                val updatedCategory = category.copy(
                    name = name,
                    type = type,
                    icon = icon,
                    parent = parent,
                    updatedAt = Clock.System.now(),
                    sortOrder = newSortOrder
                )
                db.categoryDao().update(updatedCategory.toEntity())

                // Compact old partition if parent changed
                if (oldParentId != newParentId) {
                    val oldSiblings = if (oldParentId == null) {
                        db.categoryDao().getRootCategories()
                    } else {
                        db.categoryDao().getSubcategories(oldParentId)
                    }
                    oldSiblings.sortedBy { it.sortOrder }.forEachIndexed { idx, entity ->
                        if (entity.sortOrder != idx) {
                            db.categoryDao().update(entity.copy(sortOrder = idx))
                        }
                    }
                }
            }
        }
    }

    override suspend fun deleteCategory(id: String) {
        db.useWriterConnection {
            it.immediateTransaction {
                val existing = db.categoryDao().getById(id) ?: throw NoSuchElementException("Category not found")
                val parentId = existing.parentId
                db.categoryDao().deleteById(id)
                // Compact remaining siblings to 0..n-1
                val siblings = if (parentId == null) {
                    db.categoryDao().getRootCategories()
                } else {
                    db.categoryDao().getSubcategories(parentId)
                }
                siblings.sortedBy { it.sortOrder }.forEachIndexed { idx, entity ->
                    if (entity.sortOrder != idx) {
                        db.categoryDao().update(entity.copy(sortOrder = idx))
                    }
                }
            }
        }
    }

    override suspend fun reorderCategories(parentId: String?, orderedIds: List<String>) {
        db.useWriterConnection {
            it.immediateTransaction {
                if (parentId != null) {
                    db.categoryDao().getById(parentId) ?: throw NoSuchElementException("Parent category not found")
                }
                val current = if (parentId == null) {
                    db.categoryDao().getRootCategories()
                } else {
                    db.categoryDao().getSubcategories(parentId)
                }
                if (current.size != orderedIds.size) {
                    throw IllegalArgumentException("orderedIds size must match partition size")
                }
                if (orderedIds.toSet().size != orderedIds.size) {
                    throw IllegalArgumentException("Duplicate ids in orderedIds")
                }
                val byId = current.associateBy { it.id }
                orderedIds.forEach { id ->
                    byId[id] ?: throw IllegalArgumentException("Category $id not in partition parentId=$parentId")
                }
                orderedIds.forEachIndexed { idx, id ->
                    val entity = byId[id]!!
                    if (entity.sortOrder != idx) {
                        db.categoryDao().update(entity.copy(sortOrder = idx))
                    }
                }
            }
        }
    }
}