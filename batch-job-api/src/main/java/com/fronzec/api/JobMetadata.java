package com.fronzec.api;

import java.time.Duration;
import java.util.List;

public interface JobMetadata {

  String getDisplayName();

  String getDescription();

  String getAuthor();

  List<String> getTags();

  Duration getEstimatedRuntime();
}
