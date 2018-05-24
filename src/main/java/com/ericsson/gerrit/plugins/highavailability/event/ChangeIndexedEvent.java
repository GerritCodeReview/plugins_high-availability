package com.ericsson.gerrit.plugins.highavailability.event;

import com.google.gerrit.server.events.Event;
import java.sql.Timestamp;

public class ChangeIndexedEvent extends Event {

  public ChangeIndexedEvent() {
    super("change-indexed");
  }

  @Override
  public String toString() {
    return type + "@" + new Timestamp(eventCreatedOn * 1000L);
  }
}
