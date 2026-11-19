package com.suguru.expensetracker.data.remote.firestore

import android.app.Application
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestoreException
import com.suguru.expensetracker.domain.error.CloudBackupRemoteException
import com.suguru.expensetracker.domain.model.CloudBackupChunkedPayload
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import com.suguru.expensetracker.domain.model.CloudBackupPayloadChunker
import com.suguru.expensetracker.domain.model.CloudBackupPayloadCodec
import com.suguru.expensetracker.domain.model.CloudBackupState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FirestoreCloudBackupRepositoryTest {

    private val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 16)
    private val codec = CloudBackupPayloadCodec()
    private val contentFingerprint = "f".repeat(64)
    private val contentFingerprintAlt = "e".repeat(64)

    @Test
    fun `unauthenticated operations fail with typed Unauthenticated error`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = null)
        val repository = FirestoreCloudBackupRepository(gateway, chunker)
        val (manifest, chunked) = buildSnapshot("snap_unauth", "uid_1", byteArrayOf(1, 2, 3, 4))

        val uploadResult = repository.uploadBackup("uid_1", manifest, chunked)
        assertTrue(uploadResult.isFailure)
        assertTrue(uploadResult.exceptionOrNull() is CloudBackupRemoteException.Unauthenticated)

        val latestResult = repository.getLatestCompleteManifest()
        assertTrue(latestResult.isFailure)
        assertTrue(latestResult.exceptionOrNull() is CloudBackupRemoteException.Unauthenticated)

        val downloadResult = repository.downloadChunks("uid_1", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID)
        assertTrue(downloadResult.isFailure)
        assertTrue(downloadResult.exceptionOrNull() is CloudBackupRemoteException.Unauthenticated)

        val deleteResult = repository.deleteSnapshot("uid_1", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID)
        assertTrue(deleteResult.isFailure)
        assertTrue(deleteResult.exceptionOrNull() is CloudBackupRemoteException.Unauthenticated)

        val observeResult = runCatching { repository.observeManifests("uid_1").toList() }
        assertTrue(observeResult.exceptionOrNull() is CloudBackupRemoteException.Unauthenticated)
        assertTrue(gateway.events.isEmpty())
    }

    @Test
    fun `UID ownership mismatch is rejected across upload, download, delete, and observe`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (bobManifest, bobChunked) = buildSnapshot("snap_bob", "uid_bob", byteArrayOf(5, 6, 7))
        val crossCallerUpload = repository.uploadBackup("uid_bob", bobManifest, bobChunked)
        assertTrue(crossCallerUpload.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)

        val spoofedManifestUpload = repository.uploadBackup("uid_alice", bobManifest, bobChunked)
        assertTrue(spoofedManifestUpload.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)

        val crossDownload = repository.downloadChunks("uid_bob", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID)
        assertTrue(crossDownload.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)

        val crossDelete = repository.deleteSnapshot("uid_bob", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID)
        assertTrue(crossDelete.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)

        val observeResult = runCatching { repository.observeManifests("uid_bob").toList() }
        assertTrue(observeResult.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)
    }

    @Test
    fun `upload creates single logical primary_restore_point and preserves binary payload`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val rawJson = "{\"transactions\":[{\"id\":1,\"amount\":4200,\"note\":\"Coffee\"}]}"
        val compressed = codec.compress(rawJson.toByteArray(Charsets.UTF_8)).getOrThrow()
        val (manifest, chunked) = buildSnapshot("snap_order_01", "uid_alice", compressed)
        assertTrue(chunked.totalChunkCount > 1)

        val result = repository.uploadBackup("uid_alice", manifest, chunked)
        assertTrue(result.isSuccess)
        val committed = result.getOrThrow()
        assertEquals(CloudBackupManifestStatus.COMPLETE, committed.status)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, committed.snapshotId)
        assertNotNull(committed.completedAt)

        val primaryDto = gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]
        assertNotNull(primaryDto)
        val genId = requireNotNull(primaryDto!!.generationId)
        assertTrue(genId.startsWith("gen_"))

        val storedChunks = gateway.chunks["uid_alice" to genId]!!
        assertTrue(storedChunks.values.all { it.payload != null && it.payloadBase64 == null })
        val lastChunkUploadIndex = gateway.events.indexOfLast { it.startsWith("createChunk:$genId:") }
        val generationCompleteIndex = gateway.events.indexOf("markComplete:$genId")
        val primaryPromotionIndex = gateway.events.indexOf("createPrimaryManifest:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}")
        assertTrue(lastChunkUploadIndex >= 0 && lastChunkUploadIndex < generationCompleteIndex)
        assertTrue(generationCompleteIndex < primaryPromotionIndex)

        val downloaded = repository.downloadChunks("uid_alice", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID).getOrThrow()
        val reassembled = chunker.reassemble(downloaded, committed.payloadSha256).getOrThrow()
        assertArrayEquals(compressed, reassembled)
        val decompressed = codec.decompress(reassembled).getOrThrow().toString(Charsets.UTF_8)
        assertEquals(rawJson, decompressed)
    }

    @Test
    fun `incomplete primary is not reported as no cloud backup`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)
        val (manifest, chunked) = buildSnapshot("snap_incomplete", "uid_alice", byteArrayOf(1, 2, 3))
        assertTrue(repository.uploadBackup("uid_alice", manifest, chunked).isSuccess)

        val primaryKey = "uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID
        gateway.manifests[primaryKey] = gateway.manifests.getValue(primaryKey).copy(
            status = CloudBackupManifestStatus.UPLOADING.name,
            completedAt = null
        )

        val result = repository.getLatestCompleteManifest()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CloudBackupRemoteException.SnapshotNotComplete)
    }

    @Test
    fun `temporary manifest lookup failure is not reported as a missing backup`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)
        val (manifest, chunked) = buildSnapshot("snap_available", "uid_alice", byteArrayOf(4, 5, 6))
        assertTrue(repository.uploadBackup("uid_alice", manifest, chunked).isSuccess)
        gateway.getManifestError = FirebaseNetworkException("temporarily unavailable")

        val result = repository.getLatestCompleteManifest()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CloudBackupRemoteException.RemoteOperationFailed)
        assertEquals(
            "NETWORK_UNAVAILABLE",
            (result.exceptionOrNull() as CloudBackupRemoteException.RemoteOperationFailed).failureCode
        )
    }

    @Test
    fun `corrupt uploaded generation is rejected before completion and keeps previous primary`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)
        val (firstManifest, firstChunks) = buildSnapshot(
            "snap_verified_1",
            "uid_alice",
            ByteArray(24) { 1 },
            fingerprint = contentFingerprint
        )
        assertTrue(repository.uploadBackup("uid_alice", firstManifest, firstChunks).isSuccess)
        val oldPrimary = gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]!!

        gateway.corruptUploadedChunksOnRead = true
        val eventStart = gateway.events.size
        val (secondManifest, secondChunks) = buildSnapshot(
            "snap_verified_2",
            "uid_alice",
            ByteArray(24) { 2 },
            fingerprint = "b".repeat(64)
        )
        val failed = repository.uploadBackup("uid_alice", secondManifest, secondChunks)

        assertTrue(failed.isFailure)
        assertEquals(
            oldPrimary.generationId,
            gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]?.generationId
        )
        val secondGenerationId = gateway.events.drop(eventStart)
            .last { it.startsWith("createManifest:gen_") }
            .substringAfter("createManifest:")
            .substringBefore(":")
        assertFalse(gateway.events.drop(eventStart).contains("markComplete:$secondGenerationId"))
        assertEquals(
            oldPrimary.contentFingerprint,
            repository.getLatestCompleteManifest().getOrThrow()?.contentFingerprint
        )
    }

    @Test
    fun `failed upload preserves previous valid primary_restore_point backup`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        assertTrue(repository.uploadBackup("uid_alice", m1, c1).isSuccess)

        gateway.failOnCreateChunkIndex = 0
        gateway.chunkCreateError = FirebaseFirestoreException("Network error", FirebaseFirestoreException.Code.UNAVAILABLE)

        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 }, fingerprint = contentFingerprintAlt)
        val failRes = repository.uploadBackup("uid_alice", m2, c2)
        assertTrue(failRes.isFailure)

        val latest = repository.getLatestCompleteManifest().getOrThrow()
        assertNotNull(latest)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, latest!!.snapshotId)

        // Previous backup remains downloadable
        val downloaded = repository.downloadChunks("uid_alice", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID).getOrThrow()
        assertNotNull(downloaded)
    }

    @Test
    fun `multiple rapid backups update the same single logical primary_restore_point`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        val res1 = repository.uploadBackup("uid_alice", m1, c1)
        assertTrue(res1.isSuccess)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, res1.getOrThrow().snapshotId)

        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 }, fingerprint = contentFingerprintAlt)
        val res2 = repository.uploadBackup("uid_alice", m2, c2)
        assertTrue(res2.isSuccess)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, res2.getOrThrow().snapshotId)

        val userPrimary = gateway.manifests.filter { it.key.first == "uid_alice" && it.key.second == FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID }
        assertEquals(1, userPrimary.size)
    }

    @Test
    fun `identical payload upload is idempotent and returns existing COMPLETE manifest`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (manifest1, chunked1) = buildSnapshot("snap_fixed", "uid_alice", ByteArray(20) { 1 }, fingerprint = contentFingerprint)
        assertTrue(repository.uploadBackup("uid_alice", manifest1, chunked1).isSuccess)

        gateway.events.clear()
        val idempotentResult = repository.uploadBackup("uid_alice", manifest1, chunked1)
        assertTrue(idempotentResult.isSuccess)
        assertEquals(CloudBackupManifestStatus.COMPLETE, idempotentResult.getOrThrow().status)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, idempotentResult.getOrThrow().snapshotId)
    }

    @Test
    fun `failure during promotion preserves previous backup`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        assertTrue(repository.uploadBackup("uid_alice", m1, c1).isSuccess)

        gateway.failOnPrimaryPromotion = true
        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 }, fingerprint = contentFingerprintAlt)
        val res = repository.uploadBackup("uid_alice", m2, c2)
        assertTrue(res.isFailure)

        // Previous valid backup still latest and downloadable
        val latest = repository.getLatestCompleteManifest().getOrThrow()
        assertNotNull(latest)
        val downloaded = repository.downloadChunks("uid_alice", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID).getOrThrow()
        assertNotNull(downloaded)
    }

    @Test
    fun `no duplicate visible restore points in observeManifests`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        assertTrue(repository.uploadBackup("uid_alice", m1, c1).isSuccess)

        val list = repository.observeManifests("uid_alice").first()
        assertEquals(1, list.size)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, list[0].snapshotId)
    }

    @Test
    fun `no redundant primary COMPLETE write on first backup or promotion`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        val res1 = repository.uploadBackup("uid_alice", m1, c1)
        assertTrue(res1.isSuccess)

        // On first backup: single createPrimaryManifest write, no updatePrimaryManifest, no markComplete for primary
        assertTrue(gateway.events.contains("createPrimaryManifest:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}"))
        assertFalse(gateway.events.contains("updatePrimaryManifest:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}"))
        assertFalse(gateway.events.contains("markComplete:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}"))

        gateway.events.clear()
        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 }, fingerprint = contentFingerprintAlt)
        val res2 = repository.uploadBackup("uid_alice", m2, c2)
        assertTrue(res2.isSuccess)

        // On subsequent backup: single updatePrimaryManifest write, no createPrimaryManifest, no markComplete for primary
        assertTrue(gateway.events.contains("updatePrimaryManifest:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}"))
        assertFalse(gateway.events.contains("createPrimaryManifest:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}"))
        assertFalse(gateway.events.contains("markComplete:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}"))
    }

    @Test
    fun `primary timestamps remain valid and creation time is preserved across promotions`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        val res1 = repository.uploadBackup("uid_alice", m1, c1).getOrThrow()
        assertNotNull(res1.completedAt)
        assertFalse(res1.completedAt!!.isBefore(res1.createdAt))

        val primaryDto1 = gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]!!
        val createdTimestamp1 = primaryDto1.createdAt!!

        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 }, fingerprint = contentFingerprintAlt)
        val res2 = repository.uploadBackup("uid_alice", m2, c2).getOrThrow()
        assertNotNull(res2.completedAt)
        assertFalse(res2.completedAt!!.isBefore(res2.createdAt))

        val primaryDto2 = gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]!!
        // Creation time must be preserved
        assertEquals(createdTimestamp1, primaryDto2.createdAt)
        assertEquals(res1.createdAt, res2.createdAt)
        val res1Completed = res1.completedAt
        val res2Completed = res2.completedAt
        assertNotNull(res1Completed)
        assertNotNull(res2Completed)
        assertTrue(res2Completed!!.isAfter(res1Completed))
    }

    @Test
    fun `primary references only a COMPLETE generation`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        val res1 = repository.uploadBackup("uid_alice", m1, c1).getOrThrow()

        val primaryDto = gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]!!
        val generationId = requireNotNull(primaryDto.generationId)
        val genDto = gateway.manifests["uid_alice" to generationId]
        assertNotNull(genDto)
        assertEquals(CloudBackupManifestStatus.COMPLETE.name, genDto!!.status)
        assertNotNull(genDto.completedAt)
        assertEquals(res1.payloadSha256, genDto.payloadSha256)
        assertEquals(res1.contentFingerprint, genDto.contentFingerprint)
    }

    @Test
    fun `generation cleanup does not delete the active generation`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 }, fingerprint = contentFingerprint)
        repository.uploadBackup("uid_alice", m1, c1).getOrThrow()
        val genId1 = gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]!!.generationId!!
        assertTrue(gateway.manifests.containsKey("uid_alice" to genId1))
        assertTrue(gateway.chunks.containsKey("uid_alice" to genId1))

        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 }, fingerprint = contentFingerprintAlt)
        repository.uploadBackup("uid_alice", m2, c2).getOrThrow()
        val genId2 = gateway.manifests["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]!!.generationId!!

        // Active generation genId2 is preserved with its chunks
        assertTrue(gateway.manifests.containsKey("uid_alice" to genId2))
        assertTrue(gateway.chunks.containsKey("uid_alice" to genId2))

        // Obsolete generation genId1 was cleaned up
        assertFalse(gateway.manifests.containsKey("uid_alice" to genId1))
        assertFalse(gateway.chunks.containsKey("uid_alice" to genId1))
    }

    private fun buildSnapshot(
        snapshotId: String,
        uid: String,
        compressedBytes: ByteArray,
        fingerprint: String = contentFingerprint
    ): Pair<CloudBackupManifest, CloudBackupChunkedPayload> {
        val chunked = chunker.chunk(compressedBytes)
        val manifest = CloudBackupManifest(
            snapshotId = snapshotId,
            uid = uid,
            status = CloudBackupManifestStatus.UPLOADING,
            createdAt = Instant.ofEpochSecond(1_763_200_000L),
            completedAt = null,
            formatVersion = 1,
            databaseSchemaVersion = 4,
            appVersion = "1.5.0",
            chunkCount = chunked.totalChunkCount,
            compressedBytes = compressedBytes.size.toLong(),
            uncompressedBytes = maxOf(compressedBytes.size.toLong(), 128L),
            payloadSha256 = chunked.payloadSha256,
            contentFingerprint = fingerprint
        )
        return manifest to chunked
    }

    private class FakeRemoteBackupGateway(
        var authenticatedUid: String?,
        var failOnCreateChunkIndex: Int? = null,
        var chunkCreateError: Exception = RuntimeException("Chunk upload failed"),
        var failMarkFailedError: Exception? = null,
        var failOnMarkPrimaryComplete: Boolean = false,
        var failOnPrimaryPromotion: Boolean = false,
        var corruptUploadedChunksOnRead: Boolean = false,
        var getManifestError: Exception? = null
    ) : RemoteBackupGateway {

        val events = mutableListOf<String>()
        val manifests = linkedMapOf<Pair<String, String>, FirestoreBackupManifestDto>()
        val chunks = linkedMapOf<Pair<String, String>, MutableMap<String, FirestoreBackupChunkDto>>()
        private val observed = MutableStateFlow<List<CloudBackupManifest>>(emptyList())
        private var serverTimeSec = 1_763_200_100L

        override fun currentUserUid(): String? = authenticatedUid

        override fun observeManifests(uid: String): Flow<List<CloudBackupManifest>> =
            observed.map { list -> list.filter { it.uid == uid && it.snapshotId == FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID } }

        override suspend fun getManifest(uid: String, snapshotId: String): FirestoreBackupManifestDto? {
            getManifestError?.let { throw it }
            return manifests[uid to snapshotId]
        }

        override suspend fun listCompleteManifests(uid: String): List<FirestoreBackupManifestDto> =
            manifests.entries
                .filter { (key, dto) -> key.first == uid && dto.status == CloudBackupManifestStatus.COMPLETE.name }
                .map { it.value }

        override suspend fun listAllManifests(uid: String): List<FirestoreBackupManifestDto> =
            manifests.entries
                .filter { (key, _) -> key.first == uid }
                .map { it.value }

        override suspend fun createUploadingManifest(
            uid: String,
            snapshotId: String,
            payload: Map<String, Any>
        ) {
            events.add("createManifest:$snapshotId:${payload["status"] ?: "UPLOADING"}")
            val now = Timestamp(serverTimeSec++, 0)
            val status = (payload["status"] as? String) ?: CloudBackupManifestStatus.UPLOADING.name
            val completedAt = if (status == CloudBackupManifestStatus.COMPLETE.name) Timestamp(serverTimeSec++, 0) else null
            manifests[uid to snapshotId] = FirestoreBackupManifestDto(
                snapshotId = snapshotId,
                userId = payload["userId"] as String,
                status = status,
                createdAt = now,
                updatedAt = now,
                completedAt = completedAt,
                formatVersion = payload["formatVersion"] as Long,
                databaseSchemaVersion = payload["databaseSchemaVersion"] as Long,
                appVersion = payload["appVersion"] as String,
                chunkCount = payload["chunkCount"] as Long,
                compressedBytes = payload["compressedBytes"] as Long,
                uncompressedBytes = payload["uncompressedBytes"] as Long,
                payloadSha256 = payload["payloadSha256"] as String,
                contentFingerprint = payload["contentFingerprint"] as String,
                generationId = payload["generationId"] as? String
            )
            emitObserved()
        }

        override suspend fun resetManifestToUploading(
            uid: String,
            snapshotId: String,
            payload: Map<String, Any>
        ) {
            events.add("resetToUploading:$snapshotId")
            val existing = manifests[uid to snapshotId] ?: error("Missing manifest")
            manifests[uid to snapshotId] = existing.copy(
                status = CloudBackupManifestStatus.UPLOADING.name,
                updatedAt = Timestamp(serverTimeSec++, 0),
                failedAt = null,
                failureCode = null
            )
            emitObserved()
        }

        override suspend fun markManifestComplete(
            uid: String,
            snapshotId: String,
            payload: Map<String, Any>
        ) {
            if (failOnMarkPrimaryComplete && snapshotId == FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID) {
                throw RuntimeException("Promotion failed")
            }
            events.add("markComplete:$snapshotId")
            val now = Timestamp(serverTimeSec++, 0)
            val existing = manifests[uid to snapshotId]
            val formatVersion = (payload["formatVersion"] as? Long) ?: existing?.formatVersion ?: 1L
            val databaseSchemaVersion = (payload["databaseSchemaVersion"] as? Long) ?: existing?.databaseSchemaVersion ?: 4L
            val appVersion = (payload["appVersion"] as? String) ?: existing?.appVersion ?: "1.5.0"
            val chunkCount = (payload["chunkCount"] as? Long) ?: existing?.chunkCount ?: 1L
            val compressedBytes = (payload["compressedBytes"] as? Long) ?: existing?.compressedBytes ?: 0L
            val uncompressedBytes = (payload["uncompressedBytes"] as? Long) ?: existing?.uncompressedBytes ?: 0L
            val payloadSha256 = (payload["payloadSha256"] as? String) ?: existing?.payloadSha256 ?: ""
            val contentFingerprint = (payload["contentFingerprint"] as? String) ?: existing?.contentFingerprint ?: ""
            val generationId = (payload["generationId"] as? String) ?: existing?.generationId

            val createdAt = existing?.createdAt ?: now
            var completedAt = (payload["completedAt"] as? Timestamp) ?: now
            if (completedAt.seconds < createdAt.seconds) {
                completedAt = createdAt
            }

            manifests[uid to snapshotId] = FirestoreBackupManifestDto(
                snapshotId = snapshotId,
                userId = uid,
                status = CloudBackupManifestStatus.COMPLETE.name,
                createdAt = createdAt,
                updatedAt = now,
                completedAt = completedAt,
                formatVersion = formatVersion,
                databaseSchemaVersion = databaseSchemaVersion,
                appVersion = appVersion,
                chunkCount = chunkCount,
                compressedBytes = compressedBytes,
                uncompressedBytes = uncompressedBytes,
                payloadSha256 = payloadSha256,
                contentFingerprint = contentFingerprint,
                generationId = generationId
            )
            emitObserved()
        }

        override suspend fun markManifestFailed(
            uid: String,
            snapshotId: String,
            payload: Map<String, Any>
        ) {
            failMarkFailedError?.let { throw it }
            events.add("markFailed:$snapshotId:${payload["failureCode"]}")
            val existing = manifests[uid to snapshotId] ?: error("Missing manifest")
            val now = Timestamp(serverTimeSec++, 0)
            manifests[uid to snapshotId] = existing.copy(
                status = CloudBackupManifestStatus.FAILED.name,
                failedAt = now,
                failureCode = payload["failureCode"] as String,
                updatedAt = now
            )
            emitObserved()
        }

        override suspend fun getChunk(
            uid: String,
            snapshotId: String,
            chunkId: String
        ): FirestoreBackupChunkDto? = chunks[uid to snapshotId]?.get(chunkId)

        override suspend fun createChunk(
            uid: String,
            snapshotId: String,
            chunkId: String,
            payload: Map<String, Any>
        ) {
            val idx = (payload["index"] as Long).toInt()
            if (failOnCreateChunkIndex == idx) {
                throw chunkCreateError
            }
            events.add("createChunk:$snapshotId:$chunkId")
            val bucket = chunks.getOrPut(uid to snapshotId) { linkedMapOf() }
            bucket[chunkId] = FirestoreBackupChunkDto(
                id = chunkId,
                userId = payload["userId"] as String,
                snapshotId = payload["snapshotId"] as String,
                index = payload["index"] as Long,
                totalCount = payload["totalCount"] as Long,
                sha256 = payload["sha256"] as String,
                byteCount = payload["byteCount"] as Long,
                payload = payload["payload"] as Blob,
                createdAt = Timestamp(serverTimeSec++, 0)
            )
        }

        override suspend fun deleteChunk(uid: String, snapshotId: String, chunkId: String) {
            events.add("deleteChunk:$snapshotId:$chunkId")
            chunks[uid to snapshotId]?.remove(chunkId)
        }

        override suspend fun listChunks(uid: String, snapshotId: String): List<FirestoreBackupChunkDto> {
            val result = chunks[uid to snapshotId]?.values?.sortedBy { it.index } ?: emptyList()
            if (!corruptUploadedChunksOnRead || result.isEmpty()) return result
            return result.mapIndexed { index, dto ->
                if (index == 0) dto.copy(sha256 = "0".repeat(64)) else dto
            }
        }

        override suspend fun deleteSnapshotDocuments(uid: String, snapshotId: String) {
            events.add("deleteSnapshot:$snapshotId")
            chunks.remove(uid to snapshotId)
            manifests.remove(uid to snapshotId)
            emitObserved()
        }

        private fun emitObserved() {
            observed.value = manifests.values
                .filter { it.status == CloudBackupManifestStatus.COMPLETE.name && it.snapshotId == FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID }
                .mapNotNull { it.toDomainOrNull() }
        }

        override suspend fun createPrimaryManifest(uid: String, payload: Map<String, Any>) {
            if (failOnPrimaryPromotion) {
                throw RuntimeException("Primary create failed")
            }
            events.add("createPrimaryManifest:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}")
            val now = Timestamp(serverTimeSec++, 0)
            val createdAt = (payload["createdAt"] as? Timestamp) ?: now
            val completedAt = (payload["completedAt"] as? Timestamp) ?: now
            manifests[uid to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID] = FirestoreBackupManifestDto(
                snapshotId = FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID,
                userId = payload["userId"] as String,
                status = CloudBackupManifestStatus.COMPLETE.name,
                createdAt = createdAt,
                updatedAt = now,
                completedAt = completedAt,
                formatVersion = payload["formatVersion"] as Long,
                databaseSchemaVersion = payload["databaseSchemaVersion"] as Long,
                appVersion = payload["appVersion"] as String,
                chunkCount = payload["chunkCount"] as Long,
                compressedBytes = payload["compressedBytes"] as Long,
                uncompressedBytes = payload["uncompressedBytes"] as Long,
                payloadSha256 = payload["payloadSha256"] as String,
                contentFingerprint = payload["contentFingerprint"] as String,
                generationId = payload["generationId"] as? String
            )
            emitObserved()
        }

        override suspend fun updatePrimaryManifest(uid: String, payload: Map<String, Any>) {
            if (failOnPrimaryPromotion) {
                throw RuntimeException("Primary update failed")
            }
            events.add("updatePrimaryManifest:${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}")
            val existing = manifests[uid to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]
                ?: error("Existing primary manifest missing for update")
            val now = Timestamp(serverTimeSec++, 0)
            val completedAt = (payload["completedAt"] as? Timestamp) ?: now
            manifests[uid to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID] = existing.copy(
                status = CloudBackupManifestStatus.COMPLETE.name,
                updatedAt = now,
                completedAt = completedAt,
                formatVersion = (payload["formatVersion"] as? Long) ?: existing.formatVersion,
                databaseSchemaVersion = (payload["databaseSchemaVersion"] as? Long) ?: existing.databaseSchemaVersion,
                appVersion = (payload["appVersion"] as? String) ?: existing.appVersion,
                chunkCount = (payload["chunkCount"] as? Long) ?: existing.chunkCount,
                compressedBytes = (payload["compressedBytes"] as? Long) ?: existing.compressedBytes,
                uncompressedBytes = (payload["uncompressedBytes"] as? Long) ?: existing.uncompressedBytes,
                payloadSha256 = (payload["payloadSha256"] as? String) ?: existing.payloadSha256,
                contentFingerprint = (payload["contentFingerprint"] as? String) ?: existing.contentFingerprint,
                generationId = (payload["generationId"] as? String) ?: existing.generationId
            )
            emitObserved()
        }
    }
}
