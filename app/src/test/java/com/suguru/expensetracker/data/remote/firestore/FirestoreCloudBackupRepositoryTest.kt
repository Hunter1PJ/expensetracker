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

        val downloadResult = repository.downloadChunks("uid_1", "snap_unauth")
        assertTrue(downloadResult.isFailure)
        assertTrue(downloadResult.exceptionOrNull() is CloudBackupRemoteException.Unauthenticated)

        val deleteResult = repository.deleteSnapshot("uid_1", "snap_unauth")
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

        // Even if caller passes uid_alice, manifest.uid = uid_bob must be rejected
        val spoofedManifestUpload = repository.uploadBackup("uid_alice", bobManifest, bobChunked)
        assertTrue(spoofedManifestUpload.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)

        val crossDownload = repository.downloadChunks("uid_bob", "snap_bob")
        assertTrue(crossDownload.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)

        val crossDelete = repository.deleteSnapshot("uid_bob", "snap_bob")
        assertTrue(crossDelete.exceptionOrNull() is CloudBackupRemoteException.UidMismatch)

        assertTrue(repository.observeManifests("uid_bob").toList().isEmpty())
    }

    @Test
    fun `upload follows strict lifecycle order UPLOADING to chunks to COMPLETE and preserves binary payload`() = runTest {
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
        assertNotNull(committed.completedAt)

        val expectedOrder = mutableListOf("createManifest:snap_order_01:UPLOADING")
        for (i in 0 until chunked.totalChunkCount) {
            expectedOrder.add("createChunk:snap_order_01:chunk_$i")
        }
        expectedOrder.add("markComplete:snap_order_01")
        assertEquals(expectedOrder, gateway.events)

        // Verify binary Blob storage (no Base64 expansion) and end-to-end download + decode
        val storedChunks = gateway.chunks["uid_alice" to "snap_order_01"]!!
        assertTrue(storedChunks.values.all { it.payload != null && it.payloadBase64 == null })

        val downloaded = repository.downloadChunks("uid_alice", "snap_order_01").getOrThrow()
        val reassembled = chunker.reassemble(downloaded, committed.payloadSha256).getOrThrow()
        assertArrayEquals(compressed, reassembled)
        val decompressed = codec.decompress(reassembled).getOrThrow().toString(Charsets.UTF_8)
        assertEquals(rawJson, decompressed)
    }

    @Test
    fun `failed chunk upload marks manifest FAILED with bounded code and preserves original failure if FAILED write also fails`() = runTest {
        val networkError = FirebaseFirestoreException(
            "Transient network error at https://example.com/secret-token",
            FirebaseFirestoreException.Code.UNAVAILABLE
        )
        val gateway = FakeRemoteBackupGateway(
            authenticatedUid = "uid_alice",
            failOnCreateChunkIndex = 1,
            chunkCreateError = networkError
        )
        val repository = FirestoreCloudBackupRepository(gateway, chunker)
        val (manifest, chunked) = buildSnapshot("snap_fail_01", "uid_alice", ByteArray(40) { it.toByte() })

        val result = repository.uploadBackup("uid_alice", manifest, chunked)
        assertTrue(result.isFailure)
        val failure = result.exceptionOrNull() as CloudBackupRemoteException
        assertEquals("NETWORK_UNAVAILABLE", failure.failureCode)

        val storedManifest = gateway.manifests["uid_alice" to "snap_fail_01"]!!
        assertEquals("FAILED", storedManifest.status)
        assertEquals("NETWORK_UNAVAILABLE", storedManifest.failureCode)
        assertTrue(CloudBackupState.isValidFailureCode(storedManifest.failureCode!!))
        assertNull(storedManifest.completedAt)
        assertFalse(gateway.events.any { it.startsWith("markComplete:") })

        // Now test when marking FAILED also fails: original failure must still be preserved
        val gatewayFailBoth = FakeRemoteBackupGateway(
            authenticatedUid = "uid_alice",
            failOnCreateChunkIndex = 0,
            chunkCreateError = FirebaseFirestoreException("Quota", FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED),
            failMarkFailedError = FirebaseNetworkException("Offline")
        )
        val repoFailBoth = FirestoreCloudBackupRepository(gatewayFailBoth, chunker)
        val (manifest2, chunked2) = buildSnapshot("snap_fail_02", "uid_alice", ByteArray(20) { it.toByte() })
        val result2 = repoFailBoth.uploadBackup("uid_alice", manifest2, chunked2)
        assertTrue(result2.isFailure)
        val failure2 = result2.exceptionOrNull() as CloudBackupRemoteException
        assertEquals("QUOTA_EXCEEDED", failure2.failureCode)
    }

    @Test
    fun `existing COMPLETE snapshot cannot be silently replaced with a different payload`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        val (manifest1, chunked1) = buildSnapshot("snap_fixed", "uid_alice", ByteArray(20) { 1 })
        assertTrue(repository.uploadBackup("uid_alice", manifest1, chunked1).isSuccess)

        // Different payload reusing same snapshotId must fail with SnapshotConflict
        val (manifest2, chunked2) = buildSnapshot("snap_fixed", "uid_alice", ByteArray(20) { 2 })
        val conflictResult = repository.uploadBackup("uid_alice", manifest2, chunked2)
        assertTrue(conflictResult.isFailure)
        assertTrue(conflictResult.exceptionOrNull() is CloudBackupRemoteException.SnapshotConflict)

        // Identical payload retry is idempotent and returns existing COMPLETE manifest without overwriting
        gateway.events.clear()
        val idempotentResult = repository.uploadBackup("uid_alice", manifest1, chunked1)
        assertTrue(idempotentResult.isSuccess)
        assertEquals(CloudBackupManifestStatus.COMPLETE, idempotentResult.getOrThrow().status)
        assertTrue(gateway.events.isEmpty())
    }

    @Test
    fun `retry of FAILED or incomplete UPLOADING snapshot reuses existing valid chunks and completes safely`() = runTest {
        val gateway = FakeRemoteBackupGateway(
            authenticatedUid = "uid_alice",
            failOnCreateChunkIndex = 1,
            chunkCreateError = FirebaseFirestoreException("Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        )
        val repository = FirestoreCloudBackupRepository(gateway, chunker)
        val payload = ByteArray(40) { (it * 3).toByte() } // 3 chunks of 16, 16, 8 bytes
        val (manifest, chunked) = buildSnapshot("snap_retry_01", "uid_alice", payload)

        // First attempt fails on chunk_1 -> chunk_0 exists, manifest is FAILED
        assertTrue(repository.uploadBackup("uid_alice", manifest, chunked).isFailure)
        assertEquals("FAILED", gateway.manifests["uid_alice" to "snap_retry_01"]?.status)
        assertTrue(gateway.chunks["uid_alice" to "snap_retry_01"]!!.containsKey("chunk_0"))

        // Clear transient failure and retry with the same snapshotId
        gateway.failOnCreateChunkIndex = null
        gateway.events.clear()

        val retryResult = repository.uploadBackup("uid_alice", manifest, chunked)
        assertTrue(retryResult.isSuccess)
        assertEquals(CloudBackupManifestStatus.COMPLETE, retryResult.getOrThrow().status)
        assertTrue(gateway.events.contains("resetToUploading:snap_retry_01"))
        // chunk_0 was already valid so it should not be recreated; chunk_1 & chunk_2 should be created
        assertFalse(gateway.events.contains("createChunk:snap_retry_01:chunk_0"))
        assertTrue(gateway.events.contains("createChunk:snap_retry_01:chunk_1"))
        assertTrue(gateway.events.contains("createChunk:snap_retry_01:chunk_2"))
        assertEquals("markComplete:snap_retry_01", gateway.events.last())

        val downloaded = repository.downloadChunks("uid_alice", "snap_retry_01").getOrThrow()
        assertArrayEquals(payload, chunker.reassemble(downloaded, manifest.payloadSha256).getOrThrow())
    }

    @Test
    fun `getLatestCompleteManifest ignores UPLOADING and FAILED and returns newest COMPLETE or null`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)

        // Empty case -> normal Result.success(null)
        assertNull(repository.getLatestCompleteManifest().getOrThrow())

        val baseTime = 1_763_200_000L
        gateway.manifests["uid_alice" to "snap_uploading"] = dtoManifest(
            snapshotId = "snap_uploading",
            uid = "uid_alice",
            status = "UPLOADING",
            createdSec = baseTime + 300,
            completedSec = null
        )
        gateway.manifests["uid_alice" to "snap_failed"] = dtoManifest(
            snapshotId = "snap_failed",
            uid = "uid_alice",
            status = "FAILED",
            createdSec = baseTime + 400,
            completedSec = null,
            failedSec = baseTime + 405,
            failureCode = "UPLOAD_FAILED"
        )
        // Only UPLOADING/FAILED exist -> still returns null
        assertNull(repository.getLatestCompleteManifest().getOrThrow())

        gateway.manifests["uid_alice" to "snap_complete_older"] = dtoManifest(
            snapshotId = "snap_complete_older",
            uid = "uid_alice",
            status = "COMPLETE",
            createdSec = baseTime + 100,
            completedSec = baseTime + 110
        )
        gateway.manifests["uid_alice" to "snap_complete_newest"] = dtoManifest(
            snapshotId = "snap_complete_newest",
            uid = "uid_alice",
            status = "COMPLETE",
            createdSec = baseTime + 50,
            completedSec = baseTime + 250
        )
        // Bob's newer snapshot must never leak to Alice
        gateway.manifests["uid_bob" to "snap_bob_newer"] = dtoManifest(
            snapshotId = "snap_bob_newer",
            uid = "uid_bob",
            status = "COMPLETE",
            createdSec = baseTime + 500,
            completedSec = baseTime + 550
        )

        val latest = repository.getLatestCompleteManifest().getOrThrow()
        assertNotNull(latest)
        assertEquals("snap_complete_newest", latest!!.snapshotId)
        assertEquals(CloudBackupManifestStatus.COMPLETE, latest.status)
    }

    @Test
    fun `downloadChunks rejects non-COMPLETE manifests and missing, duplicated, or corrupted chunks`() = runTest {
        val gateway = FakeRemoteBackupGateway(authenticatedUid = "uid_alice")
        val repository = FirestoreCloudBackupRepository(gateway, chunker)
        val payload = ByteArray(40) { (it + 10).toByte() } // 3 chunks
        val (manifest, chunked) = buildSnapshot("snap_dl_01", "uid_alice", payload)

        // Upload valid snapshot first
        assertTrue(repository.uploadBackup("uid_alice", manifest, chunked).isSuccess)

        // 1. Non-COMPLETE manifest rejected
        gateway.manifests["uid_alice" to "snap_uploading"] =
            gateway.manifests["uid_alice" to "snap_dl_01"]!!.copy(
                snapshotId = "snap_uploading",
                status = "UPLOADING",
                completedAt = null
            )
        val nonComplete = repository.downloadChunks("uid_alice", "snap_uploading")
        assertTrue(nonComplete.exceptionOrNull() is CloudBackupRemoteException.SnapshotNotComplete)

        // 2. Missing chunk rejected
        val savedChunk2 = gateway.chunks["uid_alice" to "snap_dl_01"]!!.remove("chunk_2")!!
        val missingRes = repository.downloadChunks("uid_alice", "snap_dl_01")
        assertTrue(missingRes.exceptionOrNull() is CloudBackupRemoteException.ChunkIntegrityError)
        gateway.chunks["uid_alice" to "snap_dl_01"]!!["chunk_2"] = savedChunk2

        // 3. Duplicate chunk index rejected
        gateway.chunks["uid_alice" to "snap_dl_01"]!!["chunk_2"] =
            gateway.chunks["uid_alice" to "snap_dl_01"]!!["chunk_1"]!!.copy(id = "chunk_2")
        val dupRes = repository.downloadChunks("uid_alice", "snap_dl_01")
        assertTrue(dupRes.exceptionOrNull() is CloudBackupRemoteException.ChunkIntegrityError)
        gateway.chunks["uid_alice" to "snap_dl_01"]!!["chunk_2"] = savedChunk2

        // 4. Corrupted chunk bytes (while keeping old sha256) rejected
        val origBytes = savedChunk2.payload!!.toBytes()
        val tampered = origBytes.copyOf().apply { this[0] = (this[0].toInt() xor 0xFF).toByte() }
        gateway.chunks["uid_alice" to "snap_dl_01"]!!["chunk_2"] =
            savedChunk2.copy(payload = Blob.fromBytes(tampered))
        val corruptRes = repository.downloadChunks("uid_alice", "snap_dl_01")
        assertTrue(corruptRes.exceptionOrNull() is CloudBackupRemoteException.ChunkIntegrityError)

        // 5. Chunk bytes + matching chunk sha256 replaced, but overall payloadSha256 mismatch rejected
        gateway.chunks["uid_alice" to "snap_dl_01"]!!["chunk_2"] =
            savedChunk2.copy(
                payload = Blob.fromBytes(tampered),
                sha256 = CloudBackupPayloadChunker.sha256Hex(tampered)
            )
        val overallShaMismatch = repository.downloadChunks("uid_alice", "snap_dl_01")
        assertTrue(overallShaMismatch.exceptionOrNull() is CloudBackupRemoteException.ChunkIntegrityError)
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

    private fun dtoManifest(
        snapshotId: String,
        uid: String,
        status: String,
        createdSec: Long,
        completedSec: Long?,
        failedSec: Long? = null,
        failureCode: String? = null
    ) = FirestoreBackupManifestDto(
        snapshotId = snapshotId,
        userId = uid,
        status = status,
        createdAt = Timestamp(createdSec, 0),
        updatedAt = Timestamp(completedSec ?: failedSec ?: createdSec, 0),
        completedAt = completedSec?.let { Timestamp(it, 0) },
        failedAt = failedSec?.let { Timestamp(it, 0) },
        failureCode = failureCode,
        formatVersion = 1L,
        databaseSchemaVersion = 4L,
        appVersion = "1.5.0",
        chunkCount = 1L,
        compressedBytes = 16L,
        uncompressedBytes = 128L,
        payloadSha256 = "a".repeat(64),
        contentFingerprint = contentFingerprint
    )

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
            val existing = manifests[uid to snapshotId] ?: error("Missing manifest")
            val now = Timestamp(serverTimeSec++, 0)
            manifests[uid to snapshotId] = existing.copy(
                status = CloudBackupManifestStatus.COMPLETE.name,
                completedAt = now,
                updatedAt = now
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
