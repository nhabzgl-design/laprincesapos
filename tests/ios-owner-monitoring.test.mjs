import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import test from "node:test";
import assert from "node:assert/strict";

const html = readFileSync("ios-owner/index.html", "utf8");
const start = html.indexOf("function safeArray(");
const end = html.indexOf("function esc(", start);
assert.ok(start >= 0 && end > start, "Owner data helpers must exist");
const source = html.slice(start, end) + "\nthis.ownerMetrics=ownerMetrics; this.safeArray=safeArray;";
const context = { Date, Number, Set, Intl, Math };
runInNewContext(source, context, { timeout: 1000 });

const today = new Date().toISOString();
const yesterday = new Date(Date.now() - 86400000).toISOString();
const data = {
  laPrincesa_orders_v2: [
    {id:"paid",total:90,discount:10,collected:90,date:today,payments:[{amount:90,at:today}]},
    {id:"partial",total:180,discount:20,collected:50,date:today,payments:[{amount:50,at:today}]},
    {id:"unpaid",total:100,discount:0,collected:0,date:today},
    {id:"older",total:45,discount:5,collected:45,date:yesterday,payments:[{amount:45,at:today}]},
    {id:"void",total:999,discount:1,collected:999,date:today}
  ],
  laPrincesa_deleted_orders_v1:[{id:"void",deletedAt:today}],
  laPrincesa_expenses_v2:[{amount:125,date:today},{amount:50,date:yesterday}],
  laPrincesa_inventory_v2:[{id:"i1"},{id:"i2"}],
  laPrincesa_customers_v2:[{id:"c1"}]
};
const isToday = value => value === today;
test("Owner metrics reconcile today's gross, net, collections and active order count",()=>{
  const m=context.ownerMetrics(data,isToday);
  assert.equal(m.gross,400);
  assert.equal(m.net,370);
  assert.equal(m.collections,185);
  assert.equal(m.orders,3);
  assert.equal(m.expenses,125);
  assert.equal(m.inventory,2);
  assert.equal(m.customers,1);
  assert.equal(m.balance,230);
});
test("Owner metrics helper safely handles missing or malformed arrays",()=>{
  const m=context.ownerMetrics({},()=>false);
  assert.equal(m.orders,0);
  assert.equal(m.gross,0);
  assert.equal(m.balance,0);
});
