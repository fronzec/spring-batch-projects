package com.fronzec.singlethreaded.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;

public class JobInfo {

  private HashMap<String, String> info = new HashMap<>();

  public HashMap<String, String> getInfo() {
    return info;
  }

  public void setInfo(HashMap<String, String> info) {
    this.info = info;
  }

  @JsonIgnore
  public void addInfo(String key, String value) {
    info.put(key, value);
  }
}
