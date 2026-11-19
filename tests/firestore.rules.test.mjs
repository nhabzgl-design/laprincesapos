import { readFileSync } from "node:fs";
import test, { after, before } from "node:test";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  writeBatch
} from "firebase/firestore";

const projectId = "demo-laprincesa-pos-rules";
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId,
    firestore: {
      rules: readFileSync("firestore.rules", "utf8")
    }
  });

  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "roles", "owner-uid"), {
      role: "owner",
      name: "Sheena Marie Cuizon",
      email: "owner@example.com"
    });
    await setDoc(doc(db, "roles", "staff-uid"), {
      role: "staff",
      username: "cashier",
      email: "cashier@laprincesa-pos.app"
    });
    await setDoc(doc(db, "data", "sample"), { total: 100 });
  });
});

after(async () => {
  if (env) await env.cleanup();
});

test("unauthenticated users cannot read or write POS data", async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "data", "sample")));
  await assertFails(setDoc(doc(db, "data", "unauthorized"), { value: true }));
  await assertFails(getDoc(doc(db, "meta", "clear_generation")));
});

test("Owner can atomically advance the clear generation and write only four empty markers", async () => {
  const db = env.authenticatedContext("owner-uid", {
    email: "owner@example.com"
  }).firestore();

  await assertSucceeds(getDoc(doc(db, "data", "sample")));
  await assertFails(setDoc(doc(db, "data", "owner-write"), { value: 1 }));
  await assertFails(updateDoc(doc(db, "data", "sample"), { total: 200 }));
  await assertFails(deleteDoc(doc(db, "data", "sample")));
  await assertFails(getDoc(doc(db, "meta", "unrelated")));

  // A marker without the matching generation update must not be accepted.
  await assertFails(setDoc(doc(db, "data", "laPrincesa_orders_v2"), {
    v: "[]", parts: 0, t: Date.now(), by: "owner", g: 1
  }));

  const batch = writeBatch(db);
  batch.set(doc(db, "meta", "clear_generation"), {
    generation: 1, t: Date.now(), by: "owner"
  });
  for (const key of [
    "laPrincesa_orders_v2",
    "laPrincesa_inventory_v2",
    "laPrincesa_deleted_orders_v1",
    "laPrincesa_activity_log_v1"
  ]) {
    batch.set(doc(db, "data", key), {
      v: "[]", parts: 0, t: Date.now(), by: "owner", g: 1
    });
  }
  await assertSucceeds(batch.commit());

  await assertSucceeds(getDoc(doc(db, "meta", "clear_generation")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_orders_v2")));
  await assertSucceeds(deleteDoc(doc(db, "data", "laPrincesa_orders_v2~p0")));

  await assertFails(setDoc(doc(db, "data", "laPrincesa_orders_v2"), {
    v: '[{"id":"not-empty"}]', parts: 0, t: Date.now(), by: "owner", g: 1
  }));
  await assertFails(setDoc(doc(db, "data", "laPrincesa_customers_v2"), {
    v: "[]", parts: 0, t: Date.now(), by: "owner", g: 1
  }));
  await assertFails(updateDoc(doc(db, "meta", "clear_generation"), {
    generation: 3
  }));
  await assertFails(deleteDoc(doc(db, "meta", "clear_generation")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_customers_v2")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_machines")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_printer_mode_v1")));
});

test("Staff writes at the current generation but cannot replay stale offline data", async () => {
  const db = env.authenticatedContext("staff-uid", {
    email: "cashier@laprincesa-pos.app"
  }).firestore();

  await assertSucceeds(getDoc(doc(db, "data", "sample")));
  await assertSucceeds(getDoc(doc(db, "meta", "clear_generation")));
  await assertSucceeds(setDoc(doc(db, "data", "staff-write"), { value: 2 }));

  await assertSucceeds(setDoc(doc(db, "data", "laPrincesa_orders_v2"), {
    v: '[{"id":"new-order"}]', parts: 0, t: Date.now(), by: "cashier", g: 1
  }));
  await assertSucceeds(setDoc(doc(db, "data", "laPrincesa_orders_v2~p1"), {
    s: "new chunk", g: 1
  }));
  await assertFails(setDoc(doc(db, "data", "laPrincesa_orders_v2"), {
    v: '[{"id":"stale-offline-order"}]', parts: 0, t: Date.now(), by: "cashier", g: 0
  }));
  await assertFails(setDoc(doc(db, "data", "laPrincesa_orders_v2~p2"), {
    s: "stale chunk", g: 0
  }));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_orders_v2")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_inventory_v2")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_deleted_orders_v1")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_activity_log_v1")));
  await assertFails(setDoc(doc(db, "meta", "clear_generation"), {
    generation: 2, t: Date.now(), by: "cashier"
  }));
});

test("users without a provisioned role cannot access POS data", async () => {
  const db = env.authenticatedContext("unprovisioned-uid", {
    email: "new@example.com"
  }).firestore();

  await assertFails(getDoc(doc(db, "data", "sample")));
  await assertFails(setDoc(doc(db, "data", "blocked"), { value: 3 }));
  await assertFails(getDoc(doc(db, "meta", "clear_generation")));
});

test("Staff can read only their own role and cannot manage roles", async () => {
  const db = env.authenticatedContext("staff-uid", {
    email: "cashier@laprincesa-pos.app"
  }).firestore();

  await assertSucceeds(getDoc(doc(db, "roles", "staff-uid")));
  await assertFails(getDoc(doc(db, "roles", "owner-uid")));
  await assertFails(getDocs(collection(db, "roles")));
  await assertFails(setDoc(doc(db, "roles", "another-staff"), {
    role: "staff",
    email: "another@laprincesa-pos.app"
  }));
  await assertFails(updateDoc(doc(db, "roles", "staff-uid"), {
    role: "owner"
  }));
  await assertFails(deleteDoc(doc(db, "roles", "staff-uid")));
});

test("Owner can create, update, and delete Staff roles but cannot create or alter another Owner", async () => {
  const db = env.authenticatedContext("owner-uid", {
    email: "owner@example.com"
  }).firestore();

  await assertSucceeds(setDoc(doc(db, "roles", "new-staff"), {
    role: "staff",
    username: "newcashier",
    email: "newcashier@laprincesa-pos.app"
  }));
  await assertSucceeds(updateDoc(doc(db, "roles", "new-staff"), {
    username: "cashier-updated"
  }));
  await assertSucceeds(deleteDoc(doc(db, "roles", "new-staff")));

  await assertFails(setDoc(doc(db, "roles", "second-owner"), {
    role: "owner",
    email: "second@example.com"
  }));
  await assertFails(updateDoc(doc(db, "roles", "owner-uid"), {
    name: "Changed Owner"
  }));
  await assertFails(deleteDoc(doc(db, "roles", "owner-uid")));
});
