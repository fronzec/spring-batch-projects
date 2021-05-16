package com.fronzec.myservice.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDataRequest {

    @NotNull
    @JsonFormat(pattern = "dd-MM-yyyy")
    private Date date;

    @PositiveOrZero
    @NotNull
    private Integer tryNumber;

    public JobDataRequest() {
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Integer getTryNumber() {
        return tryNumber;
    }

    public void setTryNumber(Integer tryNumber) {
        this.tryNumber = tryNumber;
    }
}
