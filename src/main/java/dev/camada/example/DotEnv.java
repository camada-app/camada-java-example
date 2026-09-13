package dev.camada.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal .env reader (KEY=value lines, no quoting) so the example has zero extra dependencies.
 */
final class DotEnv {
  private DotEnv() {}

  private static final Pattern LINE = Pattern.compile("^([A-Z_]+)=(.*)$");

  static Map<String, Object> load(Path file) {
    Map<String, Object> out = new LinkedHashMap<>();
    try {
      for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
        Matcher m = LINE.matcher(line.strip());
        if (m.matches()) {
          out.put(m.group(1), m.group(2));
        }
      }
    } catch (IOException e) {
      // no .env: rely on the environment
    }
    return out;
  }
}
