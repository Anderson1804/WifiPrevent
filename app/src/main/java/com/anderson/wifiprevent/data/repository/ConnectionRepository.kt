package com.anderson.wifiprevent.data.repository

import com.anderson.wifiprevent.data.remote.BackendClient
import com.anderson.wifiprevent.domain.model.ConnectionReceipt
import com.anderson.wifiprevent.domain.model.HistoryPage
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.domain.model.AnalysisReceipt
import com.anderson.wifiprevent.domain.model.AnalysisHistoryPage
import com.anderson.wifiprevent.domain.model.AnalysisHistorySummary
import com.anderson.wifiprevent.data.local.StoredAnalysisSession

class ConnectionRepository(
    private val backendClient: BackendClient
) {
    suspend fun saveConnection(
        snapshot: WifiSnapshot
    ): ConnectionReceipt {
        return backendClient.send(snapshot)
    }

    suspend fun getHistory(
        before: String? = null
    ): HistoryPage {
        return backendClient.history(before)
    }

    suspend fun saveAnalysis(session: StoredAnalysisSession): AnalysisReceipt {
        return backendClient.sendAnalysis(session)
    }

    suspend fun getAnalysisHistory(before: String? = null): AnalysisHistoryPage {
        return backendClient.analysisHistory(before)
    }

    suspend fun getAnalysisHistorySummary(): AnalysisHistorySummary {
        return backendClient.analysisHistorySummary()
    }
}
