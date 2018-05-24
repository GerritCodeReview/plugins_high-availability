package com.ericsson.gerrit.plugins.highavailability.event;

import com.google.gerrit.server.events.Event;

public class ChangeIndexedEvent extends Event {

  public ChangeIndexedEvent() {
    super("change-indexed");
  }
}
