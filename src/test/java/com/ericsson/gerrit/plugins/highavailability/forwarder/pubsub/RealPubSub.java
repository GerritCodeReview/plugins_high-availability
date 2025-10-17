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
import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.PubSubForwarderModule.GCPModule;
import com.google.api.client.util.Strings;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.common.flogger.FluentLogger;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import java.io.FileInputStream;

public class RealPubSub extends PubSubTestSystem {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static RealPubSub INSTANCE;

  public static RealPubSub create(Configuration cfg) {
    if (INSTANCE == null) {
      String keyPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
      String project = System.getenv("GCP_PROJECT");
      String topic = System.getenv("PUBSUB_TOPIC");
      if (Strings.isNullOrEmpty(topic)) {
        INSTANCE = new RealPubSub(cfg, keyPath, project);
      } else {
        INSTANCE = new RealPubSub(cfg, keyPath, project, topic);
      }
    }
    return INSTANCE;
  }

  private final String keyPath;
  private final TopicName topicName;

  private boolean topicCreatedByTest;
  private Subscriber subscriber;
  private PubSubForwarderModule pubSubForwarderModule;

  private RealPubSub(Configuration cfg, String keyPath, String project) {
    this(cfg, keyPath, project, "gerrit-pubsub-" + System.currentTimeMillis());
  }

  private RealPubSub(Configuration cfg, String keyPath, String project, String topic) {
    super(cfg);
    this.keyPath = keyPath;
    this.topicName = TopicName.of(project, topic);
    this.pubSubForwarderModule = new PubSubForwarderModule(cfg);
  }

  private TopicAdminSettings topicAdminSettings() throws Exception {
    return TopicAdminSettings.newBuilder().setCredentialsProvider(getCredentials()).build();
  }

  private SubscriptionAdminSettings subscriptionAdminSettings() throws Exception {
    return SubscriptionAdminSettings.newBuilder().setCredentialsProvider(getCredentials()).build();
  }

  @Override
  public void reset() throws Exception {}

  @Override
  void cleanup() throws Exception {
    try (SubscriptionAdminClient subscriptionAdminClient =
        SubscriptionAdminClient.create(subscriptionAdminSettings())) {
      subscriptionAdminClient.deleteSubscription(subscriber.getSubscriptionNameString());
    }
    if (topicCreatedByTest) {
      TopicName toBeDeleted = topicName;
      try (TopicAdminClient topicAdminClient = TopicAdminClient.create(topicAdminSettings())) {
        topicAdminClient.deleteTopic(toBeDeleted);
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Failed to delete topic %s", toBeDeleted);
      }
    }
  }

  @Override
  String getProjectId() {
    return topicName.getProject();
  }

  @Override
  TopicName getTopicName() {
    return topicName;
  }

  @Override
  CredentialsProvider getCredentials() throws Exception {
    return FixedCredentialsProvider.create(
        ServiceAccountCredentials.fromStream(new FileInputStream(keyPath)));
  }

  @Override
  TopicAdminClient getTopicAdminClient() throws Exception {
    return TopicAdminClient.create(topicAdminSettings());
  }

  @Override
  SubscriptionAdminClient getSubscriptionAdminClient() throws Exception {
    GCPModule gcpModule = new PubSubForwarderModule.GCPModule();
    return gcpModule.createSubscriptionAdminClient(getCredentials());
  }

  @Override
  Publisher getPublisher() throws Exception {
    return new GCPPublisherProvider(
            getCredentials(), topicName, PubSubForwarderModule.buildPublisherExecutorProvider(cfg))
        .get();
  }

  @Override
  Subscriber getSubscriber(PubSubMessageProcessor processor, String instanceId) throws Exception {
    subscriber =
        new GCPSubscriberProvider(
                getCredentials(),
                getSubscription(instanceId),
                new MessageReceiverProvider(cfg, processor, instanceId).get(),
                PubSubForwarderModule.buildSubscriberExecutorProvider(cfg))
            .get();
    return subscriber;
  }

  Subscription getSubscription(String instanceId) throws Exception {
    ProjectSubscriptionName subscriptionName =
        new ProjectSubscriptionNameFactory(instanceId, cfg).create(topicName);
    GCPModule gcpModule = new PubSubForwarderModule.GCPModule();
    return pubSubForwarderModule.getSubscription(
        gcpModule.createSubscriptionAdminClient(getCredentials()),
        ProjectSubscriptionName.of(getProjectId(), subscriptionName.getSubscription()));
  }
}
