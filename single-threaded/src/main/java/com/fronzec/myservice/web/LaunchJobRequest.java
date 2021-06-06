package com.fronzec.myservice.web;

import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fronzec.myservice.utils.JsonUtils;

@JsonNaming(value = PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LaunchJobRequest {

    /**
     * Spring batch job bean name to instance a new job dinamically
     */
    @NotNull
    @NotEmpty
    private String jobBeanName;

    /**
     * Params to use in our job, this fields must be validated to check required fields and
     * correct values for each job type
     */
    private HashMap<String, String> params = new HashMap<>();

    public String getJobBeanName() {
        return jobBeanName;
    }

    public void setJobBeanName(String jobBeanName) {
        this.jobBeanName = jobBeanName;
    }

    public Map<String, String> getParams() {
        return params;
    }

    @JsonAnySetter
    public void setParam(String key, String value) {
        this.params.put(key, value);
    }

    @Override
    public String toString() {
        return JsonUtils.parseObject2Json(this);
    }

}
