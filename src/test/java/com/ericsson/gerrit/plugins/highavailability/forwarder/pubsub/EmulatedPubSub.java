// Copyright (C) 2023 The Android Open Source Project
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
import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.PubSubForwarderModule.EmulatorModule;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

public class EmulatedPubSub extends PubSubTestSystem {
  public static final String PROJECT_ID = "test";
  public static final String PUBSUB_TOPIC_ID = "gerrit";
  public static final TopicName TOPIC_NAME = TopicName.of(PROJECT_ID, PUBSUB_TOPIC_ID);

  private final PubSubEmulatorContainer container;
  private final FixedTransportChannelProvider channelProvider;
  private final String hostPort;
  private final PubSubForwarderModule pubSubForwarderModule;

  public EmulatedPubSub(Configuration cfg) throws Exception {
    super(cfg);
    container =
        new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:470.0.0-emulators"));
    container.start();
    hostPort = container.getEmulatorEndpoint();
    ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
    channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
    System.out.println(
        String.format(
            "PubSub emulator container started and is listening on %s",
            container.getEmulatorEndpoint()));
    pubSubForwarderModule = new PubSubForwarderModule(cfg);
  }

  @Override
  public void reset() throws Exception {}

  @Override
  public void cleanup() throws Exception {
    container.stop();
    container.close();
    System.out.println("Spanner emulator container was stopped");
  }

  private TopicAdminSettings topicAdminSettings() throws Exception {
    return TopicAdminSettings.newBuilder()
        .setTransportChannelProvider(channelProvider)
        .setCredentialsProvider(getCredentials())
        .build();
  }

  @Override
  String getProjectId() {
    return PROJECT_ID;
  }

  @Override
  TopicName getTopicName() {
    return TOPIC_NAME;
  }

  @Override
  CredentialsProvider getCredentials() throws Exception {
    return NoCredentialsProvider.create();
  }

  @Override
  TopicAdminClient getTopicAdminClient() throws Exception {
    return TopicAdminClient.create(topicAdminSettings());
  }

  @Override
  SubscriptionAdminClient getSubscriptionAdminClient() throws Exception {
    EmulatorModule emulatorModule =
        new PubSubForwarderModule.EmulatorModule(container.getEmulatorEndpoint());
    return emulatorModule.createSubscriptionAdminClient(
        emulatorModule.createTransportChannelProvider());
  }

  @Override
  Publisher getPublisher() throws Exception {
    EmulatorModule emulatorModule =
        new PubSubForwarderModule.EmulatorModule(container.getEmulatorEndpoint());
    return new LocalPublisherProvider(
            getCredentials(),
            emulatorModule.createTransportChannelProvider(),
            TOPIC_NAME,
            PubSubForwarderModule.buildPublisherExecutorProvider(cfg))
        .get();
  }

  @Override
  Subscriber getSubscriber(PubSubMessageProcessor processor, String instanceId) throws Exception {
    EmulatorModule emulatorModule =
        new PubSubForwarderModule.EmulatorModule(container.getEmulatorEndpoint());
    return new LocalSubscriberProvider(
            getCredentials(),
            emulatorModule.createTransportChannelProvider(),
            getSubscription(instanceId),
            new MessageReceiverProvider(cfg, processor).get(),
            PubSubForwarderModule.buildSubscriberExecutorProvider(cfg))
        .get();
  }

  Subscription getSubscription(String instanceId) throws Exception {
    ProjectSubscriptionName subscriptionName =
        new ProjectSubscriptionNameFactory(instanceId, cfg).create(TOPIC_NAME);
    EmulatorModule emulatorModule =
        new PubSubForwarderModule.EmulatorModule(container.getEmulatorEndpoint());
    SubscriptionAdminClient subscriptionAdminClient =
        emulatorModule.createSubscriptionAdminClient(
            emulatorModule.createTransportChannelProvider());
    return pubSubForwarderModule.getSubscription(
        subscriptionAdminClient,
        ProjectSubscriptionName.of(getProjectId(), subscriptionName.getSubscription()));
  }
}
