package com.datadog.profiling.controller;

import lombok.Getter;

public enum RecordingType {
  PERIODIC("periodic"),
  CONTINUOUS("continuous");

  @Getter private final String name;

  RecordingType(final String name) {
    this.name = name;
  }
}
