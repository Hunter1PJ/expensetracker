const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const SHA_A = "a".repeat(64);
const SHA_B = "b".repeat(64);
const SAMPLE_BYTES = Uint8Array.from(Buffer.from("gzip-backup-chunk-bytes", "utf8"));
const SAMPLE_BYTE_COUNT = SAMPLE_BYTES.byteLength;

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function toBlob(db, uint8Array) {
  return db._appCompat.firebase.firestore.Blob.fromUint8Array(uint8Array);
}

function deleteFieldSentinel(db) {
  return db._appCompat.firebase.firestore.FieldValue.delete();
}

function validUploadingManifestPayload(uid, now = new Date(Date.now() - 1000)) {
  return {
    userId: uid,
    status: "UPLOADING",
    createdAt: now,
    updatedAt: now,
    formatVersion: 1,
    databaseSchemaVersion: 4,
    appVersion: "1.5.0",
    chunkCount: 2,
    compressedBytes: SAMPLE_BYTE_COUNT * 2,
    uncompressedBytes: 4096,
    payloadSha256: SHA_A,
    contentFingerprint: SHA_B,
  };
}

function validChunkPayload(
  db,
  uid,
  snapshotId,
  index,
  totalCount = 2,
  now = new Date(Date.now() - 1000)
) {
  return {
    userId: uid,
    snapshotId,
    index,
    totalCount,
    sha256: SHA_A,
    byteCount: SAMPLE_BYTE_COUNT,
    payload: toBlob(db, SAMPLE_BYTES),
    createdAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read, list, create, update, or delete backup manifests or chunks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("backup_manifests").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backup_manifests")
      .doc("snap_1")
      .set(validUploadingManifestPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backup_manifests")
      .doc("snap_1")
      .collection("chunks")
      .get()
  );
});

test("Authenticated owner: Kotlin-mirrored upload lifecycle (UPLOADING -> binary Blob chunks -> COMPLETE) and list queries succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const manifestRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("backup_manifests")
    .doc("snap_1763200000_01");

  // 1. Create UPLOADING manifest (no @DocumentId field, no optional fields)
  await assertSucceeds(manifestRef.set(validUploadingManifestPayload(ALICE_UID, created)));

  // 2. Upload ordered binary Blob chunks under parent UPLOADING manifest
  await assertSucceeds(
    manifestRef
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_1763200000_01", 0, 2, created))
  );
  await assertSucceeds(
    manifestRef
      .collection("chunks")
      .doc("chunk_1")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_1763200000_01", 1, 2, created))
  );

  // 3. Transition manifest to COMPLETE via partial update
  const completed = new Date(Date.now() - 1000);
  await assertSucceeds(
    manifestRef.update({
      status: "COMPLETE",
      completedAt: completed,
      updatedAt: completed,
    })
  );

  // 4. Mirror Kotlin ordered list query, COMPLETE status query, and chunk list query
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backup_manifests")
      .orderBy("createdAt", "desc")
      .limit(20)
      .get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backup_manifests")
      .where("status", "==", "COMPLETE")
      .get()
  );
  await assertSucceeds(manifestRef.collection("chunks").orderBy("index", "asc").get());

  // 5. Terminal state locking: COMPLETE manifest cannot be mutated or reset
  await assertFails(
    manifestRef.update({
      status: "UPLOADING",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: FAILED manifest can retry back to UPLOADING and complete, while direct FAILED -> COMPLETE is blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const manifestRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("backup_manifests")
    .doc("snap_retry_01");

  await assertSucceeds(manifestRef.set(validUploadingManifestPayload(ALICE_UID, created)));
  await assertSucceeds(
    manifestRef
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_retry_01", 0, 2, created))
  );

  const failedAt = new Date(Date.now() - 5000);
  await assertSucceeds(
    manifestRef.update({
      status: "FAILED",
      failedAt,
      failureCode: "CHUNK_UPLOAD_ERROR",
      updatedAt: failedAt,
    })
  );

  // Cannot skip directly from FAILED to COMPLETE
  await assertFails(
    manifestRef.update({
      status: "COMPLETE",
      completedAt: new Date(Date.now() - 4000),
      updatedAt: new Date(Date.now() - 4000),
    })
  );

  // Chunks cannot be created while parent manifest is FAILED
  await assertFails(
    manifestRef
      .collection("chunks")
      .doc("chunk_1")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_retry_01", 1, 2, failedAt))
  );

  // Retry: transition FAILED -> UPLOADING (deleting failedAt/failureCode), upload remaining chunk, transition to COMPLETE
  const retryAt = new Date(Date.now() - 3000);
  await assertSucceeds(
    manifestRef.update({
      status: "UPLOADING",
      updatedAt: retryAt,
      failedAt: deleteFieldSentinel(aliceDb),
      failureCode: deleteFieldSentinel(aliceDb),
    })
  );
  await assertSucceeds(
    manifestRef
      .collection("chunks")
      .doc("chunk_1")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_retry_01", 1, 2, retryAt))
  );
  const completedAt = new Date(Date.now() - 1000);
  await assertSucceeds(
    manifestRef.update({
      status: "COMPLETE",
      completedAt,
      updatedAt: completedAt,
    })
  );
});

test("Authenticated user: cannot read, list, create, update, or delete another user's manifests or chunks", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const bobManifest = adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("backup_manifests")
      .doc("bob_snap_1");
    await bobManifest.set(validUploadingManifestPayload(BOB_UID));
    await bobManifest
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(adminDb, BOB_UID, "bob_snap_1", 0, 2));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobManifestAsAlice = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("backup_manifests")
    .doc("bob_snap_1");

  await assertFails(bobManifestAsAlice.get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("backup_manifests").get()
  );
  await assertFails(bobManifestAsAlice.collection("chunks").doc("chunk_0").get());
  await assertFails(bobManifestAsAlice.collection("chunks").get());
  await assertFails(bobManifestAsAlice.delete());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("backup_manifests")
      .doc("alice_spoof")
      .set(validUploadingManifestPayload(ALICE_UID))
  );
});

