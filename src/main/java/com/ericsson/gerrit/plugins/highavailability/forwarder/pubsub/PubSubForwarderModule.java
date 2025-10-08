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
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;

public class PubSubForwarderModule extends LifecycleModule {
  private final Configuration config;

  @Inject
  public PubSubForwarderModule(Configuration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bind(MessageReceiver.class).toProvider(MessageReceiverProvider.class);

    bind(CredentialsProvider.class)
        .toProvider(ServiceAccountCredentialsProvider.class)
        .in(Scopes.SINGLETON);
    bind(Publisher.class).toProvider(GCPPublisherProvider.class).in(SINGLETON);
    bind(Subscription.class).toProvider(PubSubSubscriptionProvider.class).in(SINGLETON);
    bind(Subscriber.class).toProvider(GCPSubscriberProvider.class).in(SINGLETON);

    bind(PubSubMessageProcessor.class);
    bind(Forwarder.class).to(PubSubForwarder.class);
    bind(TopicName.class)
        .annotatedWith(ForwarderTopic.class)
        .toInstance(TopicName.of(config.pubSub().gCloudProject(), config.pubSub().topic()));
    listener().to(OnStartStop.class);
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
}
