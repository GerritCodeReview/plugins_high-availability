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

import static com.google.inject.Scopes.SINGLETON;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

public class PubSubForwarderModule extends LifecycleModule {
  public static final String PUBSUB_EMULATOR_HOST = "PUBSUB_EMULATOR_HOST";

  private final Configuration config;

  public PubSubForwarderModule(Configuration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(PubSubInitializer.class);
    bind(MessageReceiver.class).toProvider(MessageReceiverProvider.class);

    String hostPort = getEmulatorHost();
    if (Strings.isNullOrEmpty(hostPort)) {
      install(new GCPModule());
    } else {
      install(new EmulatorModule(hostPort));
    }

    bind(PubSubMessageProcessor.class);
    bind(Forwarder.class).to(PubSubForwarder.class);
    bind(TopicName.class)
        .annotatedWith(ForwarderTopic.class)
        .toInstance(TopicName.of(config.pubSub().gCloudProject(), config.pubSub().topic()));
    listener().to(OnStartStop.class);
  }

  @Provides
  @Singleton
  CredentialsProvider getCredentialsProvider(Configuration config)
      throws FileNotFoundException, IOException {
    return FixedCredentialsProvider.create(
        ServiceAccountCredentials.fromStream(
            new FileInputStream(config.pubSub().privateKeyLocation())));
  }

  @Provides
  @Singleton
  @PublisherExecutorProvider
  @VisibleForTesting
  public static ExecutorProvider buildPublisherExecutorProvider(Configuration config) {
    return InstantiatingExecutorProvider.newBuilder()
        .setExecutorThreadCount(config.pubSub().publisherThreadPoolSize())
        .build();
  }

  @Provides
  @Singleton
  @SubscriberExecutorProvider
  @VisibleForTesting
  public static ExecutorProvider buildSubscriberExecutorProvider(Configuration config) {
    return InstantiatingExecutorProvider.newBuilder()
        .setExecutorThreadCount(config.pubSub().subscriberThreadPoolSize())
        .build();
  }

  @Provides
  @Singleton
  ProjectSubscriptionName getProjectSubscriptionName(
      Configuration config, @GerritInstanceId String instanceId, @ForwarderTopic TopicName topic) {
    String subscriptionId = String.format("%s-%s", instanceId, topic.getTopic());
    return ProjectSubscriptionName.of(config.pubSub().gCloudProject(), subscriptionId);
  }

  @Provides
  @Singleton
  Subscription getSubscription(
      SubscriptionAdminClient subscriptionAdminClient,
      ProjectSubscriptionName projectSubscriptionName) {
    return subscriptionAdminClient.getSubscription(projectSubscriptionName);
  }

  private static String getEmulatorHost() {
    return Optional.ofNullable(System.getenv(PUBSUB_EMULATOR_HOST))
        .orElse(System.getProperty(PUBSUB_EMULATOR_HOST));
  }

  static class GCPModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(CredentialsProvider.class)
          .toProvider(ServiceAccountCredentialsProvider.class)
          .in(Scopes.SINGLETON);
      bind(Publisher.class).toProvider(GCPPublisherProvider.class).in(SINGLETON);
      bind(Subscriber.class).toProvider(GCPSubscriberProvider.class).in(SINGLETON);
    }

    @Provides
    @Singleton
    SubscriptionAdminClient createSubscriptionAdminClient(CredentialsProvider credentials)
        throws IOException {
      return SubscriptionAdminClient.create(
          SubscriptionAdminSettings.newBuilder().setCredentialsProvider(credentials).build());
    }

    @Provides
    @Singleton
    TopicAdminClient createTopicAdminClient(CredentialsProvider credentials) throws IOException {
      TopicAdminSettings settings =
          TopicAdminSettings.newBuilder().setCredentialsProvider(credentials).build();
      return TopicAdminClient.create(settings);
    }
  }

  static class EmulatorModule extends AbstractModule {
    private final String hostPort;

    EmulatorModule(String hostPort) {
      this.hostPort = hostPort;
    }

    @Override
    protected void configure() {
      bind(CredentialsProvider.class).toInstance(NoCredentialsProvider.create());
      bind(Publisher.class).toProvider(LocalPublisherProvider.class).in(SINGLETON);
      bind(Subscriber.class).toProvider(LocalSubscriberProvider.class).in(SINGLETON);
    }

    @Provides
    @Singleton
    TransportChannelProvider createTransportChannelProvider() {
      ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
      return FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
    }

    @Provides
    @Singleton
    SubscriptionAdminClient createSubscriptionAdminClient(TransportChannelProvider channelProvider)
        throws IOException {
      SubscriptionAdminSettings settings =
          SubscriptionAdminSettings.newBuilder()
              .setTransportChannelProvider(channelProvider)
              .setCredentialsProvider(NoCredentialsProvider.create())
              .build();
      return SubscriptionAdminClient.create(settings);
    }

    @Provides
    @Singleton
    TopicAdminClient createTopicAdminClient(TransportChannelProvider channelProvider)
        throws IOException {
      TopicAdminSettings settings =
          TopicAdminSettings.newBuilder()
              .setTransportChannelProvider(channelProvider)
              .setCredentialsProvider(NoCredentialsProvider.create())
              .build();
      return TopicAdminClient.create(settings);
    }
  }
}
