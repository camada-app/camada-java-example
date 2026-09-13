package dev.camada.example;

import java.nio.file.Paths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * camada-java-example: a small Spring Boot app wired with camada against a local edge-analyst.
 * Setup: cp .env.example .env (paste the CAMADA_KEY printed by `npm run seed`), then `mvn -q -f
 * ../camada-java/pom.xml install -DskipTests` once and `mvn spring-boot:run`
 * (http://localhost:3006).
 */
@SpringBootApplication
public class ExampleApplication {
  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(ExampleApplication.class);
    // .env fills what the process environment leaves unset: default properties sit below every
    // other property source, so an exported CAMADA_DISABLED=1 still wins over the file
    app.setDefaultProperties(DotEnv.load(Paths.get(".env")));
    app.run(args);
  }
}
