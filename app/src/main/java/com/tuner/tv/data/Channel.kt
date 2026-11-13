package com.tuner.tv.data

data class Channel(
    val id: Int,
    val number: Int,
    val name: String,
    val url: String,
    val logo: String?,
    val group: String,
    val tvgId: String?,
)
