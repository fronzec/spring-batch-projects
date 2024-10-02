/* (C)2024 */
package com.fronzec.frbatchservice.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fronzec.frbatchservice.utils.JsonUtils;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AllJobsDataRequest {

    @NotNull
    @JsonFormat(pattern = "dd-MM-yyyy")
    private Date date;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate localDate;

    @PositiveOrZero @NotNull private Integer tryNumber;

    public AllJobsDataRequest() {}

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

    public LocalDate getLocalDate() {
        return localDate;
    }

    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }

    @Override
    public String toString() {
        return JsonUtils.parseObject2Json(this);
    }
}
