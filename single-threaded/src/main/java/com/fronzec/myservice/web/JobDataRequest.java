package com.fronzec.myservice.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import com.fronzec.myservice.utils.JsonUtils;

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

  @Override
  public String toString() {
    return JsonUtils.parseObject2Json(this);
  }
}
