package ai.ghostshift.util;

import io.micronaut.json.JsonMapper;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/** JSON helper used for workflow definitions, AI payloads, and stored metadata. */
@Singleton
public class Json {
    private final JsonMapper mapper;

    public Json(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not serialize local JSON", e);
        }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON value", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readObject(String json) {
        try {
            return mapper.readValue(json, Argument.mapOf(String.class, Object.class));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON object", e);
        }
    }

    public List<Object> readArray(String json) {
        try {
            return mapper.readValue(json, Argument.listOf(Object.class));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON array", e);
        }
    }

    public Map<String, Object> toObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return readObject(json);
    }
}
