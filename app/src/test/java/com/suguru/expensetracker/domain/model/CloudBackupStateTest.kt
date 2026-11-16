package com.suguru.expensetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudBackupStateTest {

    @Test
    fun `1 - empty initial state is valid`() {
        val state = CloudBackupState()

        assertEquals(CloudBackupState.EMPTY, state)
        assertTrue(state.isEmpty)
        assertNull(state.lastSuccessfulBackupAtEpochMillis)
        assertNull(state.lastSuccessfulSnapshotId)
        assertNull(state.pendingSnapshotId)
        assertNull(state.pendingSinceEpochMillis)
        assertNull(state.lastAttemptAtEpochMillis)
        assertNull(state.lastFailureAtEpochMillis)
        assertNull(state.lastFailureCode)
    }

    @Test
    fun `2 - successful backup metadata is represented correctly`() {
        val state = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_763_200_000_000L,
            lastSuccessfulSnapshotId = "snap_20261115_080000",
            lastAttemptAtEpochMillis = 1_763_200_000_000L
        )

        assertFalse(state.isEmpty)
        assertEquals(1_763_200_000_000L, state.lastSuccessfulBackupAtEpochMillis)
        assertEquals("snap_20261115_080000", state.lastSuccessfulSnapshotId)
        assertEquals(1_763_200_000_000L, state.lastAttemptAtEpochMillis)

        // Epoch 0 is valid
        val epochState = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 0L,
            lastSuccessfulSnapshotId = "550e8400-e29b-41d4-a716-446655440000"
        )
        assertEquals(0L, epochState.lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun `3 - pending snapshot metadata is represented correctly`() {
        val state = CloudBackupState(
            pendingSnapshotId = "snap_pending_01",
            pendingSinceEpochMillis = 1_763_200_050_000L,
            lastAttemptAtEpochMillis = 1_763_200_060_000L
        )

        assertFalse(state.isEmpty)
        assertEquals("snap_pending_01", state.pendingSnapshotId)
        assertEquals(1_763_200_050_000L, state.pendingSinceEpochMillis)
        assertEquals(1_763_200_060_000L, state.lastAttemptAtEpochMillis)
    }

    @Test
    fun `4 - failure metadata is represented correctly alongside success and pending metadata`() {
        val state = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_763_100_000_000L,
            lastSuccessfulSnapshotId = "snap_prev_01",
            pendingSnapshotId = "snap_retry_02",
            pendingSinceEpochMillis = 1_763_200_000_000L,
            lastAttemptAtEpochMillis = 1_763_200_010_000L,
            lastFailureAtEpochMillis = 1_763_200_010_000L,
            lastFailureCode = "NETWORK_UNAVAILABLE"
        )

        assertEquals(1_763_200_010_000L, state.lastFailureAtEpochMillis)
        assertEquals("NETWORK_UNAVAILABLE", state.lastFailureCode)

        // Also supports lowercase/kebab-case machine-readable codes
        val kebabCodeState = state.copy(lastFailureCode = "permission-denied")
        assertEquals("permission-denied", kebabCodeState.lastFailureCode)
    }

    @Test
    fun `5 - negative timestamps are rejected`() {
        for (negativeTimestamp in listOf(-1L, -1_000L, Long.MIN_VALUE)) {
            assertThrowsIllegalArgument("lastSuccessfulBackupAtEpochMillis=$negativeTimestamp") {
                CloudBackupState(lastSuccessfulBackupAtEpochMillis = negativeTimestamp)
            }
            assertThrowsIllegalArgument("pendingSinceEpochMillis=$negativeTimestamp") {
                CloudBackupState(
                    pendingSnapshotId = "snap_1",
                    pendingSinceEpochMillis = negativeTimestamp
                )
            }
            assertThrowsIllegalArgument("lastAttemptAtEpochMillis=$negativeTimestamp") {
                CloudBackupState(lastAttemptAtEpochMillis = negativeTimestamp)
            }
            assertThrowsIllegalArgument("lastFailureAtEpochMillis=$negativeTimestamp") {
                CloudBackupState(
                    lastFailureAtEpochMillis = negativeTimestamp,
                    lastFailureCode = "TIMEOUT"
                )
            }
        }
    }

    @Test
    fun `6 - blank or malformed snapshot IDs are rejected`() {
        val invalidSnapshotIds = listOf(
            "",
            " ",
            "   ",
            "\t\n",
            " snap_1",
            "snap_1 ",
            "snap 1",
            "snap\n1",
            "uid/snap_1",
            ".",
            "..",
            "a".repeat(CloudBackupState.MAX_SNAPSHOT_ID_LENGTH + 1)
        )

        for (invalidId in invalidSnapshotIds) {
            assertThrowsIllegalArgument("lastSuccessfulSnapshotId='$invalidId'") {
                CloudBackupState(
                    lastSuccessfulBackupAtEpochMillis = 1_000L,
                    lastSuccessfulSnapshotId = invalidId
                )
            }
            assertThrowsIllegalArgument("pendingSnapshotId='$invalidId'") {
                CloudBackupState(
                    pendingSnapshotId = invalidId,
                    pendingSinceEpochMillis = 1_000L
                )
            }
        }
    }

    @Test
    fun `7 - blank or malformed failure codes are rejected`() {
        val invalidFailureCodes = listOf(
            "",
            " ",
            "   ",
            "\t\n",
            " NETWORK_ERROR",
            "NETWORK_ERROR ",
            "Failed to upload backup",
            "java.io.IOException: Connection reset",
            "https://firestore.googleapis.com/v1/projects/foo",
            "ERR\nat Foo.kt:10",
            "_LEADING_UNDERSCORE",
            "-LEADING_HYPHEN",
            "123_NUMERIC_START",
            "user@example.com",
            "A".repeat(CloudBackupState.MAX_FAILURE_CODE_LENGTH + 1)
        )

        for (invalidCode in invalidFailureCodes) {
            assertThrowsIllegalArgument("lastFailureCode='$invalidCode'") {
                CloudBackupState(
                    lastFailureAtEpochMillis = 1_000L,
                    lastFailureCode = invalidCode
                )
            }
        }
    }

    @Test
    fun `8 - inconsistent pending fields are rejected`() {
        assertThrowsIllegalArgument("pendingSnapshotId without pendingSinceEpochMillis") {
            CloudBackupState(pendingSnapshotId = "snap_pending_01")
        }
        assertThrowsIllegalArgument("pendingSinceEpochMillis without pendingSnapshotId") {
            CloudBackupState(pendingSinceEpochMillis = 1_763_200_000_000L)
        }
    }

    @Test
    fun `9 - inconsistent failure fields are rejected`() {
        assertThrowsIllegalArgument("lastFailureAtEpochMillis without lastFailureCode") {
            CloudBackupState(lastFailureAtEpochMillis = 1_763_200_000_000L)
        }
        assertThrowsIllegalArgument("lastFailureCode without lastFailureAtEpochMillis") {
            CloudBackupState(lastFailureCode = "NETWORK_UNAVAILABLE")
        }
    }

    @Test
    fun `10 - state equality and deterministic behavior`() {
        val first = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_000L,
            lastSuccessfulSnapshotId = "snap_1",
            pendingSnapshotId = "snap_2",
            pendingSinceEpochMillis = 2_000L,
            lastAttemptAtEpochMillis = 3_000L,
            lastFailureAtEpochMillis = 3_000L,
            lastFailureCode = "TIMEOUT"
        )
        val second = first.copy()
        val different = first.copy(lastFailureCode = "AUTH_REQUIRED")

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.toString(), second.toString())
        assertNotEquals(first, different)
    }

    private fun assertThrowsIllegalArgument(label: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException for $label")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }
}
