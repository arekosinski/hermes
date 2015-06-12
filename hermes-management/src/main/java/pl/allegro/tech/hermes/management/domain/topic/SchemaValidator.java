package pl.allegro.tech.hermes.management.domain.topic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static pl.allegro.tech.hermes.api.Topic.ContentType;

@Component
public class SchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);

    private final JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.byDefault();

    private final ObjectMapper objectMapper;

    @Autowired
    public SchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    //TODO refactor - move validators to separate classes
    public void check(String schema, ContentType contentType) {
        switch (contentType) {
            case JSON:
                validateJsonSchema(schema);
                break;
            case AVRO:
                validateAvroSchema(schema);
                break;
            default:
                throw new IllegalStateException("Unsupported content type");
        }
    }

    private void validateAvroSchema(String schema) {
        try {
            new Schema.Parser().parse(schema);
        } catch (SchemaParseException e) {
            throw new InvalidSchemaException(e);
        }
    }

    private void validateJsonSchema(String schema) {
        checkArgument(!isNullOrEmpty(schema), "Message schema cannot be empty");

        List<String> errors = new ArrayList<>();
        try {
            JsonNode schemaNode = objectMapper.readTree(schema);
            ProcessingReport report = jsonSchemaFactory.getSyntaxValidator().validateSchema(schemaNode);

            StreamSupport.stream(report.spliterator(), false).forEach(e -> errors.add(e.getMessage()));
        } catch (IOException e) {
            logger.warn("Exception occurred while validating schema", e);
            throw new InvalidSchemaException(e);
        }

        if (!errors.isEmpty()) {
            throw new InvalidSchemaException(errors);
        }
    }
}
