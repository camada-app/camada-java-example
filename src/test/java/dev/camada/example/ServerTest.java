package dev.camada.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.camada.Camada;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The example against a configured engine that can never load a snapshot: the analyst URL is a
 * closed port, so every request runs cold and falls open, and the route the app gates itself
 * (challenge) still works because the challenge kit needs no snapshot. A real Tomcat on a random
 * port, so the filter, the peer and the forwarded-headers stance are production's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerTest {
  static final String BLOCKED_IP = "203.0.113.66";
  static final Pattern RID = Pattern.compile("[0-9a-f-]{36}");

  @DynamicPropertySource
  static void camadaEnv(DynamicPropertyRegistry r) throws IOException {
    String dead;
    try (ServerSocket s = new ServerSocket(0)) {
      dead = "http://127.0.0.1:" + s.getLocalPort();
    }
    r.add("CAMADA_KEY", () -> "tok-example.snap-example");
    r.add("CAMADA_INGEST_URL", () -> dead);
    r.add("CAMADA_SNAPSHOT_URL", () -> dead + "/snapshot");
    r.add("CAMADA_TRUSTED_PROXY", () -> "hops:1");
    r.add("CAMADA_DISABLED", () -> "");
  }

  @AfterAll
  static void stopEngine() {
    Camada.resetDefault(); // the lazy default this context built: stop its threads
  }

  @Autowired TestRestTemplate http;

  ResponseEntity<String> get(String path, String... headerPairs) {
    HttpHeaders h = new HttpHeaders();
    for (int i = 0; i < headerPairs.length; i += 2) {
      h.add(headerPairs[i], headerPairs[i + 1]);
    }
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class);
  }

  ResponseEntity<String> postForm(String path, String body) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
  }

  static String header(ResponseEntity<?> r, String name) {
    return r.getHeaders().getFirst(name);
  }

  @Test
  void pagesRenderWithTheFirstPartyBeacon() {
    ResponseEntity<String> first = get("/");
    assertEquals(200, first.getStatusCode().value());
    assertTrue(header(first, "set-cookie").startsWith("_sfp="));
    for (String path : new String[] {"/", "/pricing", "/login-form"}) {
      ResponseEntity<String> r = get(path);
      assertEquals(200, r.getStatusCode().value(), path);
      String rid = header(r, "x-rid");
      assertNotNull(rid);
      assertTrue(RID.matcher(rid).matches());
      assertTrue(r.getBody().contains("/_cam/b.js?r=" + rid));
    }
    ResponseEntity<String> js = get("/_cam/b.js");
    assertEquals("application/javascript", header(js, "content-type"));
  }

  @Test
  void apiAnswersJson() {
    ResponseEntity<String> r = get("/api/data");
    assertEquals(200, r.getStatusCode().value());
    assertTrue(r.getBody().contains("\"ok\":true"));
  }

  @Test
  void loginReportsTheOutcome() {
    ResponseEntity<String> bad = postForm("/login", "user=demo%40example.com&pass=nope");
    assertEquals(401, bad.getStatusCode().value());
    assertTrue(bad.getBody().contains("login failed"));
    ResponseEntity<String> ok = postForm("/login", "user=demo%40example.com&pass=demo");
    assertEquals(200, ok.getStatusCode().value());
    assertTrue(ok.getBody().contains("login succeeded"));
    // a body that is not UTF-8 is a failed login, not a 500
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    byte[] raw =
        new byte[] {'u', 's', 'e', 'r', '=', (byte) 0xff, '&', 'p', 'a', 's', 's', '=', 'x'};
    ResponseEntity<String> r =
        http.exchange("/login", HttpMethod.POST, new HttpEntity<>(raw, h), String.class);
    assertEquals(401, r.getStatusCode().value());
  }

  @Test
  void theEngineReadsThisTestsEnvironment() {
    get("/"); // the lazy first-request build, as in production
    Camada engine = Camada.getDefault();
    assertNotNull(engine.env());
    assertTrue(engine.env().snapshotUrl().startsWith("http://127.0.0.1:"));
    assertFalse(engine.disabled());
  }

  @Test
  void aColdSnapshotFallsOpen() {
    ResponseEntity<String> r = get("/", "x-forwarded-for", BLOCKED_IP);
    assertEquals(200, r.getStatusCode().value());
    assertNull(header(r, "x-block-reason"));
    assertNotNull(header(r, "x-rid"));
  }

  @Test
  void challengeMeServesThePageToABrowser() {
    ResponseEntity<String> r =
        get(
            "/challenge-me",
            "x-forwarded-for",
            "198.51.100.7",
            "accept",
            "text/html",
            "sec-fetch-dest",
            "document");
    assertEquals(403, r.getStatusCode().value());
    assertEquals("1", header(r, "x-camada-challenge"));
    assertTrue(header(r, "content-type").startsWith("text/html"));
    assertTrue(r.getBody().contains("/__camada/challenge"));
  }

  @Test
  void challengeMeAnswersJsonToAnApiClient() {
    ResponseEntity<String> r = get("/challenge-me", "x-forwarded-for", "198.51.100.7");
    assertEquals(403, r.getStatusCode().value());
    assertEquals("{\"error\":\"challenge_required\"}", r.getBody());
  }

  @Test
  void unknownPathRendersThe404Page() {
    ResponseEntity<String> r = get("/nope");
    assertEquals(404, r.getStatusCode().value());
    assertTrue(r.getBody().contains("Nothing here"));
    assertNotNull(header(r, "x-rid"));
  }

  /** The one place this repo names the SDK version: pom.xml's {@code camada.version} property. */
  static String pinnedSdkVersion() throws IOException {
    Matcher pinned =
        Pattern.compile("<camada.version>([^<]+)</camada.version>")
            .matcher(Files.readString(Paths.get("pom.xml"), StandardCharsets.UTF_8));
    assertTrue(pinned.find(), "pom.xml has no camada.version property");
    return pinned.group(1);
  }

  /**
   * pom.xml pins dev.camada:camada the way uv.lock pins a path dependency: the number must be the
   * sibling checkout's current version, or the example runs an SDK the repo no longer describes.
   */
  @Test
  void pomRecordsTheSiblingSdkVersion() throws IOException {
    Path sdkPom = Paths.get("..", "camada-java", "pom.xml");
    assertTrue(
        Files.exists(sdkPom),
        "no camada-java checkout beside this repo (" + sdkPom.toAbsolutePath().normalize() + ")");
    Matcher shipped =
        Pattern.compile("<artifactId>camada</artifactId>\\s*<version>([^<]+)</version>")
            .matcher(Files.readString(sdkPom, StandardCharsets.UTF_8));
    assertTrue(shipped.find());
    assertEquals(
        shipped.group(1), pinnedSdkVersion(), "pom.xml is behind camada-java: bump camada.version");
  }

  /**
   * The SDK on the classpath is the one pom.xml pins: its published version constant (the wire
   * identity {@code x-camada-sdk: @camada/java/<version>}) must be the property's value, or the
   * local repository holds a stale install (re-run {@code mvn -f ../camada-java/pom.xml install}).
   */
  @Test
  void theSdkOnTheClasspathIsThePinnedVersion() throws IOException {
    assertEquals(
        pinnedSdkVersion(),
        dev.camada.Version.VERSION,
        "the installed dev.camada:camada is not the version pom.xml pins");
  }

  /**
   * A bump is one edit: the README points at the property instead of repeating the number, so no
   * literal SDK version may appear there.
   */
  @Test
  void theReadmeDoesNotRepeatTheSdkVersion() throws IOException {
    String readme = Files.readString(Paths.get("README.md"), StandardCharsets.UTF_8);
    assertFalse(
        readme.contains("dev.camada:camada:"),
        "README.md hardcodes the SDK version: point at pom.xml's camada.version instead");
    assertFalse(
        readme.contains(pinnedSdkVersion()),
        "README.md repeats the SDK version literal " + pinnedSdkVersion());
  }
}
