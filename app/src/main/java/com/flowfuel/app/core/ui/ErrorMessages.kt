package com.flowfuel.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flowfuel.app.R
import com.flowfuel.app.core.domain.AppError

@Composable
fun AppError.userMessage(): String = when (this) {
    AppError.Network -> stringResource(R.string.error_network)
    AppError.Unauthorized -> stringResource(R.string.error_unauthorized)
    is AppError.Api -> when (code) {
        "ACCOUNT_NOT_ACTIVATED" -> stringResource(R.string.error_account_not_activated)
        "INVALID_CREDENTIALS", "BAD_CREDENTIALS" -> stringResource(R.string.error_invalid_credentials)
        "AUTH_BAD_CREDENTIALS" -> stringResource(R.string.error_password_current_wrong)
        "EMAIL_ALREADY_EXISTS", "EMAIL_TAKEN", "EMAIL_ALREADY_REGISTERED" -> stringResource(R.string.error_email_taken)
        "FORBIDDEN_OPERATION" -> stringResource(R.string.error_forbidden)
        "RESOURCE_NOT_FOUND" -> stringResource(R.string.error_not_found)
        "AUTH_ACTIVATION_INVALID" -> stringResource(R.string.error_activation_invalid)
        "AUTH_RESET_INVALID" -> stringResource(R.string.error_reset_invalid)
        "BUSINESS_RULE_VIOLATED" -> message ?: stringResource(R.string.error_business_rule)
        "VALIDATION_FAILED" -> message ?: stringResource(R.string.error_validation_failed)
        "CONFLICT" -> message ?: stringResource(R.string.error_conflict)
        "RATE_LIMIT_EXCEEDED" -> stringResource(R.string.error_rate_limited)
        else -> stringResource(R.string.error_unknown)
    }
    is AppError.Unknown -> stringResource(R.string.error_unknown)
}
