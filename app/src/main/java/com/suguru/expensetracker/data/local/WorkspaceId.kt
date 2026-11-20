package com.suguru.expensetracker.data.local

import java.security.MessageDigest

/**
 * Internal financial-data owner. Firebase identifiers are never written to Room filenames.
 */
sealed interface WorkspaceId {
    val storageKey: String
    val databaseName: String
    val firebaseUid: String?

    data object Guest : WorkspaceId {
        override val storageKey: String = "guest"
        override val databaseName: String = "expense_tracker_guest.db"
        override val firebaseUid: String? = null
    }

    data class FirebaseAccount(val uid: String) : WorkspaceId {
        init {
            require(uid.isNotBlank())
        }

        private val digest: String = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        override val storageKey: String = "user_$digest"
        override val databaseName: String = "expense_tracker_$storageKey.db"
        override val firebaseUid: String = uid
    }

    companion object {
        fun forUid(uid: String?): WorkspaceId =
            uid?.takeIf(String::isNotBlank)?.let(::FirebaseAccount) ?: Guest

    }
}
