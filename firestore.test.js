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

function validUploadingGenerationPayload(uid, now = new Date(Date.now() - 5000)) {
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

function validPrimaryCreatePayload(uid, generationId, genDoc) {
  return {
    userId: uid,
    status: "COMPLETE",
    createdAt: genDoc.createdAt,
    updatedAt: new Date(Date.now() - 500),
    completedAt: genDoc.completedAt,
    formatVersion: genDoc.formatVersion,
    databaseSchemaVersion: genDoc.databaseSchemaVersion,
    appVersion: genDoc.appVersion,
    chunkCount: genDoc.chunkCount,
    compressedBytes: genDoc.compressedBytes,
    uncompressedBytes: genDoc.uncompressedBytes,
    payloadSha256: genDoc.payloadSha256,
    contentFingerprint: genDoc.contentFingerprint,
    generationId: generationId,
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
      .doc("gen_1")
      .set(validUploadingGenerationPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backup_manifests")
      .doc("gen_1")
      .collection("chunks")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("backup_manifests")
      .doc("primary_restore_point")
      .get()
  );
});

test("Authenticated owner: generation lifecycle and primary promotion lifecycle succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const genId = "gen_1763200000_01";
  const genRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("backup_manifests")
    .doc(genId);

  // 1. Create UPLOADING generation manifest
  const genPayload = validUploadingGenerationPayload(ALICE_UID, created);
  await assertSucceeds(genRef.set(genPayload));

  // 2. Upload ordered binary Blob chunks under parent generation manifest
  await assertSucceeds(
    genRef
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(aliceDb, ALICE_UID, genId, 0, 2, created))
  );
  await assertSucceeds(
    genRef
      .collection("chunks")
      .doc("chunk_1")
      .set(validChunkPayload(aliceDb, ALICE_UID, genId, 1, 2, created))
  );

  // 3. Transition generation manifest to COMPLETE via partial update
  const completed = new Date(Date.now() - 2000);
  await assertSucceeds(
    genRef.update({
      status: "COMPLETE",
      completedAt: completed,
      updatedAt: completed,
    })
  );

  // 4. Create primary_restore_point referencing completed generation
  const primaryRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("backup_manifests")
    .doc("primary_restore_point");

  const completedGenDoc = {
    ...genPayload,
    status: "COMPLETE",
    completedAt: completed,
  };
  await assertSucceeds(primaryRef.set(validPrimaryCreatePayload(ALICE_UID, genId, completedGenDoc)));

  // 5. Subsequent generation upload and primary promotion
  const genId2 = "gen_1763200000_02";
  const genRef2 = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("backup_manifests")
    .doc(genId2);
  const created2 = new Date(Date.now() - 1500);
  const completed2 = new Date(Date.now() - 1000);
  const genPayload2 = validUploadingGenerationPayload(ALICE_UID, created2);
  await assertSucceeds(genRef2.set(genPayload2));
  await assertSucceeds(
    genRef2
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(aliceDb, ALICE_UID, genId2, 0, 2, created2))
  );
  await assertSucceeds(
    genRef2
      .collection("chunks")
      .doc("chunk_1")
      .set(validChunkPayload(aliceDb, ALICE_UID, genId2, 1, 2, created2))
  );
  await assertSucceeds(
    genRef2.update({
      status: "COMPLETE",
      completedAt: completed2,
      updatedAt: completed2,
    })
  );

  // Promote primary: updates metadata, points to genId2, preserves createdAt
  await assertSucceeds(
    primaryRef.update({
      status: "COMPLETE",
      updatedAt: new Date(Date.now() - 500),
      completedAt: completed2,
      formatVersion: 1,
      databaseSchemaVersion: 4,
      appVersion: "1.5.0",
      chunkCount: 2,
      compressedBytes: SAMPLE_BYTE_COUNT * 2,
      uncompressedBytes: 4096,
      payloadSha256: SHA_A,
      contentFingerprint: SHA_B,
      generationId: genId2,
    })
  );

  // 6. Terminal state locking: generation COMPLETE cannot be mutated
  await assertFails(
    genRef.update({
      status: "UPLOADING",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Authenticated owner: FAILED generation can retry back to UPLOADING and complete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const genId = "gen_retry_01";
  const genRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("backup_manifests")
    .doc(genId);

  await assertSucceeds(genRef.set(validUploadingGenerationPayload(ALICE_UID, created)));
  await assertSucceeds(
    genRef
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(aliceDb, ALICE_UID, genId, 0, 2, created))
  );

  const failedAt = new Date(Date.now() - 5000);
  await assertSucceeds(
    genRef.update({
      status: "FAILED",
      failedAt,
      failureCode: "CHUNK_UPLOAD_ERROR",
      updatedAt: failedAt,
    })
  );

  // Direct FAILED -> COMPLETE blocked
  await assertFails(
    genRef.update({
      status: "COMPLETE",
      completedAt: new Date(Date.now() - 4000),
      updatedAt: new Date(Date.now() - 4000),
    })
  );

  // Chunks cannot be created while parent is FAILED
  await assertFails(
    genRef
      .collection("chunks")
      .doc("chunk_1")
      .set(validChunkPayload(aliceDb, ALICE_UID, genId, 1, 2, failedAt))
  );

  // Retry: FAILED -> UPLOADING
  const retryAt = new Date(Date.now() - 3000);
  await assertSucceeds(
    genRef.update({
      status: "UPLOADING",
      updatedAt: retryAt,
      failedAt: deleteFieldSentinel(aliceDb),
      failureCode: deleteFieldSentinel(aliceDb),
    })
  );
  await assertSucceeds(
    genRef
      .collection("chunks")
      .doc("chunk_1")
      .set(validChunkPayload(aliceDb, ALICE_UID, genId, 1, 2, retryAt))
  );
  const completedAt = new Date(Date.now() - 1000);
  await assertSucceeds(
    genRef.update({
      status: "COMPLETE",
      completedAt,
      updatedAt: completedAt,
    })
  );
});

