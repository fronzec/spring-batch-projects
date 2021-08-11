package com.fronzec.myservice.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonUtils {

  public static final ObjectMapper mapper = new ObjectMapper();

  private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

  private JsonUtils() {
  }

  public static String parseObject2Json(final Object object) {
    return Optional.ofNullable(object)
            .map(o -> {
              var result = "{}";
              try {
                result = mapper.writeValueAsString(o);
              } catch (JsonProcessingException e) {
                logger.warn("Object cannot be parsed to json by -> {}", e.getMessage(), e);
              }
              return result;
            })
            .orElseGet(() -> "{}");
  }

}
