import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import assert from "node:assert/strict";
import test from "node:test";

const html = readFileSync("index.html", "utf8");
const start = html.indexOf("function lpFin(");
const end = html.indexOf("function lpR(", start);
assert.ok(start >= 0 && end > start, "Finance functions must exist in index.html");

const finance = {};
runInNewContext(html.slice(start, end), { Date, Number, Array, Math }, { timeout: 1000 });
// Re-evaluate into a context so the actual production functions are tested.
const context = { Date, Number, Array, Math };
runInNewContext(html.slice(start, end) + "\nthis.lpStats = lpStats;", context, { timeout: 1000 });

const today = new Date().toISOString();
const twoDaysAgo = new Date(Date.now() - 2 * 86400000).toISOString();
const orders = [
  { id: "paid", total: 90, discount: 10, paid: 90, collected: 90, date: today, payments: [{ amount: 90, at: today }] },
  { id: "partial", total: 180, discount: 20, paid: 50, collected: 50, date: today, payments: [{ amount: 50, at: today }] },
  { id: "unpaid", total: 100, discount: 0, paid: 0, collected: 0, date: today, payments: [] },
  { id: "deleted", total: 999, discount: 1, paid: 999, collected: 999, date: today, deletedAt: today, payments: [{ amount: 999, at: today }] },
  { id: "older-order-paid-today", total: 45, discount: 5, paid: 45, collected: 45, date: twoDaysAgo, payments: [{ amount: 45, at: today }] }
];

test("Gross Sales includes paid, partial and unpaid active orders, restores discounts, and excludes deleted orders", () => {
  const result = context.lpStats(orders, [], false);
  assert.equal(result.gross, 450);
  assert.equal(result.discounts, 35);
  assert.equal(result.count, 4);
});

test("Collections use payment dates while Gross Sales uses order creation dates", () => {
  const result = context.lpStats(orders, [], true);
  assert.equal(result.gross, 400);
  assert.equal(result.discounts, 30);
  assert.equal(result.count, 3);
  assert.equal(result.collections, 185);
});

test("Deleted orders do not inflate Collections or Outstanding Balance", () => {
  const result = context.lpStats(orders, [], false);
  assert.equal(result.collections, 185);
  assert.equal(result.balance, 230);
});
