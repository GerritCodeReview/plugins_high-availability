package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.pubsub.v1.TopicName;
import java.util.List;

@Singleton
public class TopicNames {
  private final TopicName defaultTopic;
  private final TopicName streamEventsTopic;

  @Inject
  TopicNames(Configuration config) {
    this.defaultTopic =
        TopicName.of(config.pubSub().gCloudProject(), config.pubSub().defaultTopic());
    this.streamEventsTopic =
        TopicName.of(config.pubSub().gCloudProject(), config.pubSub().streamEventsTopic());
  }

  public List<TopicName> all() {
    return List.of(defaultTopic, streamEventsTopic);
  }

  public TopicName defaultTopic() {
    return defaultTopic;
  }

  public TopicName streamEventsTopic() {
    return streamEventsTopic;
  }
}
