package jumpking.config;

import jumpking.repository.SchemaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaValidator implements ApplicationRunner {
  private final SchemaRepository schema;

  public SchemaValidator(SchemaRepository schema) {
    this.schema = schema;
  }

  @Override
  public void run(ApplicationArguments args) {
    validate();
  }

  public void validate() {
    schema.validate();
  }
}
