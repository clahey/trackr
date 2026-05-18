package com.trackr.app.ui

sealed class SaveResult {
    data object Idle : SaveResult()
    data object Success : SaveResult()
    data class ValidationError(val field: String) : SaveResult()
}
