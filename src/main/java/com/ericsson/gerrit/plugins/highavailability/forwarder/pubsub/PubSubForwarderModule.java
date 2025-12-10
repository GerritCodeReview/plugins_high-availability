package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.gcp.GcpPubSubForwarderModule;
import com.google.inject.AbstractModule;

public class PubSubForwarderModule extends AbstractModule {
  private final Configuration config;

  public PubSubForwarderModule(Configuration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(TopicNames.class);

    PubSubProvider provider = PubSubProvider.valueOf(config.pubSub().provider().toUpperCase());
    switch (provider) {
      case GCP -> install(new GcpPubSubForwarderModule());
    }
  }
}
