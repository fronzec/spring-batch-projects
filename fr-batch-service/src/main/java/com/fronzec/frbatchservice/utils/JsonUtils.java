/* 2024-2025 */
package com.fronzec.frbatchservice.utils;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

public class JsonUtils {

    public static final JsonMapper mapper =
            JsonMapper.builder()
                    .findAndAddModules()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .build();

    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    private JsonUtils() {}

    /**
     * Produce a JSON string representation of the given object.
     *
     * @param object the object to serialize; may be {@code null}
     * @return the object's JSON string representation, or "{}" if the input is {@code null} or serialization fails
     */
    public static String parseObject2Json(final Object object) {
        return Optional.ofNullable(object)
                .map(
                        o -> {
                            var result = "{}";
                            try {
                                result = mapper.writeValueAsString(o);
                            } catch (JacksonException e) {
                                logger.warn(
                                        "Object cannot be parsed to json by -> {}",
                                        e.getMessage(),
                                        e);
                            }
                            return result;
                        })
                .orElseGet(() -> "{}");
    }
}