package com.fronzec.myservice.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;

import org.junit.jupiter.api.Test;

public class JsonUtilsTest {
    @Test
    void testParseObject2Json() {
        String actual1 = JsonUtils.parseObject2Json(null);
        String actual2 = JsonUtils.parseObject2Json(Collections.singletonMap("key", "value"));
        String actual3 = JsonUtils.parseObject2Json("{}");

        assertNotNull(actual1);
        assertNotNull(actual2);
        assertNotNull(actual3);
    }
}
