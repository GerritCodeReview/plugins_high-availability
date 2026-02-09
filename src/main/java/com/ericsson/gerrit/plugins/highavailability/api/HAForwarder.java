package com.ericsson.gerrit.plugins.highavailability.api;

public interface HAForwarder {
  void forward(String message);
}
