package com.anderson.wifiprevent.data.local

import android.content.Context
import java.security.SecureRandom
import java.util.UUID

class InstallationStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    val token: String
        get() {
            val savedToken = preferences.getString(
                INSTALLATION_TOKEN,
                null
            )

            if (savedToken != null) {
                return savedToken
            }

            val generatedToken = generateToken()

            check(
                preferences.edit()
                    .putString(
                        INSTALLATION_TOKEN,
                        generatedToken
                    )
                    .commit()
            ) {
                "No se pudo guardar la identificación de esta instalación."
            }

            return generatedToken
        }

    fun requestIdFor(payload: String): String {
        val pendingId =
            preferences.getString(PENDING_REQUEST_ID, null)

        val pendingBody =
            preferences.getString(PENDING_REQUEST_BODY, null)

        val requestId =
            if (pendingId != null && pendingBody == payload) {
                pendingId
            } else {
                UUID.randomUUID().toString()
            }

        check(
            preferences.edit()
                .putString(PENDING_REQUEST_ID, requestId)
                .putString(PENDING_REQUEST_BODY, payload)
                .commit()
        ) {
            "No se pudo preparar el envío."
        }

        return requestId
    }

    fun clearPendingRequest() {
        check(
            preferences.edit()
                .remove(PENDING_REQUEST_ID)
                .remove(PENDING_REQUEST_BODY)
                .commit()
        ) {
            "El envío fue confirmado, pero no se pudo limpiar " +
                    "su estado local."
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_SIZE_BYTES)

        SecureRandom().nextBytes(bytes)

        return bytes.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private companion object {
        const val PREFERENCES_NAME =
            "backend_installation"

        const val INSTALLATION_TOKEN =
            "token"

        const val PENDING_REQUEST_ID =
            "pending_id"

        const val PENDING_REQUEST_BODY =
            "pending_body"

        const val TOKEN_SIZE_BYTES = 32
    }
}