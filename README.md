# camada-java-example

A Spring Boot app wired with [`camada`](../camada-java) against a locally running edge-analyst.
This is the hand-test bench for the Java SDK, the twin of
[`camada-python-example`](../camada-python-example).

## Setup

1. Terminal A — `cd ../camada/edge-analyst && npm run dev` (analyst on :8787), then
   `npm run seed` in a second terminal. Note the printed `CAMADA_KEY`.
2. Here: `cp .env.example .env` (paste the key if it differs), then
   `mvn -q -f ../camada-java/pom.xml install -DskipTests` once — Maven has no path dependencies,
   so the sibling checkout is installed into your local repository as `dev.camada:camada` at the
   version `pom.xml`'s `camada.version` property pins (re-run it after changing the SDK) — and
   `mvn spring-boot:run` → http://localhost:3006.
   Or build the jar: `mvn -q -DskipTests package && java -jar target/camada-java-example.jar`.

`server.forward-headers-strategy` must stay `none` (Spring Boot's default off cloud platforms;
`application.properties` says so explicitly): `native` would have Tomcat's `RemoteIpValve` rewrite
the peer from `X-Forwarded-For` for any client, before camada's trusted-proxy rules
(`CAMADA_TRUSTED_PROXY`, or the tenant config) get to judge the header.

The SDK reads `CAMADA_*` from the environment. `.env` is loaded as Spring default properties
(the lowest precedence), so an exported variable — `CAMADA_DISABLED=1 java -jar …` — still wins
over the file, and the filter is handed the merged values through `Options.env(...)`.

## Hand test (what to look for)

1. Browse `http://localhost:3006` — the page renders; devtools → Network shows
   `/_cam/b.js?r=<uuid>` (200, JavaScript) and ~2 s later `POST /_cam/fp` (204). The document
   response carries an `x-rid` header and sets the `_sfp` cookie.
2. Click/type before the beacon fires — the `/_cam/fp` payload's `input` counters are non-zero.
3. `curl -i http://localhost:3006/ -H 'X-Forwarded-For: 203.0.113.66'` → **403** with
   `x-block-reason: rule` and `x-block-rule: builtin:block` (the seed blocks that IP as an entry
   of the built-in block list on snapshot v5). The very first request after boot answers **200**
   instead — see below.
4. Fail three logins (`demo@example.com` / anything wrong) via `/login-form`, then run an
   analysis (`curl -s -X POST -H 'authorization: Bearer dev' 'http://localhost:8787/admin/run?tenant=acme&minutes=10'`) —
   events include `login_failed` rows with a hashed `uid`; your raw email appears nowhere.
5. Kill the analyst (Ctrl-C in terminal A) and reload the page + `curl localhost:3006/api/data` —
   everything still answers 200; the app's console shows at most one
   `[camada] suppressed error` line a minute (the `camada` JUL logger, bridged into Logback by
   Spring Boot). That is fail-open.
6. Restart this app with `CAMADA_DISABLED=1` — no `x-rid` header, no `/_cam/b.js` requests:
   the kill switch bypasses the SDK entirely.

## The first request after boot is cold

The filter builds one engine lazily, on the first request through it. That build starts the
snapshot poll on a daemon thread and never blocks, so the request that triggered it is matched
against an empty snapshot and falls open: a `curl` from the blocked IP right after boot gets `200`
with an `x-rid`, and requests keep passing until that first poll lands — a few hundred
milliseconds against a local analyst (snapshot-size and network bound), then `403`.
`node scripts/e2e-sdk-java.mjs` in `../camada/edge-analyst` makes that first request itself and
asserts both answers.

If request 1 must be enforced, warm the engine at startup by waiting for the boot poll — a bounded
loop, so an unreachable analyst leaves it cold and the app still starts and fails open:

```java
@Bean
ApplicationRunner warmCamada(FilterRegistrationBean<CamadaFilter> camada) {
  // engine() builds the default engine with the filter's options (the Spring-merged CAMADA_*);
  // the boot poll is already running on its thread when warmUp() starts waiting
  return args -> camada.getFilter().engine().warmUp(5000);
}
```

`snapshot().refresh()` is not the warm-up: the boot poll holds the single-in-flight lock, so a
synchronous `refresh()` called right after the build returns at once and the engine is still
cold. `warmUp` returns false at once — inert — when `CAMADA_KEY` is unset or `CAMADA_DISABLED=1`.
Go through the filter's `engine()` rather than a bare `Camada.getDefault()`: called first, that
would build the default from `System.getenv()` alone and skip the `.env` values.

## Challenge (SDK-04)

`/challenge-me` forces the first-party proof-of-work challenge, whatever the snapshot says — the
same page camada serves automatically for a `challenge` verdict.

1. Browse `http://localhost:3006/challenge-me` — "Checking your browser" appears, the inline
   solver hunts a SHA-256 with 16 leading zero bits (tens of milliseconds), the hidden form
   posts to `/__camada/challenge`, and the browser lands on "Challenge passed". Devtools shows
   the `_cch` cookie (`HttpOnly`, `SameSite=Lax`, one hour); reload and the page renders at once.
2. `curl -i http://localhost:3006/challenge-me -H 'accept: text/html' -H 'sec-fetch-dest: document'`
   → **403** with `x-camada-challenge: 1` and the page in the body.
3. Without an HTML `Accept` (an API client, an image, a fetch) the answer is
   `403 {"error":"challenge_required"}` instead — a status a client can act on rather than a
   page it cannot solve.
4. The events tell the two apart: a served challenge ships `st: 403, blk: "challenge"`, a passed
   one ships `st: 200, ch: 1`.

The nonce and the cookie are bound to the client IP, so camada serves no challenge to a request
it cannot identify (no trusted-proxy config and no socket address). Clear `_cch` — or open a
private window — to see the check again.

## Tests

`mvn -q verify` (after the SDK `install` above) — `spotless:check`, `-Werror`, then the routes on
a real Tomcat (`@SpringBootTest`, random port) against an engine whose analyst URL is a closed
port (cold, fail open; the challenge needs no snapshot), plus a guard that `pom.xml`'s
`camada.version` is the one place the SDK version lives: it must match the sibling checkout and the
`dev.camada.Version.VERSION` on the classpath, and the README may not repeat the literal (bump
`camada.version` after bumping `camada-java`, nothing else).
