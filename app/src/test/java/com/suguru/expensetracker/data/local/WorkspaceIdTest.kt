package com.suguru.expensetracker.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WorkspaceIdTest {
    @Test
    fun guestHasDedicatedStableDatabaseName() {
        assertEquals("expense_tracker_guest.db", WorkspaceId.Guest.databaseName)
        assertEquals("guest", WorkspaceId.forUid(null).storageKey)
    }

    @Test
    fun firebaseWorkspaceNamesAreUidSpecificWithoutContainingRawUid() {
        val uidA = "firebase-user-a"
        val uidB = "firebase-user-b"
        val workspaceA = WorkspaceId.FirebaseAccount(uidA)
        val workspaceB = WorkspaceId.FirebaseAccount(uidB)

        assertNotEquals(workspaceA.databaseName, workspaceB.databaseName)
        assertTrue(workspaceA.databaseName.startsWith("expense_tracker_user_"))
        assertFalse(workspaceA.databaseName.contains(uidA))
        assertFalse(workspaceB.databaseName.contains(uidB))
        assertEquals(uidA, workspaceA.firebaseUid)
    }

    @Test
    fun legacyUidHashAndWorkspaceDatabaseNameAreStableAcrossSupportedLocales() {
        val originalLocale = Locale.getDefault()
        try {
            val uid = "firebase-user-a"
            val names = listOf("en", "uz", "ru").map { language ->
                Locale.setDefault(Locale.forLanguageTag(language))
                val legacyDigest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(uid.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                "expense_tracker_user_$legacyDigest.db"
            }

            assertEquals(1, names.distinct().size)
            assertEquals(names.first(), WorkspaceId.FirebaseAccount(uid).databaseName)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
