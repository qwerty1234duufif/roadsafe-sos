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
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validAlertPayload(overrides = {}) {
  const now = new Date();
  return {
    reporterId: ALICE_UID,
    reporterName: "Alice",
    category: "accident",
    severity: "critical",
    description: "Two-car collision near highway exit 14",
    latitude: 37.422,
    longitude: -122.084,
    locationLabel: "US-101 Exit 14, Mountain View",
    status: "active",
    responderCount: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
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

// --- SECURITY BOUNDS & DIRTY DOZEN TESTS ---

test("Unauthenticated user cannot list or create alerts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("alerts").where("status", "==", "active").get());
  await assertFails(unauthDb.collection("alerts").doc("alert_1").set(validAlertPayload()));
});

test("Unfiltered alerts query is rejected (rules are not filters)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("alerts").get());
});

test("Authenticated user can create valid active alert and others can query active alerts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("alerts").doc("alert_1").set(validAlertPayload()));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("alerts").where("status", "==", "active").get());
});

test("Identity spoofing: cannot create alert with another user's reporterId", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("alerts").doc("alert_spoof").set(validAlertPayload({ reporterId: ALICE_UID }))
  );
});

test("Shadow update test: cannot inject ghost fields on alert creation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("alerts").doc("alert_ghost").set({
      ...validAlertPayload(),
      isVerified: true,
    })
  );
});

test("Value poisoning: cannot create alert with out-of-range coordinates or pre-populated responderCount", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("alerts").doc("alert_bad_lat").set(validAlertPayload({ latitude: 120.0 }))
  );
  await assertFails(
    aliceDb.collection("alerts").doc("alert_bad_count").set(validAlertPayload({ responderCount: 5 }))
  );
});

test("Atomic responder claim: helper creates response and increments responderCount in batch", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("alerts").doc("alert_1").set(validAlertPayload()));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const batch = bobDb.batch();
  const alertRef = bobDb.collection("alerts").doc("alert_1");
  const respRef = alertRef.collection("responses").doc(BOB_UID);
  const now = new Date();
  batch.set(respRef, {
    responderId: BOB_UID,
    responderName: "Bob",
    alertId: "alert_1",
    alertReporterId: ALICE_UID,
    status: "responding",
    createdAt: now,
    updatedAt: now,
  });
  batch.update(alertRef, {
    responderCount: 1,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());
});

test("Non-owner cannot resolve alert or tamper with alert description", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("alerts").doc("alert_1").set(validAlertPayload()));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("alerts").doc("alert_1").update({ status: "resolved", updatedAt: new Date() })
  );
  await assertFails(
    bobDb.collection("alerts").doc("alert_1").update({ description: "Tampered", updatedAt: new Date() })
  );
});

test("Terminal state locking: resolved alert cannot be reopened or read by non-owners", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("alerts").doc("alert_1").set(validAlertPayload()));
  await assertSucceeds(
    aliceDb.collection("alerts").doc("alert_1").update({ status: "resolved", updatedAt: new Date() })
  );

  await assertFails(
    aliceDb.collection("alerts").doc("alert_1").update({ status: "active", updatedAt: new Date() })
  );

  // Non-owner cannot get resolved alert
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("alerts").doc("alert_1").get());

  // Reporter can query their own alerts including resolved
  await assertSucceeds(aliceDb.collection("alerts").where("reporterId", "==", ALICE_UID).get());
});

test("User safety profile PII isolation: cross-user read and write rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      bloodGroup: "O+",
      createdAt: now,
      updatedAt: now,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: new Date() })
  );
});

test("Responder list query: reporter can list responses for their alert; third party cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("alerts").doc("alert_1").set(validAlertPayload()));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    bobDb.collection("alerts").doc("alert_1").collection("responses").doc(BOB_UID).set({
      responderId: BOB_UID,
      responderName: "Bob",
      alertId: "alert_1",
      alertReporterId: ALICE_UID,
      status: "responding",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("alerts")
      .doc("alert_1")
      .collection("responses")
      .where("alertReporterId", "==", ALICE_UID)
      .get()
  );

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb
      .collection("alerts")
      .doc("alert_1")
      .collection("responses")
      .where("alertReporterId", "==", ALICE_UID)
      .get()
  );
});
