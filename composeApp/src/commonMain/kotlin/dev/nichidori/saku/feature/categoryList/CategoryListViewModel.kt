package dev.nichidori.saku.feature.categoryList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nichidori.saku.core.util.log
import dev.nichidori.saku.domain.model.Category
import dev.nichidori.saku.domain.model.TrxType
import dev.nichidori.saku.domain.repo.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias CategoryByParent = Pair<Category, List<Category>>

data class CategoryListUiState(
    val isLoading: Boolean = false,
    val selectedType: TrxType = TrxType.Expense,
    val incomesByParent: List<CategoryByParent> = emptyList(),
    val expensesByParent: List<CategoryByParent> = emptyList(),
)

class CategoryListViewModel(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    fun onReorder(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val snapshot = _uiState.value
        val isIncome = snapshot.selectedType == TrxType.Income
        val sourceList = if (isIncome) snapshot.incomesByParent else snapshot.expensesByParent
        if (fromIndex !in sourceList.indices || toIndex !in sourceList.indices) return

        val reordered = sourceList.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }

        _uiState.update {
            if (isIncome) it.copy(incomesByParent = reordered)
            else it.copy(expensesByParent = reordered)
        }

        viewModelScope.launch {
            try {
                val newIncomes = if (isIncome) reordered else snapshot.incomesByParent
                val newExpenses = if (isIncome) snapshot.expensesByParent else reordered

                // Build global ordering preserving the interleaving of Income/Expense positions
                // based on sortOrder, but with the reordered sequence for the active type.
                val allRootsSorted = (snapshot.incomesByParent.map { it.first } +
                    snapshot.expensesByParent.map { it.first })
                    .sortedBy { it.sortOrder }

                val incomeQueue = ArrayDeque(newIncomes.map { it.first.id })
                val expenseQueue = ArrayDeque(newExpenses.map { it.first.id })

                val globalOrderedIds = allRootsSorted.map { cat ->
                    if (cat.type == TrxType.Income) incomeQueue.removeFirst()
                    else expenseQueue.removeFirst()
                }

                categoryRepository.reorderCategories(null, globalOrderedIds)
            } catch (e: Exception) {
                this@CategoryListViewModel.log(e)
                load()
            }
        }
    }

    fun onReorderChild(parentId: String, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val snapshot = _uiState.value
        val isIncomeParent = snapshot.incomesByParent.any { it.first.id == parentId }
        val isExpenseParent = snapshot.expensesByParent.any { it.first.id == parentId }
        if (!isIncomeParent && !isExpenseParent) return

        val sourceList = if (isIncomeParent) snapshot.incomesByParent else snapshot.expensesByParent
        val parentIndex = sourceList.indexOfFirst { it.first.id == parentId }
        if (parentIndex == -1) return
        val (parent, children) = sourceList[parentIndex]
        if (fromIndex !in children.indices || toIndex !in children.indices) return

        val reorderedChildren = children.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }

        _uiState.update {
            if (isIncomeParent) {
                val updated = it.incomesByParent.toMutableList().apply {
                    set(parentIndex, parent to reorderedChildren)
                }
                it.copy(incomesByParent = updated)
            } else {
                val updated = it.expensesByParent.toMutableList().apply {
                    set(parentIndex, parent to reorderedChildren)
                }
                it.copy(expensesByParent = updated)
            }
        }

        viewModelScope.launch {
            try {
                categoryRepository.reorderCategories(parentId, reorderedChildren.map { it.id })
            } catch (e: Exception) {
                this@CategoryListViewModel.log(e)
                load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(isLoading = true)
                }
                val categories = categoryRepository.getAllCategories()
                val (parents, children) = categories.partition { it.parent == null }
                val childrenByParentId = children.groupBy { it.parent?.id }
                val incomesByParent = parents
                    .filter { it.type == TrxType.Income }
                    .associateWith { childrenByParentId[it.id].orEmpty() }
                    .toList()
                val expensesByParent = parents
                    .filter { it.type == TrxType.Expense }
                    .associateWith { childrenByParentId[it.id].orEmpty() }
                    .toList()
                _uiState.update {
                    it.copy(
                        incomesByParent = incomesByParent,
                        expensesByParent = expensesByParent,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                this@CategoryListViewModel.log(e)
                _uiState.update {
                    it.copy(isLoading = false)
                }
            }
        }
    }

    fun onSelectedTypeChange(type: TrxType) {
        _uiState.update {
            it.copy(selectedType = type)
        }
    }
}
