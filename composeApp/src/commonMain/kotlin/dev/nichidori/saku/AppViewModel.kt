package dev.nichidori.saku

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nichidori.saku.core.util.log
import dev.nichidori.saku.domain.model.Trx
import dev.nichidori.saku.domain.repo.AccountRepository
import dev.nichidori.saku.domain.repo.InstallmentRepository
import dev.nichidori.saku.domain.repo.TrxRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")

data class AppUiState(
    val darkTheme: Boolean? = null,
)

class AppViewModel(
    private val dataStore: DataStore<Preferences>,
    private val trxRepository: TrxRepository,
    private val accountRepository: AccountRepository,
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data
                .map { it[DARK_THEME_KEY] ?: false }.first()
                .let { dark ->
                    _uiState.update {
                        it.copy(darkTheme = dark)
                    }
                }
        }
        viewModelScope.launch {
            try {
                installmentRepository.processDueInstallments()
            } catch (e: Exception) {
                this@AppViewModel.log(e)
            }
        }
        viewModelScope.launch {
            try {
                accountRepository.ensureCurrentMonthNetWorth()
            } catch (e: Exception) {
                this@AppViewModel.log(e)
            }
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            try {
                val newDarkTheme = !(_uiState.value.darkTheme ?: false)
                dataStore.edit { prefs ->
                    prefs[DARK_THEME_KEY] = newDarkTheme
                }
                _uiState.update {
                    it.copy(darkTheme = newDarkTheme)
                }
            } catch (e: Exception) {
                this@AppViewModel.log(e)
            }
        }
    }

    fun restoreTrx(trx: Trx) {
        viewModelScope.launch {
            try {
                trxRepository.addTrx(
                    type = trx.type,
                    transactionAt = trx.transactionAt,
                    amount = trx.amount,
                    description = trx.description,
                    sourceAccount = trx.sourceAccount,
                    targetAccount = (trx as? Trx.Transfer)?.targetAccount,
                    category = trx.category,
                )
            } catch (e: Exception) {
                this@AppViewModel.log(e)
            }
        }
    }
}
