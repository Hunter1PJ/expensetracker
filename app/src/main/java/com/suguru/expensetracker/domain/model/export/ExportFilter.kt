package com.suguru.expensetracker.domain.model.export

import com.suguru.expensetracker.domain.model.TransactionType
import java.time.LocalDate

/**
 * Immutable filter model specifying the parameters for exporting financial records.
 *
 * Empty sets represent "ALL" (no constraint).
 */
data class ExportFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val transactionTypes: Set<TransactionType> = emptySet(),
    val currencyCodes: Set<String> = emptySet()
) {
    val isFiltered: Boolean
        get() = startDate != null ||
                endDate != null ||
                accountIds.isNotEmpty() ||
                categoryIds.isNotEmpty() ||
                transactionTypes.isNotEmpty() ||
                currencyCodes.isNotEmpty()
}
