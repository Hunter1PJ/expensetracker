package com.suguru.expensetracker.data.worker

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCloudBackupWorkerTest {

    @Test
    fun automaticRestoreRequestWaitsForConnectedNetworkAndIsUidScoped() {
        val request = AutomaticCloudBackupWorker.createAutomaticRestoreWorkRequest("uid_a")

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.input.getBoolean("key_automatic_restore", false))
        assertEquals("uid_a", request.workSpec.input.getString(AutomaticCloudBackupWorker.KEY_UID))
        assertEquals(
            AutomaticCloudBackupWorker.automaticRestoreWorkName("uid_a"),
            AutomaticCloudBackupWorker.automaticRestoreWorkName("uid_a")
        )
        assertTrue(
            AutomaticCloudBackupWorker.automaticRestoreWorkName("uid_a") !=
                AutomaticCloudBackupWorker.automaticRestoreWorkName("uid_b")
        )
    }

    @Test
    fun automaticRestoreExecutionRequiresCurrentAccountAndEnabledPreference() {
        assertFalse(AutomaticCloudBackupWorker.shouldRunAutomaticRestore(null, true))
        assertFalse(AutomaticCloudBackupWorker.shouldRunAutomaticRestore("", true))
        assertFalse(AutomaticCloudBackupWorker.shouldRunAutomaticRestore("current_uid", false))
        assertTrue(AutomaticCloudBackupWorker.shouldRunAutomaticRestore("current_uid", true))
    }
}
