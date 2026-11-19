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

function ownerDb() {
  return env.authenticatedContext("owner-uid", {
    email: "owner@example.com"
  }).firestore();
}

function staffDb() {
  return env.authenticatedContext("staff-uid", {
    email: "cashier@laprincesa-pos.app"
  }).firestore();
}

test("unauthenticated users cannot read or write POS data", async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, "data", "sample")));
  await assertFails(setDoc(doc(db, "data", "unauthorized"), { value: true }));
});

test("Owner and Staff can read and write normal shared POS data", async () => {
  await assertSucceeds(getDoc(doc(ownerDb(), "data", "sample")));
  await assertSucceeds(setDoc(doc(ownerDb(), "data", "owner-write"), { value: 1 }));
  await assertSucceeds(getDoc(doc(staffDb(), "data", "sample")));
  await assertSucceeds(setDoc(doc(staffDb(), "data", "staff-write"), { value: 2 }));
});

test("legacy Staff operational writes are accepted only before the first clear", async () => {
  await assertSucceeds(setDoc(doc(staffDb(), "data", "laPrincesa_orders_v2"), {
    v: "[]", parts: 0, t: 1, by: "cashier"
  }));
});

test("only Owner can atomically increment generation and publish the four empty markers", async () => {
  const owner = ownerDb();
  const batch = writeBatch(owner);
  const next = 1;
  batch.set(doc(owner, "meta", "clear_generation"), {
    generation: next, t: 2, by: "Sheena Marie Cuizon"
  });
  for (const key of ["orders_v2", "inventory_v2", "deleted_orders_v1", "activity_log_v1"]) {
    batch.set(doc(owner, "data", `laPrincesa_${key}`), {
      v: "[]", parts: 0, t: 2, by: "Sheena Marie Cuizon", g: next
    });
  }
  await assertSucceeds(batch.commit());

  const staff = staffDb();
  const staffBatch = writeBatch(staff);
  staffBatch.set(doc(staff, "meta", "clear_generation"), {
    generation: 2, t: 3, by: "cashier"
  });
  await assertFails(staffBatch.commit());
});

test("Staff writes with the current generation succeed; stale and untagged writes fail", async () => {
  await assertSucceeds(setDoc(doc(staffDb(), "data", "laPrincesa_orders_v2"), {
    v: "[]", parts: 0, t: 4, by: "cashier", g: 1
  }));
  await assertSucceeds(setDoc(doc(staffDb(), "data", "laPrincesa_inventory_v2~p0"), {
    s: "[]", g: 1
  }));
  await assertFails(setDoc(doc(staffDb(), "data", "laPrincesa_orders_v2"), {
    v: "[{\"old\":true}]", parts: 0, t: 5, by: "offline-cashier"
  }));
  await assertFails(setDoc(doc(staffDb(), "data", "laPrincesa_orders_v2"), {
    v: "[{\"old\":true}]", parts: 0, t: 5, by: "offline-cashier", g: 0
  }));
});

test("only Owner can remove obsolete operational chunks; root markers cannot be deleted", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "data", "laPrincesa_orders_v2~p9"), { s: "obsolete", g: 1 });
  });
  await assertFails(deleteDoc(doc(staffDb(), "data", "laPrincesa_orders_v2~p9")));
  await assertSucceeds(deleteDoc(doc(ownerDb(), "data", "laPrincesa_orders_v2~p9")));
  await assertFails(deleteDoc(doc(ownerDb(), "data", "laPrincesa_orders_v2")));
});

test("users without a provisioned role cannot access POS data", async () => {
  const db = env.authenticatedContext("unprovisioned-uid", {
    email: "new@example.com"
  }).firestore();
  await assertFails(getDoc(doc(db, "data", "sample")));
  await assertFails(setDoc(doc(db, "data", "blocked"), { value: 3 }));
});

test("Staff can read only their own role and cannot manage roles", async () => {
  const db = staffDb();
  await assertSucceeds(getDoc(doc(db, "roles", "staff-uid")));
  await assertFails(getDoc(doc(db, "roles", "owner-uid")));
  await assertFails(getDocs(collection(db, "roles")));
  await assertFails(setDoc(doc(db, "roles", "another-staff"), {
    role: "staff", email: "another@laprincesa-pos.app"
  }));
  await assertFails(updateDoc(doc(db, "roles", "staff-uid"), { role: "owner" }));
  await assertFails(deleteDoc(doc(db, "roles", "staff-uid")));
});

test("Owner can manage Staff roles but cannot create or alter another Owner", async () => {
  const db = ownerDb();
  await assertSucceeds(setDoc(doc(db, "roles", "new-staff"), {
    role: "staff", username: "newcashier", email: "newcashier@laprincesa-pos.app"
  }));
  await assertSucceeds(updateDoc(doc(db, "roles", "new-staff"), {
    username: "cashier-updated"
  }));
  await assertSucceeds(deleteDoc(doc(db, "roles", "new-staff")));
  await assertFails(setDoc(doc(db, "roles", "second-owner"), {
    role: "owner", email: "second@example.com"
  }));
  await assertFails(updateDoc(doc(db, "roles", "owner-uid"), {
    name: "Changed Owner"
  }));
  await assertFails(deleteDoc(doc(db, "roles", "owner-uid")));
});
