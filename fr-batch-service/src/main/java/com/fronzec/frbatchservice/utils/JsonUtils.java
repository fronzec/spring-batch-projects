/* 2024 */
package com.fronzec.frbatchservice.utils;


import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

public class JsonUtils {

    public static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    private JsonUtils() {}

    public static String parseObject2Json(final Object object) {
        return Optional.ofNullable(object)
                .map(
                        o -> {
                            var result = "{}";
                            try {
                                result = mapper.writeValueAsString(o);
                            } catch (JsonProcessingException e) {
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