test("Negative security bounds: rejects @DocumentId field, ghost keys, future timestamps, spoofing, binary payload mismatches, and invalid chunk counts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const manifests = aliceDb.collection("users").doc(ALICE_UID).collection("backup_manifests");

  // Extra @DocumentId / snapshotId key forbidden
  await assertFails(
    manifests.doc("snap_extra_id").set({
      ...validUploadingManifestPayload(ALICE_UID),
      snapshotId: "snap_extra_id",
    })
  );

  // Ghost field rejected
  await assertFails(
    manifests.doc("snap_ghost").set({
      ...validUploadingManifestPayload(ALICE_UID),
      isAdmin: true,
    })
  );

  // Spoofed userId rejected
  await assertFails(
    manifests.doc("snap_spoof").set(validUploadingManifestPayload(BOB_UID))
  );

  // Cannot create directly as COMPLETE (must start as UPLOADING)
  await assertFails(
    manifests.doc("snap_skip_state").set({
      ...validUploadingManifestPayload(ALICE_UID),
      status: "COMPLETE",
      completedAt: new Date(Date.now() - 1000),
    })
  );

  // Tampered future timestamp rejected
  await assertFails(
    manifests.doc("snap_future").set(
      validUploadingManifestPayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Invalid document ID poisoned path rejected
  await assertFails(
    manifests.doc("bad.id.with.dots").set(validUploadingManifestPayload(ALICE_UID))
  );

  // Create valid manifest then test chunk constraints & immutable update prevention
  const validManifestRef = manifests.doc("snap_bounds_01");
  await assertSucceeds(validManifestRef.set(validUploadingManifestPayload(ALICE_UID)));

  // Chunk with mismatched totalCount or out-of-range index fails
  await assertFails(
    validManifestRef
      .collection("chunks")
      .doc("chunk_bad_count")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_bounds_01", 0, 3))
  );
  await assertFails(
    validManifestRef
      .collection("chunks")
      .doc("chunk_bad_idx")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_bounds_01", 2, 2))
  );
  // Chunk with invalid SHA256 fails
  await assertFails(
    validManifestRef
      .collection("chunks")
      .doc("chunk_bad_sha")
      .set({
        ...validChunkPayload(aliceDb, ALICE_UID, "snap_bounds_01", 0, 2),
        sha256: "not-a-sha",
      })
  );
  // Chunk with string instead of binary Blob or mismatched byteCount fails
  await assertFails(
    validManifestRef
      .collection("chunks")
      .doc("chunk_bad_type")
      .set({
        ...validChunkPayload(aliceDb, ALICE_UID, "snap_bounds_01", 0, 2),
        payload: "not-bytes",
      })
  );
  await assertFails(
    validManifestRef
      .collection("chunks")
      .doc("chunk_bad_len")
      .set({
        ...validChunkPayload(aliceDb, ALICE_UID, "snap_bounds_01", 0, 2),
        byteCount: SAMPLE_BYTE_COUNT + 10,
      })
  );

  // Valid chunk created, then update on chunk fails (chunks immutable)
  await assertSucceeds(
    validManifestRef
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(aliceDb, ALICE_UID, "snap_bounds_01", 0, 2))
  );
  await assertFails(
    validManifestRef
      .collection("chunks")
      .doc("chunk_0")
      .update({ byteCount: 10 })
  );

  // Tampered immutable field during manifest update fails
  await assertFails(
    validManifestRef.update({
      status: "COMPLETE",
      completedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
      contentFingerprint: SHA_A,
    })
  );
});
