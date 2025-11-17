// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;

/** Initialize topic(s) and subscription(s) for Pub/Sub if they do not already exist. */
@Singleton
public class PubSubInitializer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final TopicAdminClient topicAdminClient;
  private final SubscriptionAdminClient subscriptionAdminClient;
  private final Configuration pluginConfiguration;
  private final String instanceId;
  private final TopicName topic;

  @Inject
  PubSubInitializer(
      TopicAdminClient topicAdminClient,
      SubscriptionAdminClient subscriptionAdminClient,
      Configuration pluginConfiguration,
      @GerritInstanceId String instanceId,
      @ForwarderTopic TopicName topic) {
    this.topicAdminClient = topicAdminClient;
    this.subscriptionAdminClient = subscriptionAdminClient;
    this.pluginConfiguration = pluginConfiguration;
    this.instanceId = instanceId;
    this.topic = topic;
  }

  public void initialize() {
    initializeTopic();
    initializeSubscription();
  }

  private void initializeTopic() {
    try {
      topicAdminClient.createTopic(topic);
      logger.atInfo().log("Created topic %s", topic.getTopic());
    } catch (AlreadyExistsException e) {
      logger.atInfo().log("Topic %s already exists", topic.getTopic());
    }
  }

  private void initializeSubscription() {
    Configuration.PubSub pubSubProperties = pluginConfiguration.pubSub();
    String subscriptionId = String.format("%s-%s", instanceId, topic.getTopic());
    ProjectSubscriptionName projectSubscriptionName =
        ProjectSubscriptionName.of(pubSubProperties.gCloudProject(), subscriptionId);
    try {
      subscriptionAdminClient.getSubscription(projectSubscriptionName);
      logger.atInfo().log("Subscription %s already exists", subscriptionId);
    } catch (NotFoundException e) {
      logger.atInfo().log("Creating subscription %s", subscriptionId);
      String filter = String.format("attributes.instanceId!=\"%s\"", instanceId);
      Subscription subscription =
          Subscription.newBuilder()
              .setName(projectSubscriptionName.toString())
              .setTopic(topic.toString())
              .setAckDeadlineSeconds((int) pubSubProperties.ackDeadline().getSeconds())
              .setFilter(filter)
              .build();
      subscriptionAdminClient.createSubscription(subscription);
    }
  }
}
