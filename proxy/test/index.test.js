import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import worker, { CACHE_SECONDS, UPSTREAM } from "../src/index.js";

let calls;
beforeEach(() => {
  calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    return new Response(JSON.stringify({ locationName: "London Paddington" }), { status: 200 });
  };
});

const env = { RDM_API_KEY: "test-key" };
const get = (path, init) => worker.fetch(new Request(`https://proxy.example${path}`, init), env);

test("forwards a board request with the secret key and caches it", async () => {
  const res = await get("/v1/board/PAD");
  assert.equal(res.status, 200);
  assert.equal(res.headers.get("Cache-Control"), `public, max-age=${CACHE_SECONDS}`);
  assert.deepEqual(await res.json(), { locationName: "London Paddington" });

  const upstream = new URL(calls[0].url);
  assert.equal(upstream.origin + upstream.pathname, UPSTREAM + "PAD");
  assert.equal(upstream.searchParams.get("numRows"), "10");
  assert.equal(upstream.searchParams.get("filterCrs"), null);
  assert.equal(calls[0].init.headers["x-apikey"], "test-key");
});

test("passes the calling-at filter through", async () => {
  await get("/v1/board/PAD?filter=RDG");
  const upstream = new URL(calls[0].url);
  assert.equal(upstream.searchParams.get("filterCrs"), "RDG");
  assert.equal(upstream.searchParams.get("filterType"), "to");
});

test("bigger boards for platform filtering and platform discovery", async () => {
  await get("/v1/board/RDG?rows=50");
  let upstream = new URL(calls[0].url);
  assert.equal(upstream.searchParams.get("numRows"), "50");
  assert.equal(upstream.searchParams.get("timeWindow"), null);

  await get("/v1/board/RDG?filter=PAD&rows=150");
  upstream = new URL(calls[1].url);
  assert.equal(upstream.searchParams.get("numRows"), "150");
  assert.equal(upstream.searchParams.get("timeWindow"), "120");
  assert.equal(upstream.searchParams.get("filterCrs"), "PAD");
});

test("never returns the key to the client", async () => {
  const res = await get("/v1/board/PAD");
  assert.ok(!(await res.text()).includes("test-key"));
  assert.ok(![...res.headers.values()].some((v) => v.includes("test-key")));
});

test("rejects non-canonical requests without calling upstream", async () => {
  for (const path of ["/v1/board/pad", "/v1/board/PADD", "/v1/board/PAD?filter=rdg", "/v1/board/PAD?x=1",
    "/v1/board/PAD?filter=RDG&filter=SLO", "/v1/board/PAD?rows=10", "/v1/board/PAD?rows=20",
    "/v1/board/PAD?rows=50&filter=RDG", "/v1/board/PAD?rows=50&rows=150", "/v1/board/PAD?rows=constructor",
    "/other"]) {
    const res = await get(path);
    assert.ok(res.status === 400 || res.status === 404, `${path} → ${res.status}`);
    assert.equal(res.headers.get("Cache-Control"), "no-store");
  }
  assert.equal(calls.length, 0);
});

test("only GET is allowed", async () => {
  assert.equal((await get("/v1/board/PAD", { method: "POST" })).status, 405);
});

test("upstream errors pass through but aren't cached", async () => {
  globalThis.fetch = async () => new Response('{"fault":{}}', { status: 401 });
  const res = await get("/v1/board/PAD");
  assert.equal(res.status, 401);
  assert.equal(res.headers.get("Cache-Control"), "no-store");
});

test("network failure upstream gives 502", async () => {
  globalThis.fetch = async () => { throw new Error("down"); };
  assert.equal((await get("/v1/board/PAD")).status, 502);
});

test("rate limited clients get 429", async () => {
  const limited = { ...env, LIMITER: { limit: async () => ({ success: false }) } };
  const res = await worker.fetch(new Request("https://proxy.example/v1/board/PAD"), limited);
  assert.equal(res.status, 429);
  assert.equal(calls.length, 0);
});

test("missing secret gives 503", async () => {
  const res = await worker.fetch(new Request("https://proxy.example/v1/board/PAD"), {});
  assert.equal(res.status, 503);
});
