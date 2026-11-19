package com.suguru.expensetracker.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.error.CloudBackupRemoteException
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.tasks.await

/**
 * Minimal internal I/O seam isolating Firebase Auth and Firestore SDK calls from
 * [FirestoreCloudBackupRepository] validation, lifecycle ordering, and retry logic.
 */
internal interface RemoteBackupGateway {
    fun currentUserUid(): String?
    fun observeManifests(uid: String): Flow<List<CloudBackupManifest>>
    suspend fun getManifest(uid: String, snapshotId: String): FirestoreBackupManifestDto?
    suspend fun listCompleteManifests(uid: String): List<FirestoreBackupManifestDto>
    suspend fun createUploadingManifest(uid: String, snapshotId: String, payload: Map<String, Any>)
    suspend fun resetManifestToUploading(uid: String, snapshotId: String, payload: Map<String, Any>)
    suspend fun markManifestComplete(uid: String, snapshotId: String, payload: Map<String, Any>)
    suspend fun markManifestFailed(uid: String, snapshotId: String, payload: Map<String, Any>)
    suspend fun getChunk(uid: String, snapshotId: String, chunkId: String): FirestoreBackupChunkDto?
    suspend fun createChunk(uid: String, snapshotId: String, chunkId: String, payload: Map<String, Any>)
    suspend fun deleteChunk(uid: String, snapshotId: String, chunkId: String)
    suspend fun listChunks(uid: String, snapshotId: String): List<FirestoreBackupChunkDto>
    suspend fun deleteSnapshotDocuments(uid: String, snapshotId: String)
    suspend fun listAllManifests(uid: String): List<FirestoreBackupManifestDto>
    suspend fun createPrimaryManifest(uid: String, payload: Map<String, Any>)
    suspend fun updatePrimaryManifest(uid: String, payload: Map<String, Any>)
}

internal class FirestoreRemoteBackupGateway(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : RemoteBackupGateway {

    private fun manifestsCollection(uid: String) =
        db.collection("users").document(uid).collection("backup_manifests")

    override fun currentUserUid(): String? =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    override fun observeManifests(uid: String): Flow<List<CloudBackupManifest>> {
        val path = "users/$uid/backup_manifests/${FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID}"
        return manifestsCollection(uid)
            .document(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID)
            .snapshots(MetadataChanges.INCLUDE)
            .transform { snapshot ->
                // A cold Firestore memory cache is empty after process death. Do not publish that
                // cache miss as a definitive "no backup" result; wait for the server snapshot.
                if (snapshot.metadata.isFromCache) return@transform
                if (!snapshot.exists()) {
                    emit(emptyList())
                    return@transform
                }

                val manifest = snapshot.toObject(
                    FirestoreBackupManifestDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )?.toDomainOrNull()
                    ?: throw CloudBackupRemoteException.InvalidSnapshot(
                        "The primary restore point metadata is invalid."
                    )
                if (manifest.status != CloudBackupManifestStatus.COMPLETE || manifest.completedAt == null) {
                    // Keep the last known complete primary visible while a generation is being promoted.
                    return@transform
                }
                emit(listOf(manifest))
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && currentUserUid() == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    override suspend fun getManifest(uid: String, snapshotId: String): FirestoreBackupManifestDto? {
        // Backup existence and completeness decisions must come from Firestore, never an empty
        // process-local cache after restart.
        val snapshot = manifestsCollection(uid).document(snapshotId).get(Source.SERVER).await()
        if (!snapshot.exists()) return null
        return snapshot.toObject(
            FirestoreBackupManifestDto::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
    }

    override suspend fun listCompleteManifests(uid: String): List<FirestoreBackupManifestDto> {
        val querySnapshot = manifestsCollection(uid)
            .whereEqualTo("status", CloudBackupManifestStatus.COMPLETE.name)
            .get()
            .await()
        return querySnapshot.documents.mapNotNull { doc ->
            doc.toObject(
                FirestoreBackupManifestDto::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        }
    }

    override suspend fun createUploadingManifest(
        uid: String,
        snapshotId: String,
        payload: Map<String, Any>
    ) {
        manifestsCollection(uid).document(snapshotId).set(payload).await()
    }

    override suspend fun resetManifestToUploading(
        uid: String,
        snapshotId: String,
        payload: Map<String, Any>
    ) {
        manifestsCollection(uid).document(snapshotId).update(payload).await()
    }

    override suspend fun markManifestComplete(
        uid: String,
        snapshotId: String,
        payload: Map<String, Any>
    ) {
        manifestsCollection(uid).document(snapshotId).update(payload).await()
    }

    override suspend fun markManifestFailed(
        uid: String,
        snapshotId: String,
        payload: Map<String, Any>
    ) {
        manifestsCollection(uid).document(snapshotId).update(payload).await()
    }

    override suspend fun getChunk(
        uid: String,
        snapshotId: String,
        chunkId: String
    ): FirestoreBackupChunkDto? {
        val snapshot = manifestsCollection(uid)
            .document(snapshotId)
            .collection("chunks")
            .document(chunkId)
            .get()
            .await()
        if (!snapshot.exists()) return null
        return snapshot.toObject(
            FirestoreBackupChunkDto::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
    }

    override suspend fun createChunk(
        uid: String,
        snapshotId: String,
        chunkId: String,
        payload: Map<String, Any>
    ) {
        manifestsCollection(uid)
            .document(snapshotId)
            .collection("chunks")
            .document(chunkId)
            .set(payload)
            .await()
    }

    override suspend fun deleteChunk(uid: String, snapshotId: String, chunkId: String) {
        manifestsCollection(uid)
            .document(snapshotId)
            .collection("chunks")
            .document(chunkId)
            .delete()
            .await()
    }

    override suspend fun listChunks(uid: String, snapshotId: String): List<FirestoreBackupChunkDto> {
        val querySnapshot = manifestsCollection(uid)
            .document(snapshotId)
            .collection("chunks")
            .orderBy("index", Query.Direction.ASCENDING)
            .get()
            .await()
        return querySnapshot.documents.mapNotNull { doc ->
            doc.toObject(
                FirestoreBackupChunkDto::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        }
    }

    override suspend fun deleteSnapshotDocuments(uid: String, snapshotId: String) {
        val manifestRef = manifestsCollection(uid).document(snapshotId)
        val chunksSnapshot = manifestRef.collection("chunks").get(Source.SERVER).await()
        for (chunkDoc in chunksSnapshot.documents) {
            chunkDoc.reference.delete().await()
        }
        manifestRef.delete().await()
    }

    override suspend fun listAllManifests(uid: String): List<FirestoreBackupManifestDto> {
        val querySnapshot = manifestsCollection(uid).get(Source.SERVER).await()
        return querySnapshot.documents.mapNotNull { doc ->
            doc.toObject(
                FirestoreBackupManifestDto::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        }
    }

    override suspend fun createPrimaryManifest(
        uid: String,
        payload: Map<String, Any>
    ) {
        manifestsCollection(uid)
            .document(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID)
            .set(payload)
            .await()
    }

    override suspend fun updatePrimaryManifest(
        uid: String,
        payload: Map<String, Any>
    ) {
        manifestsCollection(uid)
            .document(FirestoreCloudBackupRepository.PRIMARY_SNAPSHOT_ID)
            .update(payload)
            .await()
    }
}
