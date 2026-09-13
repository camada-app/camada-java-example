package dev.camada.example;

import dev.camada.Camada;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The routes of the hand-test bench: the pages, the login form, an API, and a gated route. */
@RestController
class ShopController {
  static ResponseEntity<String> page(HttpServletRequest req, String title, String body) {
    return page(req, title, body, HttpStatus.OK);
  }

  static ResponseEntity<String> page(
      HttpServletRequest req, String title, String body, HttpStatus status) {
    String html =
        "<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>"
            + title
            + "</title>"
            + Camada.scriptTag(req)
            + "</head>\n<body style=\"font-family: system-ui; max-width: 40rem; margin: 3rem auto\">\n"
            + "<nav><a href=\"/\">home</a> · <a href=\"/pricing\">pricing</a> · <a href=\"/login-form\">login</a> · <a href=\"/challenge-me\">challenge</a></nav>\n"
            + "<h1>"
            + title
            + "</h1>"
            + body
            + "</body></html>";
    return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
  }

  @GetMapping("/")
  ResponseEntity<String> home(HttpServletRequest req) {
    return page(
        req,
        "camada example shop",
        """

          <p>Every request here is captured by camada; the beacon below fingerprints this browser first-party.</p>
          <p><button onclick="fetch('/api/data').then(r=>r.json()).then(d=>alert(JSON.stringify(d)))">call the API</button></p>""");
  }

  @GetMapping("/pricing")
  ResponseEntity<String> pricing(HttpServletRequest req) {
    return page(req, "Pricing", "<p>Free while unreleased.</p>");
  }

  @GetMapping("/login-form")
  ResponseEntity<String> loginForm(HttpServletRequest req) {
    return page(
        req,
        "Log in",
        """

          <form method="post" action="/login">
            <input name="user" placeholder="email"> <input name="pass" type="password"> <button>go</button>
          </form>""");
  }

  @PostMapping("/login")
  ResponseEntity<String> login(HttpServletRequest req) {
    // the container parses the urlencoded form; a body that is not UTF-8 is a failed login, not a
    // 500
    String user = req.getParameter("user");
    boolean ok = "demo@example.com".equals(user) && "demo".equals(req.getParameter("pass"));
    // uid is HMAC-hashed in the SDK; the raw email never reaches the queue
    Camada.track(req, ok ? "login_succeeded" : "login_failed", user == null ? "" : user);
    return page(
        req,
        ok ? "Welcome" : "Nope",
        "<p>login " + (ok ? "succeeded" : "failed") + "</p>",
        ok ? HttpStatus.OK : HttpStatus.UNAUTHORIZED);
  }

  @GetMapping("/api/data")
  Map<String, Object> apiData() {
    return Map.of("ok", true, "at", System.currentTimeMillis());
  }

  /**
   * SDK-04 demo: force the challenge for this route, whatever the snapshot says. In production the
   * same page is served automatically for a `challenge` verdict. Once solved, the `_cch` cookie is
   * good for an hour and this route renders normally.
   */
  @GetMapping("/challenge-me")
  ResponseEntity<String> challengeMe(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (Camada.serveChallenge(req, res)) {
      return null; // the page is written and the response committed
    }
    return page(
        req,
        "Challenge passed",
        """

            <p>The <code>_cch</code> cookie is set for an hour. Clear it (or open a private window) to see the check again.</p>""");
  }
}
