package com.fronzec.myservice.utils;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

public class JsonUtils {

    public static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    private JsonUtils(){}

    public static String parseObject2Json(final Object object) {
        return Optional.ofNullable(object).map(o -> {
            var result = "{}";
            try {
                result = mapper.writeValueAsString(o);
            } catch (JsonProcessingException e) {
                logger.warn("Object cannot be parsed to json by -> {}", e.getMessage(), e);
            }
            return result;
        })
        .orElseGet(() ->  "{}" );
    }

}
