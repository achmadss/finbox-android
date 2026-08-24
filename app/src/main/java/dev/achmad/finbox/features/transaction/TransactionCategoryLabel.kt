package dev.achmad.finbox.features.transaction

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.achmad.data.model.TransactionCategory
import dev.achmad.finbox.R

/**
 * What each category is called on screen.
 *
 * The enum name is the stored value and never reaches the user: it has to be
 * stable across builds, and a label has to be translatable. Kept exhaustive on
 * purpose — adding a category should not compile until it has a name.
 */
@get:StringRes
val TransactionCategory.labelRes: Int
    get() = when (this) {
        TransactionCategory.INCOME -> R.string.category_income
        TransactionCategory.FOOD -> R.string.category_food
        TransactionCategory.GROCERIES -> R.string.category_groceries
        TransactionCategory.SHOPPING -> R.string.category_shopping
        TransactionCategory.TRANSPORTATION -> R.string.category_transportation
        TransactionCategory.BILLS -> R.string.category_bills
        TransactionCategory.HOUSING -> R.string.category_housing
        TransactionCategory.ENTERTAINMENT -> R.string.category_entertainment
        TransactionCategory.HEALTH -> R.string.category_health
        TransactionCategory.EDUCATION -> R.string.category_education
        TransactionCategory.TRAVEL -> R.string.category_travel
        TransactionCategory.PERSONAL_CARE -> R.string.category_personal_care
        TransactionCategory.FINANCIAL -> R.string.category_financial
        TransactionCategory.TRANSFER -> R.string.category_transfer
        TransactionCategory.FEES -> R.string.category_fees
        TransactionCategory.OTHER -> R.string.category_other
        TransactionCategory.UNKNOWN -> R.string.category_unknown
    }

/**
 * A category's name, or "Uncategorized" for a row nothing has decided yet.
 *
 * Null and [TransactionCategory.UNKNOWN] read differently on purpose: null is
 * waiting its turn, UNKNOWN is the answer that there was nothing to go on.
 */
@Composable
fun categoryLabel(category: TransactionCategory?): String =
    stringResource(category?.labelRes ?: R.string.category_uncategorized)

/** The categories a person can file something under. */
val pickableCategories: List<TransactionCategory> =
    TransactionCategory.entries - TransactionCategory.UNKNOWN
