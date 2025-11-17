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
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class OnStartStop implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PubSubInitializer initializer;
  private final Configuration config;
  private final Provider<Publisher> publisher;
  private final Provider<Subscriber> subscriber;

  @Inject
  public OnStartStop(
      PubSubInitializer initializer,
      Configuration config,
      Provider<Publisher> publisher,
      Provider<Subscriber> subscriber) {
    this.initializer = initializer;
    this.config = config;
    this.publisher = publisher;
    this.subscriber = subscriber;
  }

  @Override
  public void start() {
    initializer.initialize();
    try {
      subscriber
          .get()
          .startAsync()
          .awaitRunning(config.pubSub().subscriptionTimeout().getSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new IllegalStateException("Timeout while subscribing to PubSub topic", e);
    }
  }

  @Override
  public void stop() {
    logger.atInfo().log("Closing PubSub publisher");
    try {
      publisher.get().shutdown();
      publisher
          .get()
          .awaitTermination(config.pubSub().shutdownTimeout().getSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.atSevere().withCause(e).log("Could not close the MessageDispatcher");
    }

    logger.atInfo().log("Closing PubSub subscriber");
    try {
      subscriber
          .get()
          .stopAsync()
          .awaitTerminated(config.pubSub().shutdownTimeout().getSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      logger.atSevere().withCause(e).log("Timeout during stopping of subscription.");
    }
  }
}
