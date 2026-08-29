package dev.achmad.finbox.features.categorize

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.Signature
import dev.achmad.data.model.SignatureGroup
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.repository.CategoryRuleRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The filing screen: groups of receipts that read the same, biggest first,
 * each fileable in one tap.
 *
 * No category was parsed from a receipt, and nothing here asks anyone else
 * for one. The user is the only party who knows whether the GoPay rows are
 * food or transport, and filing IS the declaration: declare one group and the
 * rule covers that merchant's existing and future rows.
 */
class CategoryGroupsScreenModel(
    private val transactions: TransactionRepository = inject(),
    private val rules: CategoryRuleRepository = inject(),
) : ScreenModel {

    private val _groups = MutableStateFlow<List<SignatureGroup>>(emptyList())
    val groups: StateFlow<List<SignatureGroup>> = _groups.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        screenModelScope.launch {
            _groups.value = transactions.fileableGroups()
        }
    }

    /**
     * Files one group under [category] and declares the rule for it.
     *
     * [TransactionRepository.setCategoryByUser] makes the filing durable:
     * `category_source = 'USER'` for every row, which outranks any AI answer
     * and is what the re-parse skips. Declaring the rule is what makes the
     * *next* row with that merchant file itself; a merchant-less group has no
     * name to declare, so filing it covers what is on screen and nothing more.
     */
    fun file(group: SignatureGroup, category: TransactionCategory) {
        screenModelScope.launch {
            transactions.setCategoryByUser(group.rows.map { it.id }, category)
            group.signature.merchant?.let { merchant ->
                // The filing itself IS the declaration. Declaring closes the
                // ring: the next email with this merchant is already filed by
                // the update path, and any open rows the group screen has not
                // shown yet (it only shows fileable ones) are closed here.
                rules.declare(merchant, group.signature.direction, category)
                transactions.applyRules(rules.all())
            }
            refresh()
        }
    }
}
