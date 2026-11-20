package com.suguru.expensetracker.presentation.util

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.suguru.expensetracker.data.repository.DataStoreAutoBackupPreferenceStore
import com.suguru.expensetracker.data.repository.DataStoreCloudBackupFingerprintStore
import com.suguru.expensetracker.data.repository.DataStoreCloudBackupStateStore
import com.suguru.expensetracker.data.local.WorkspaceId
import com.suguru.expensetracker.domain.model.CloudBackupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = Application::class)
class AppLanguageManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun explicitLanguagePersistsAcrossManagerReads() {
        listOf("uz", "en", "ru").forEach { language ->
            AppLanguageManager.setLanguage(context, language)
            assertEquals(language, AppLanguageManager.selectedLanguageTag(context))
        }
    }

    @Test
    fun selectedLocaleLoadsLocalizedNavigationResources() {
        AppLanguageManager.setLanguage(context, "uz")
        assertEquals("Bosh sahifa", AppLanguageManager.wrap(context).getString(com.suguru.expensetracker.R.string.nav_home))
        AppLanguageManager.setLanguage(context, "ru")
        assertEquals("Главная", AppLanguageManager.wrap(context).getString(com.suguru.expensetracker.R.string.nav_home))
    }

    @Test
    fun builtInExpenseAndIncomeLabelsResolveForAllSupportedLocales() {
        val expectedByLanguage = mapOf(
            "en" to listOf("Groceries", "Salary", "Other"),
            "uz" to listOf("Oziq-ovqat mahsulotlari", "Maosh", "Boshqa"),
            "ru" to listOf("Продукты", "Зарплата", "Другое"),
        )
        val categoryResources = listOf(
            com.suguru.expensetracker.R.string.category_groceries,
            com.suguru.expensetracker.R.string.category_salary,
            com.suguru.expensetracker.R.string.category_other,
        )

        expectedByLanguage.forEach { (language, expectedLabels) ->
            AppLanguageManager.setLanguage(context, language)
            val localized = AppLanguageManager.wrap(context)
            assertEquals(expectedLabels, categoryResources.map(localized::getString))
        }
    }

    @Test
    fun changingWorkspacePreferencesDoesNotChangeInstallationLanguage() {
        AppLanguageManager.setLanguage(context, "ru")
        listOf("guest", "firebase-user-a", "firebase-user-b").forEach { workspace ->
            context.getSharedPreferences("workspace_$workspace", Context.MODE_PRIVATE)
                .edit()
                .putString("active_workspace", workspace)
                .commit()
            assertEquals("ru", AppLanguageManager.selectedLanguageTag(context))
        }
    }

    @Test
    fun changingLanguageAndReopeningProcessStoresKeepsUidCloudStateWorkspaceAndPreference() = runBlocking {
        val uid = "firebase-user-a"
        val backupFingerprint = "a".repeat(64)
        val state = CloudBackupState(
            lastSuccessfulBackupAtEpochMillis = 1_763_200_050_000L,
            lastSuccessfulSnapshotId = "primary_restore_point",
            lastSuccessfulRestoreGenerationId = "gen_existing",
            lastSuccessfulRestoreFingerprint = backupFingerprint
        )
        val dataFile = File(context.filesDir, "language_restart_${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var dataStore = PreferenceDataStoreFactory.create(scope = scope) { dataFile }

        try {
            DataStoreCloudBackupStateStore(dataStore).setState(uid, state)
            DataStoreCloudBackupFingerprintStore(dataStore).setLastUploadedFingerprint(uid, backupFingerprint)
            DataStoreAutoBackupPreferenceStore(dataStore).setAutoBackupEnabled(uid, true)
            val expectedDatabaseName = WorkspaceId.FirebaseAccount(uid).databaseName

            listOf("en", "ru", "uz").forEach { language ->
                AppLanguageManager.setLanguage(context, language)
                assertEquals(language, AppLanguageManager.selectedLanguageTag(context))
                assertEquals(expectedDatabaseName, WorkspaceId.FirebaseAccount(uid).databaseName)

                // Reopen the persisted stores to model a process restart for each selected locale.
                scope.cancel()
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                dataStore = PreferenceDataStoreFactory.create(scope = scope) { dataFile }
                assertEquals(state, DataStoreCloudBackupStateStore(dataStore).getState(uid))
                assertEquals(
                    backupFingerprint,
                    DataStoreCloudBackupFingerprintStore(dataStore).getLastUploadedFingerprint(uid)
                )
                assertEquals(true, DataStoreAutoBackupPreferenceStore(dataStore).isAutoBackupEnabled(uid))
            }
        } finally {
            scope.cancel()
            dataFile.delete()
        }
    }

    @Test
    fun unsupportedSavedLanguageFallsBackToSupportedDeviceOrEnglish() {
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
            .edit()
            .putString("language_tag", "fr")
            .commit()
        val deviceLanguage = context.resources.configuration.locales[0]?.language
        val expected = deviceLanguage?.takeIf { it in setOf("uz", "en", "ru") } ?: "en"
        assertEquals(expected, AppLanguageManager.selectedLanguageTag(context))
    }
}