test("Authenticated user: cannot read, list, create, update, or delete another user's manifests or chunks", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const bobGen = adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("backup_manifests")
      .doc("gen_bob_1");
    await bobGen.set(validUploadingGenerationPayload(BOB_UID));
    await bobGen
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(adminDb, BOB_UID, "gen_bob_1", 0, 2));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobGenAsAlice = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("backup_manifests")
    .doc("gen_bob_1");

  await assertFails(bobGenAsAlice.get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("backup_manifests").get()
  );
  await assertFails(bobGenAsAlice.collection("chunks").doc("chunk_0").get());
  await assertFails(bobGenAsAlice.collection("chunks").get());
  await assertFails(bobGenAsAlice.delete());
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("backup_manifests")
      .doc("gen_alice_spoof")
      .set(validUploadingGenerationPayload(ALICE_UID))
  );
});

test("Negative security bounds: rejects invalid snapshot IDs, unpromoted references, tampered primary metadata, and chunks under primary", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const manifests = aliceDb.collection("users").doc(ALICE_UID).collection("backup_manifests");

  // Snapshot ID not starting with gen_ and not primary_restore_point rejected
  await assertFails(
    manifests.doc("snap_invalid_prefix").set(validUploadingGenerationPayload(ALICE_UID))
  );

  // Generation manifest cannot be created directly as COMPLETE
  await assertFails(
    manifests.doc("gen_skip_state").set({
      ...validUploadingGenerationPayload(ALICE_UID),
      status: "COMPLETE",
      completedAt: new Date(Date.now() - 1000),
    })
  );

  // Primary restore point cannot reference non-existent generation
  await assertFails(
    manifests.doc("primary_restore_point").set({
      ...validUploadingGenerationPayload(ALICE_UID),
      status: "COMPLETE",
      completedAt: new Date(Date.now() - 1000),
      generationId: "gen_non_existent",
    })
  );

  // Create UPLOADING generation
  const genId = "gen_bounds_01";
  const genRef = manifests.doc(genId);
  const created = new Date(Date.now() - 5000);
  await assertSucceeds(genRef.set(validUploadingGenerationPayload(ALICE_UID, created)));

  // Primary restore point cannot reference UPLOADING generation
  await assertFails(
    manifests.doc("primary_restore_point").set({
      ...validUploadingGenerationPayload(ALICE_UID, created),
      status: "COMPLETE",
      completedAt: new Date(Date.now() - 1000),
      generationId: genId,
    })
  );

  // Chunks cannot be created directly under primary_restore_point
  await assertFails(
    manifests
      .doc("primary_restore_point")
      .collection("chunks")
      .doc("chunk_0")
      .set(validChunkPayload(aliceDb, ALICE_UID, "primary_restore_point", 0, 2))
  );

  // Complete the generation
  const completed = new Date(Date.now() - 2000);
  await assertSucceeds(
    genRef.update({
      status: "COMPLETE",
      completedAt: completed,
      updatedAt: completed,
    })
  );

  // Primary with tampered contentFingerprint (not matching generation) rejected
  await assertFails(
    manifests.doc("primary_restore_point").set({
      ...validPrimaryCreatePayload(ALICE_UID, genId, {
        ...validUploadingGenerationPayload(ALICE_UID, created),
        status: "COMPLETE",
        completedAt: completed,
      }),
      contentFingerprint: SHA_A, // Tampered!
    })
  );

  // Primary create succeeds with matching metadata
  const validPrimary = validPrimaryCreatePayload(ALICE_UID, genId, {
    ...validUploadingGenerationPayload(ALICE_UID, created),
    status: "COMPLETE",
    completedAt: completed,
  });
  await assertSucceeds(manifests.doc("primary_restore_point").set(validPrimary));

  // Primary update cannot modify createdAt
  await assertFails(
    manifests.doc("primary_restore_point").update({
      createdAt: new Date(Date.now() - 100),
    })
  );
});
