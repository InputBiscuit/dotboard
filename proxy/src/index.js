// Dotboard API proxy (Cloudflare Worker).
//
// GET /v1/board/{CRS}[?filter={CRS}]
//   → Rail Data Marketplace LDBWS GetDepBoardWithDetails/{CRS}?numRows=10[&filterCrs=…&filterType=to]
//
// The response body is the API's JSON, unchanged. Successful boards are cached for CACHE_SECONDS
// by Workers Cache (see wrangler.jsonc), keyed by path + query, so requests are only accepted in one
// canonical form (upper-case codes, at most one "filter" parameter). The API key lives in the
// RDM_API_KEY secret and is never sent to clients. Nothing is logged.

export const UPSTREAM =
  "https://api1.raildata.org.uk/1010-live-departure-board-dep1_2/LDBWS/api/20220120/GetDepBoardWithDetails/";
export const CACHE_SECONDS = 45;
export const ROWS = "10";

const BOARD_PATH = /^\/v1\/board\/([A-Z]{3})$/;
const CRS = /^[A-Z]{3}$/;

export default {
  async fetch(request, env) {
    if (request.method !== "GET") return error(405, "Method not allowed");

    const url = new URL(request.url);
    if (url.pathname === "/" || url.pathname === "/health") return error(200, "ok");

    const match = url.pathname.match(BOARD_PATH);
    if (!match) return error(404, "Not found");
    const crs = match[1];

    const params = [...url.searchParams.keys()];
    if (params.some((p) => p !== "filter") || url.searchParams.getAll("filter").length > 1) {
      return error(400, "Only a single 'filter' parameter is allowed");
    }
    const filter = url.searchParams.get("filter") ?? "";
    if (filter && !CRS.test(filter)) return error(400, "filter must be a 3-letter upper-case station code");

    if (env.LIMITER) {
      const client = request.headers.get("CF-Connecting-IP") ?? "unknown";
      const { success } = await env.LIMITER.limit({ key: client });
      if (!success) return error(429, "Too many requests", { "Retry-After": "60" });
    }

    if (!env.RDM_API_KEY) return error(503, "Proxy not configured");

    const upstream = new URL(UPSTREAM + crs);
    upstream.searchParams.set("numRows", ROWS);
    if (filter) {
      upstream.searchParams.set("filterCrs", filter);
      upstream.searchParams.set("filterType", "to");
    }

    let response;
    try {
      response = await fetch(upstream, {
        headers: { "x-apikey": env.RDM_API_KEY, Accept: "application/json" },
      });
    } catch {
      return error(502, "Upstream unavailable");
    }

    // Pass the upstream status through (the app maps errors itself), but only cache successes.
    return new Response(response.body, {
      status: response.status,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "Cache-Control": response.ok ? `public, max-age=${CACHE_SECONDS}` : "no-store",
      },
    });
  },
};

function error(status, message, extraHeaders = {}) {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...extraHeaders },
  });
}
