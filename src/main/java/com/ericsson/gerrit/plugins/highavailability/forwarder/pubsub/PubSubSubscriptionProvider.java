// Copyright (C) 2024 The Android Open Source Project
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
import com.ericsson.gerrit.plugins.highavailability.Configuration.PubSub;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;
import java.util.Optional;

public class PubSubSubscriptionProvider implements Provider<Subscription> {
  private final SubscriptionAdminClient subscriptionAdminClient;
  final CredentialsProvider credentials;
  private final PubSub pubSubProperties;
  private final TopicName topic;
  private final String subscriptionId;
  private final String instanceId;

  @Inject
  public PubSubSubscriptionProvider(
      SubscriptionAdminClient subscriptionAdminClient,
      CredentialsProvider credentials,
      Configuration pluginConfiguration,
      @GerritInstanceId String instanceId,
      @ForwarderTopic TopicName topic) {
    this.subscriptionAdminClient = subscriptionAdminClient;
    this.credentials = credentials;
    this.pubSubProperties = pluginConfiguration.pubSub();
    this.topic = topic;
    this.subscriptionId = String.format("%s-%s", instanceId, topic.getTopic());
    this.instanceId = instanceId;
  }

  @Override
  public Subscription get() {
    ProjectSubscriptionName projectSubscriptionName =
        ProjectSubscriptionName.of(pubSubProperties.gCloudProject(), subscriptionId);

    return getSubscription(subscriptionAdminClient, projectSubscriptionName)
        .orElseGet(
            () ->
                subscriptionAdminClient.createSubscription(
                    createSubscriptionRequest(projectSubscriptionName)));
  }

  protected SubscriptionAdminSettings createSubscriptionAdminSettings() throws IOException {
    return SubscriptionAdminSettings.newBuilder().setCredentialsProvider(credentials).build();
  }

  private Subscription createSubscriptionRequest(ProjectSubscriptionName projectSubscriptionName) {
    String filter = String.format("attributes.instanceId!=\"%s\"", instanceId);
    return Subscription.newBuilder()
        .setName(projectSubscriptionName.toString())
        .setTopic(topic.toString())
        .setAckDeadlineSeconds((int) pubSubProperties.ackDeadline().getSeconds())
        .setFilter(filter)
        .build();
  }

  protected Optional<Subscription> getSubscription(
      SubscriptionAdminClient subscriptionAdminClient,
      ProjectSubscriptionName projectSubscriptionName) {
    try {
      // we should use subscriptionAdminClient.listSubscriptions but for local setup this method
      // throws UNKNOWN_EXCEPTION
      return Optional.of(subscriptionAdminClient.getSubscription(projectSubscriptionName));
    } catch (NotFoundException e) {
      return Optional.empty();
    }
  }
}
