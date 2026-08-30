package dev.achmad.finbox.features.rules

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.CategoryRule
import dev.achmad.data.model.Signature
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.normalizeForSignature
import dev.achmad.data.repository.CategoryRuleRepository
import dev.achmad.data.repository.matches
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * The user's rules, and the one place they are edited as rules.
 *
 * A rule exists because it was declared — filing a group declares one — but
 * declarations are the user's to change and drop. Editing a rule is the only
 * place "replace existing rows" is offered, because that is the one that
 * touches decisions already made and is asked for before anything runs.
 */
class CategoryRulesScreenModel(
    private val repository: CategoryRuleRepository = inject(),
) : ScreenModel {

    private val _rules = MutableStateFlow<List<CategoryRule>>(emptyList())
    val rules: StateFlow<List<CategoryRule>> = _rules.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        screenModelScope.launch { _rules.value = repository.all() }
    }

    fun delete(rule: CategoryRule) {
        screenModelScope.launch {
            repository.delete(rule.id)
            refresh()
        }
    }

    /**
     * Creates or replaces a declaration.
     *
     * [applyToExisting] is the destructive half and is chosen explicitly by
     * the dialog, which also shows the count. Declaring replaces whatever rule
     * exists for that merchant, so the replacement writes the newest answer.
     */
    fun save(
        merchant: String,
        direction: TransactionDirection?,
        category: TransactionCategory,
        applyToExisting: Boolean,
    ) {
        screenModelScope.launch {
            repository.declare(merchant, direction, category)
            if (applyToExisting) {
                repository.all()
                    .firstOrNull { it.matches(Signature(normalizeForSignature(merchant), direction)) }
                    ?.let { repository.replaceExisting(it) }
            }
            refresh()
        }
    }

    /** How many rows [save] with [applyToExisting] would write. Null when the merchant is blank. */
    suspend fun countReplace(merchant: String, direction: TransactionDirection?): Int? =
        withContext(Dispatchers.IO) {
            val normalized = normalizeForSignature(merchant) ?: return@withContext 0
            runCatching { repository.countReplace(normalized, direction) }.getOrNull()
        }
}
