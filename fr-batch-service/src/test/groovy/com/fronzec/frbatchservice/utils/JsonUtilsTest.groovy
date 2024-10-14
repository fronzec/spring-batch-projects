package com.fronzec.frbatchservice.utils

import spock.lang.Specification

class JsonUtilsTest extends Specification {

    def "test parseObject2Json"() {
        when:
        def actual1 = JsonUtils.parseObject2Json(null)
        def actual2 = JsonUtils.parseObject2Json(Collections.singletonMap("key", "value"))
        def actual3 = JsonUtils.parseObject2Json("{}")
        then:
        actual1 != null
        actual2 != null
        actual3 != null
    }
}
