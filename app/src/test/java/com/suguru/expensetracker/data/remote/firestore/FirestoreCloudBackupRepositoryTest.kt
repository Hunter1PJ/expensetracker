package com.suguru.expensetracker.data.remote.firestore

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
@Config(sdk = [36])
class FirestoreCloudBackupRepositoryTest {

    private val chunker = CloudBackupPayloadChunker(maxChunkSizeBytes = 16)
    private val codec = CloudBackupPayloadCodec()
    private val contentFingerprint = "f".repeat(64)

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

        assertTrue(repository.observeManifests("uid_1").toList().isEmpty())
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

        assertTrue(repository.observeManifests("uid_bob").toList().isEmpty())
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

        val storedChunks = gateway.chunks["uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID]!!
        assertTrue(storedChunks.values.all { it.payload != null && it.payloadBase64 == null })

        val downloaded = repository.downloadChunks("uid_alice", FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID).getOrThrow()
        val reassembled = chunker.reassemble(downloaded, committed.payloadSha256).getOrThrow()
        assertArrayEquals(compressed, reassembled)
        val decompressed = codec.decompress(reassembled).getOrThrow().toString(Charsets.UTF_8)
        assertEquals(rawJson, decompressed)
    }

    @Test
    fun `failed upload preserves previous valid primary_restore_point backup`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 })
        assertTrue(repository.uploadBackup("uid_alice", m1, c1).isSuccess)

        gateway.failOnCreateChunkIndex = 0
        gateway.chunkCreateError = FirebaseFirestoreException("Network error", FirebaseFirestoreException.Code.UNAVAILABLE)

        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 })
        val failRes = repository.uploadBackup("uid_alice", m2, c2)
        assertTrue(failRes.isFailure)

        val latest = repository.getLatestCompleteManifest().getOrThrow()
        assertNotNull(latest)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, latest!!.snapshotId)
    }

    @Test
    fun `multiple rapid backups update the same single logical primary_restore_point`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (m1, c1) = buildSnapshot("snap_1", "uid_alice", ByteArray(16) { 1 })
        val res1 = repository.uploadBackup("uid_alice", m1, c1)
        assertTrue(res1.isSuccess)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, res1.getOrThrow().snapshotId)

        val (m2, c2) = buildSnapshot("snap_2", "uid_alice", ByteArray(16) { 2 })
        val res2 = repository.uploadBackup("uid_alice", m2, c2)
        assertTrue(res2.isSuccess)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, res2.getOrThrow().snapshotId)

        val userManifests = gateway.manifests.filter { it.key.first == "uid_alice" }
        assertEquals(1, userManifests.size)
        assertTrue(userManifests.containsKey("uid_alice" to FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID))
    }

    @Test
    fun `identical payload upload is idempotent and returns existing COMPLETE manifest`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (manifest1, chunked1) = buildSnapshot("snap_fixed", "uid_alice", ByteArray(20) { 1 })
        assertTrue(repository.uploadBackup("uid_alice", manifest1, chunked1).isSuccess)

        gateway.events.clear()
        val idempotentResult = repository.uploadBackup("uid_alice", manifest1, chunked1)
        assertTrue(idempotentResult.isSuccess)
        assertEquals(CloudBackupManifestStatus.COMPLETE, idempotentResult.getOrThrow().status)
        assertEquals(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID, idempotentResult.getOrThrow().snapshotId)
    }

    private fun buildSnapshot(
        snapshotId: String,
        uid: String,
        compressedBytes: ByteArray
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
            contentFingerprint = contentFingerprint
        )
        return manifest to chunked
    }

    private class FakeRemoteBackupGateway(
        var authenticatedUid: String?,
        var failOnCreateChunkIndex: Int? = null,
        var chunkCreateError: Exception = RuntimeException("Chunk upload failed"),
        var failMarkFailedError: Exception? = null
    ) : RemoteBackupGateway {

        val events = mutableListOf<String>()
        val manifests = linkedMapOf<Pair<String, String>, FirestoreBackupManifestDto>()
        val chunks = linkedMapOf<Pair<String, String>, MutableMap<String, FirestoreBackupChunkDto>>()
        private val observed = MutableStateFlow<List<CloudBackupManifest>>(emptyList())
        private var serverTimeSec = 1_763_200_100L

        override fun currentUserUid(): String? = authenticatedUid

        override fun observeManifests(uid: String, limit: Long): Flow<List<CloudBackupManifest>> =
            observed.map { list -> list.filter { it.uid == uid }.take(limit.toInt()) }

        override suspend fun getManifest(uid: String, snapshotId: String): FirestoreBackupManifestDto? =
            manifests[uid to snapshotId]

        override suspend fun listCompleteManifests(uid: String): List<FirestoreBackupManifestDto> =
            manifests.entries
                .filter { (key, dto) -> key.first == uid && dto.status == CloudBackupManifestStatus.COMPLETE.name }
                .map { it.value }

        override suspend fun createUploadingManifest(
            uid: String,
            snapshotId: String,
            payload: Map<String, Any>
        ) {
            events.add("createManifest:$snapshotId:UPLOADING")
            val now = Timestamp(serverTimeSec++, 0)
            manifests[uid to snapshotId] = FirestoreBackupManifestDto(
                snapshotId = snapshotId,
                userId = payload["userId"] as String,
                status = payload["status"] as String,
                createdAt = now,
                updatedAt = now,
                formatVersion = payload["formatVersion"] as Long,
                databaseSchemaVersion = payload["databaseSchemaVersion"] as Long,
                appVersion = payload["appVersion"] as String,
                chunkCount = payload["chunkCount"] as Long,
                compressedBytes = payload["compressedBytes"] as Long,
                uncompressedBytes = payload["uncompressedBytes"] as Long,
                payloadSha256 = payload["payloadSha256"] as String,
                contentFingerprint = payload["contentFingerprint"] as String
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
            events.add("markComplete:$snapshotId")
            val now = Timestamp(serverTimeSec++, 0)
            val existing = manifests[uid to snapshotId]
            if (existing != null) {
                manifests[uid to snapshotId] = existing.copy(
                    snapshotId = snapshotId,
                    status = CloudBackupManifestStatus.COMPLETE.name,
                    completedAt = now,
                    updatedAt = now
                )
            } else {
                manifests[uid to snapshotId] = FirestoreBackupManifestDto(
                    snapshotId = snapshotId,
                    userId = uid,
                    status = CloudBackupManifestStatus.COMPLETE.name,
                    createdAt = now,
                    updatedAt = now,
                    completedAt = now,
                    formatVersion = 1L,
                    databaseSchemaVersion = 4L,
                    appVersion = "1.5.0",
                    chunkCount = 1L,
                    compressedBytes = 16L,
                    uncompressedBytes = 128L,
                    payloadSha256 = "a".repeat(64),
                    contentFingerprint = "f".repeat(64)
                )
            }
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

        override suspend fun listChunks(uid: String, snapshotId: String): List<FirestoreBackupChunkDto> =
            chunks[uid to snapshotId]?.values?.sortedBy { it.index } ?: emptyList()

        override suspend fun deleteSnapshotDocuments(uid: String, snapshotId: String) {
            events.add("deleteSnapshot:$snapshotId")
            chunks.remove(uid to snapshotId)
            manifests.remove(uid to snapshotId)
            emitObserved()
        }

        private fun emitObserved() {
            observed.value = manifests.values.mapNotNull { it.toDomainOrNull() }
        }
    }
}
