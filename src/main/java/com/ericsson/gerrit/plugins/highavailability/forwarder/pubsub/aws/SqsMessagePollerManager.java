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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws.SqsMessagePoller.Factory;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;

@Singleton
public class SqsMessagePollerManager implements LifecycleListener {

  private final Factory pollerFactory;
  private final SqsQueueInfo defaultQueue;
  private final SqsQueueInfo streamEventsQueue;
  private final ExecutorService executor;

  private volatile SqsMessagePoller defaultQueuePoller;
  private volatile SqsMessagePoller streamEventsQueuePoller;

  @Inject
  SqsMessagePollerManager(
      SqsMessagePoller.Factory pollerFactory,
      @DefaultTopic SqsQueueInfo defaultQueue,
      @StreamEventsTopic SqsQueueInfo streamEventsQueue,
      @MessageProcessingExecutor ExecutorService executor) {
    this.pollerFactory = pollerFactory;
    this.defaultQueue = defaultQueue;
    this.streamEventsQueue = streamEventsQueue;
    this.executor = executor;
  }

  @Override
  public void start() {
    defaultQueuePoller = pollerFactory.create(defaultQueue);
    defaultQueuePoller.start();
    streamEventsQueuePoller = pollerFactory.create(streamEventsQueue);
    streamEventsQueuePoller.start();
  }

  @Override
  public void stop() {
    if (defaultQueuePoller == null) {
      defaultQueuePoller.stop();
    }
    if (streamEventsQueuePoller == null) {
      streamEventsQueuePoller.stop();
    }
    executor.shutdown();
  }
}
