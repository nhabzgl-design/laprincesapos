import { readFileSync } from "node:fs";
import test from "node:test";
import assert from "node:assert/strict";

const html = readFileSync("index.html", "utf8");

function section(startText, endText) {
  const start = html.indexOf(startText);
  const end = html.indexOf(endText, start);
  assert.notEqual(start, -1, `Missing section: ${startText}`);
  assert.notEqual(end, -1, `Missing section end: ${endText}`);
  return html.slice(start, end);
}

test("Owner Clear Data is restricted to the four approved local data keys", () => {
  const clear = section("function clearAll(){", "setTimeout(function(){try{var n=cnt(snapshot()");
  assert.match(clear, /!window\.__LP_IS_OWNER\s*\|\|\s*!window\.__LP_IS_OWNER\(\)/);
  assert.match(clear, /TARGETS=\['orders_v2','inventory_v2','deleted_orders_v1','activity_log_v1'\]/);
  const local = section("function wipeLocal(result)", "if(cl){");
  assert.match(local, /TARGETS\.forEach\(function\(n\)\{localStorage\.removeItem\(P\+n\)\}\)/);
  assert.doesNotMatch(local, /keys\(\)\.forEach|LPCloud\.wipe|machines|customers_v2|expenses_v2|services_v2/);
});

test("Cloud clear commits only empty markers before deleting approved chunks", () => {
  const cloud = section("L.clearOperationalData=function(){", "L.flushNow=function()");
  assert.match(cloud, /roleName!=='owner'/);
  assert.match(cloud, /names=\['orders_v2','inventory_v2','deleted_orders_v1','activity_log_v1'\]/);
  assert.match(cloud, /v:'\[\]',parts:0/);
  assert.match(cloud, /allowed=\/\^laPrincesa_\(orders_v2\|inventory_v2\|deleted_orders_v1\|activity_log_v1\)\(~p\[0-9\]\+\)\?\$\//);
  assert.ok(cloud.indexOf("markerBatch.commit()") < cloud.indexOf("F.getDocs(F.collection(db,'data'))"));
  assert.match(cloud, /refs\.slice\(i,i\+450\)/);
  assert.match(cloud, /batchRefs\.forEach\(function\(ref\)\{b\.delete\(ref\)\}\)/);
  assert.match(cloud, /catch\(function\(\)\{return false\}\)/);
  assert.doesNotMatch(cloud, /L\.wipe\s*=|base!==keep/);
});

test("Printer and manual cash-drawer hooks remain present", () => {
  assert.match(html, /printHtmlBase64/);
  assert.match(html, /__LP_AUTH_MANUAL_DRAWER/);
});
