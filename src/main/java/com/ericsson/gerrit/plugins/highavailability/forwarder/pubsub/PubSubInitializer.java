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
import com.google.protobuf.FieldMask;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import com.google.pubsub.v1.UpdateSubscriptionRequest;

/** Initialize topic(s) and subscription(s) for Pub/Sub if they do not already exist. */
@Singleton
public class PubSubInitializer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final TopicAdminClient topicAdminClient;
  private final SubscriptionAdminClient subscriptionAdminClient;
  private final Configuration pluginConfiguration;
  private final String instanceId;
  private final TopicNames topicNames;
  private final ProjectSubscriptionNameFactory subscriptionNameFactory;

  @Inject
  PubSubInitializer(
      TopicAdminClient topicAdminClient,
      SubscriptionAdminClient subscriptionAdminClient,
      Configuration pluginConfiguration,
      @GerritInstanceId String instanceId,
      TopicNames topicNames,
      ProjectSubscriptionNameFactory subscriptionNameFactory) {
    this.topicAdminClient = topicAdminClient;
    this.subscriptionAdminClient = subscriptionAdminClient;
    this.pluginConfiguration = pluginConfiguration;
    this.instanceId = instanceId;
    this.topicNames = topicNames;
    this.subscriptionNameFactory = subscriptionNameFactory;
  }

  public void initialize() {
    for (TopicName topic : topicNames.all()) {
      initializeTopic(topic);
      initializeTopic(createDltTopicName(topic));
      initializeSubscription(topic);
    }
  }

  private TopicName createDltTopicName(TopicName topic) {
    return TopicName.of(topic.getProject(), topic.getTopic() + "-dlt");
  }

  private void initializeTopic(TopicName topic) {
    try {
      topicAdminClient.createTopic(topic);
      logger.atInfo().log("Created topic %s", topic.getTopic());
    } catch (AlreadyExistsException e) {
      logger.atInfo().log("Topic %s already exists", topic.getTopic());
    }
  }

  private void initializeSubscription(TopicName topic) {
    ProjectSubscriptionName projectSubscriptionName = subscriptionNameFactory.create(topic);
    Subscription subscription;
    try {
      subscription = subscriptionAdminClient.getSubscription(projectSubscriptionName);
      logger.atInfo().log("Subscription for topic %s already exists", topic);
    } catch (NotFoundException e) {
      logger.atInfo().log("Creating subscription for topic %s", topic);
      String filter = String.format("attributes.instanceId!=\"%s\"", instanceId);
      subscription =
          Subscription.newBuilder()
              .setName(projectSubscriptionName.toString())
              .setTopic(topic.toString())
              .setFilter(filter)
              .build();
      subscriptionAdminClient.createSubscription(subscription);
    }

    updateSubscriptionSettings(subscription, topic);
  }

  private void updateSubscriptionSettings(Subscription subscription, TopicName topic) {
    DeadLetterPolicy deadLetterPolicy =
        DeadLetterPolicy.newBuilder()
            .setDeadLetterTopic(createDltTopicName(topic).toString())
            .setMaxDeliveryAttempts(pluginConfiguration.pubSubDlt().maxDeliveryAttempts())
            .build();

    Subscription desired =
        subscription.toBuilder()
            .setAckDeadlineSeconds((int) pluginConfiguration.pubSub().ackDeadline().getSeconds())
            .setMessageRetentionDuration(
                com.google.protobuf.Duration.newBuilder()
                    .setSeconds(
                        pluginConfiguration.pubSub().messageRetentionDuration().getSeconds())
                    .build())
            .setRetainAckedMessages(pluginConfiguration.pubSub().retainAckedMessages())
            .setDeadLetterPolicy(deadLetterPolicy)
            .build();

    FieldMask fieldMask =
        FieldMask.newBuilder()
            .addPaths("ack_deadline_seconds")
            .addPaths("message_retention_duration")
            .addPaths("retain_acked_messages")
            .addPaths("dead_letter_policy")
            .build();

    UpdateSubscriptionRequest updateRequest =
        UpdateSubscriptionRequest.newBuilder()
            .setSubscription(desired)
            .setUpdateMask(fieldMask)
            .build();

    subscriptionAdminClient.updateSubscription(updateRequest);
  }
}
