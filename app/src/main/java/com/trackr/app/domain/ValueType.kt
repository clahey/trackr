package com.trackr.app.domain

sealed class ValueType {
    data object None : ValueType()
    data object Scale : ValueType()
    data object Boolean : ValueType()
    data object Number : ValueType()
    data object Text : ValueType()
    data object Duration : ValueType()
    data class Unknown(val raw: String) : ValueType()
}
