package com.suguru.expensetracker.presentation.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A presentation message that stays locale-independent until the UI renders it.
 */
data class UiMessage(
    @get:StringRes val resourceId: Int,
    val arguments: List<Any> = emptyList()
)

fun uiMessage(@StringRes resourceId: Int, vararg arguments: Any): UiMessage =
    UiMessage(resourceId, arguments.toList())

@Composable
fun UiMessage.localized(): String {
    val localizedArguments = mutableListOf<Any>()
    for (argument in arguments) {
        localizedArguments += if (argument is UiMessage) argument.localized() else argument
    }
    return stringResource(resourceId, *localizedArguments.toTypedArray())
}
