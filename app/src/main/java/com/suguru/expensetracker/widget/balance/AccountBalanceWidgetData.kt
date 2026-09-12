package com.suguru.expensetracker.widget.balance

enum class WidgetDataState {
    LOADING,
    READY,
    ACCOUNT_UNAVAILABLE,
    CONFIGURATION_REQUIRED,
    ERROR
}

data class AccountBalanceWidgetData(
    val accountId: Long,
    val accountName: String,
    val formattedBalance: String,
    val currencyCode: String,
    val privacyMode: Boolean,
    val state: WidgetDataState
) {
    companion object {
        fun loading(accountId: Long = 0L) = AccountBalanceWidgetData(
            accountId = accountId,
            accountName = "",
            formattedBalance = "",
            currencyCode = "",
            privacyMode = false,
            state = WidgetDataState.LOADING
        )

        fun configurationRequired() = AccountBalanceWidgetData(
            accountId = 0L,
            accountName = "",
            formattedBalance = "",
            currencyCode = "",
            privacyMode = false,
            state = WidgetDataState.CONFIGURATION_REQUIRED
        )

        fun unavailable(accountId: Long) = AccountBalanceWidgetData(
            accountId = accountId,
            accountName = "",
            formattedBalance = "",
            currencyCode = "",
            privacyMode = false,
            state = WidgetDataState.ACCOUNT_UNAVAILABLE
        )

        fun error(accountId: Long) = AccountBalanceWidgetData(
            accountId = accountId,
            accountName = "",
            formattedBalance = "",
            currencyCode = "",
            privacyMode = false,
            state = WidgetDataState.ERROR
        )
    }
}
