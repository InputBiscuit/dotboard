/* Overpass query for tools/platforms/build_platforms.py: every Great Britain station with a CRS code,
   the platforms in its stop_area relations, and platforms within 300 m (for stations with no stop_area). */
[out:json][timeout:900];
area["ISO3166-1"="GB"]["admin_level"="2"]->.gb;
nwr["railway"~"^(station|halt)$"]["ref:crs"](area.gb)->.st;
.st out tags center;
(rel(bn.st)["public_transport"="stop_area"]; rel(bw.st)["public_transport"="stop_area"];)->.sa;
.sa out body;
(node(r.sa); way(r.sa);)->.m;
(nwr.m["railway"~"^(platform|platform_edge)$"]; nwr.m["public_transport"="platform"];);
out tags;
nwr(around.st:300)["railway"~"^(platform|platform_edge)$"]["ref"];
out tags center;
