package com.ericsson.gerrit.plugins.highavailability.api;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.AbstractModule;

public class ApiModule extends AbstractModule {
  @Override
  protected void configure() {
    DynamicItem.itemOf(binder(), HAForwarder.class);
    // DynamicSet.setOf(binder(), HAForwarder.class);
  }
}
