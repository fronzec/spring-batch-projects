package com.fronzec.frbatchservice.restclients;

import java.math.BigDecimal;

public class DataCalculatedResponse {

  BigDecimal value;

  public DataCalculatedResponse() {}

  public BigDecimal getValue() {
    return value;
  }

  public void setValue(BigDecimal value) {
    this.value = value;
  }
}
