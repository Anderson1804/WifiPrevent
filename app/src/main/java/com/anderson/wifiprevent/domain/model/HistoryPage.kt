package com.anderson.wifiprevent.domain.model

data class HistoryPage(
    val entries: List<HistoryEntry>,
    val nextBefore: String?
)