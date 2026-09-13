package dev.camada.example;

import dev.camada.Options;
import dev.camada.servlet.CamadaFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * The install: CamadaFilter first in the chain, so camada answers before routing. The SDK reads
 * CAMADA_* from the environment; here that environment is Spring's, so a .env loaded as default
 * properties and a test's {@code @DynamicPropertySource} reach the SDK the way exported variables
 * do (an exported variable still wins). The engine itself is built lazily, on the first request.
 */
@Configuration
class CamadaConfig {
  static final List<String> KEYS =
      List.of(
          "CAMADA_KEY",
          "CAMADA_TOKEN",
          "CAMADA_SNAPSHOT_TOKEN",
          "CAMADA_INGEST_URL",
          "CAMADA_SNAPSHOT_URL",
          "CAMADA_TRUSTED_PROXY",
          "CAMADA_SERVERLESS",
          "CAMADA_CHALLENGE",
          "CAMADA_DISABLED");

  @Bean
  FilterRegistrationBean<CamadaFilter> camadaFilter(Environment spring) {
    Map<String, String> env = new LinkedHashMap<>();
    for (String key : KEYS) {
      String v = spring.getProperty(key);
      if (v != null) {
        env.put(key, v);
      }
    }
    FilterRegistrationBean<CamadaFilter> reg =
        new FilterRegistrationBean<>(new CamadaFilter(new Options().env(env)));
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE); // before everything else, error pages included
    return reg;
  }
}
