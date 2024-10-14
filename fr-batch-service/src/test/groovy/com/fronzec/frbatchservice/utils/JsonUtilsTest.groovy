package com.fronzec.frbatchservice.utils

import spock.lang.Specification

class JsonUtilsTest extends Specification {

    def "test parseObject2Json in:#input out:#expected "() {
        expect:
        JsonUtils.parseObject2Json(input) != expected

        where:
        input                                    || expected
        null                                     || null
        Collections.singletonMap("key", "value") || null
        "{}"                                     || null
    }
}
