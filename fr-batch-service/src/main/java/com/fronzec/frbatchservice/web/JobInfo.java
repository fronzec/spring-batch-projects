/* 2024 */
package com.fronzec.frbatchservice.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;

public record JobInfo(HashMap<String, String> info) {
    public JobInfo() {
        this(new HashMap<>());
    }

    @JsonIgnore
    public void addInfo(String key, String value) {
        info.put(key, value);
    }
}
