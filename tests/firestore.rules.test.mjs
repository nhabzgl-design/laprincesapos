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
  updateDoc
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
});

test("Owner can read data and delete only the four approved Clear Data categories", async () => {
  const db = env.authenticatedContext("owner-uid", {
    email: "owner@example.com"
  }).firestore();

  await assertSucceeds(getDoc(doc(db, "data", "sample")));
  await assertFails(setDoc(doc(db, "data", "owner-write"), { value: 1 }));
  await assertFails(updateDoc(doc(db, "data", "sample"), { total: 200 }));
  await assertFails(deleteDoc(doc(db, "data", "sample")));
  await assertSucceeds(deleteDoc(doc(db, "data", "laPrincesa_orders_v2")));
  await assertSucceeds(deleteDoc(doc(db, "data", "laPrincesa_inventory_v2")));
  await assertSucceeds(deleteDoc(doc(db, "data", "laPrincesa_deleted_orders_v1")));
  await assertSucceeds(deleteDoc(doc(db, "data", "laPrincesa_activity_log_v1")));
  await assertSucceeds(deleteDoc(doc(db, "data", "laPrincesa_orders_v2~p0")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_customers_v2")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_machines")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_printer_mode_v1")));
});

test("Staff can read and write shared POS data", async () => {
  const db = env.authenticatedContext("staff-uid", {
    email: "cashier@laprincesa-pos.app"
  }).firestore();

  await assertSucceeds(getDoc(doc(db, "data", "sample")));
  await assertSucceeds(setDoc(doc(db, "data", "staff-write"), { value: 2 }));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_orders_v2")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_inventory_v2")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_deleted_orders_v1")));
  await assertFails(deleteDoc(doc(db, "data", "laPrincesa_activity_log_v1")));
});

test("users without a provisioned role cannot access POS data", async () => {
  const db = env.authenticatedContext("unprovisioned-uid", {
    email: "new@example.com"
  }).firestore();

  await assertFails(getDoc(doc(db, "data", "sample")));
  await assertFails(setDoc(doc(db, "data", "blocked"), { value: 3 }));
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
