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
