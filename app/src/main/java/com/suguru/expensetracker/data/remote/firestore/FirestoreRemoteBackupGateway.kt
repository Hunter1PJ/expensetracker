package com.suguru.expensetracker.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.suguru.expensetracker.domain.model.CloudBackupManifest
import com.suguru.expensetracker.domain.model.CloudBackupManifestStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Minimal internal I/O seam isolating Firebase Auth and Firestore SDK calls from
 * [FirestoreCloudBackupRepository] validation, lifecycle ordering, and retry logic.
 */
internal interface RemoteBackupGateway {
    fun currentUserUid(): String?
    fun observeManifests(uid: String, limit: Long): Flow<List<CloudBackupManifest>>
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
}

internal class FirestoreRemoteBackupGateway(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : RemoteBackupGateway {

    private fun manifestsCollection(uid: String) =
        db.collection("users").document(uid).collection("backup_manifests")

    override fun currentUserUid(): String? =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    override fun observeManifests(uid: String, limit: Long): Flow<List<CloudBackupManifest>> {
        val path = "users/$uid/backup_manifests"
        return manifestsCollection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        FirestoreBackupManifestDto::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.toDomainOrNull()
                }
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
        val snapshot = manifestsCollection(uid).document(snapshotId).get().await()
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
        val chunksSnapshot = manifestRef.collection("chunks").get().await()
        for (chunkDoc in chunksSnapshot.documents) {
            chunkDoc.reference.delete().await()
        }
        manifestRef.delete().await()
    }
}
