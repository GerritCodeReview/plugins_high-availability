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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandProcessor;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.ForwarderCommandsModule;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import com.google.inject.util.Providers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PubSubForwarderTest {
  private static final String PUBLISHER_INSTANCE_ID = "gerrit-pub";
  private static final String SUBSCRIBER_INSTANCE_ID = "gerrit-sub";

  private PubSubTestSystem testSystem;
  private Publisher publisher;
  private Publisher streamEventsPublisher;
  private PubSubForwarder forwarder;
  private Subscriber subscriber;
  private OnStartStop onStartStop;
  @Mock private CommandProcessor cmdProcessor;
  private Gson gson;

  @Before
  public void setUp() throws Exception {
    Configuration cfg = mock(Configuration.class, RETURNS_DEEP_STUBS);

    testSystem = PubSubTestSystem.create(cfg);
    when(cfg.pubSub().gCloudProject()).thenReturn(testSystem.getProjectId());
    when(cfg.pubSub().defaultTopic()).thenReturn("gerrit");
    when(cfg.pubSub().streamEventsTopic()).thenReturn("stream-events");
    when(cfg.pubSub().subscriptionTimeout()).thenReturn(Duration.ofSeconds(30));
    when(cfg.pubSub().shutdownTimeout()).thenReturn(Duration.ofSeconds(30));

    Gson eventGson = new EventGsonProvider().get();
    gson = new ForwarderCommandsModule().buildCommandsGson(eventGson);
    publisher = testSystem.getPublisher();
    streamEventsPublisher = testSystem.getStreamEventsPublisher();
    forwarder = new PubSubForwarder(publisher, streamEventsPublisher, gson, PUBLISHER_INSTANCE_ID);

    TopicAdminClient topicAdminClient = testSystem.getTopicAdminClient();
    SubscriptionAdminClient subscriptionAdminClient = testSystem.getSubscriptionAdminClient();
    PubSubInitializer initializer =
        new PubSubInitializer(
            topicAdminClient,
            subscriptionAdminClient,
            cfg,
            SUBSCRIBER_INSTANCE_ID,
            new TopicNames(cfg),
            new ProjectSubscriptionNameFactory(SUBSCRIBER_INSTANCE_ID, cfg));

    onStartStop =
        new OnStartStop(initializer, cfg, Providers.of(List.of(publisher)), this::getSubscriber);
    onStartStop.start();
  }

  private List<Subscriber> getSubscriber() {
    if (subscriber != null) {
      return List.of(subscriber);
    }
    PubSubMessageProcessor msgProcessor = new PubSubMessageProcessor(gson, cmdProcessor);
    try {
      subscriber = testSystem.getSubscriber(msgProcessor, SUBSCRIBER_INSTANCE_ID);
    } catch (Exception e) {
      throw new IllegalStateException("Could not create PubSub subscriber", e);
    }
    return List.of(subscriber);
  }

  @After
  public void tearDown() throws Exception {
    onStartStop.stop();
    testSystem.cleanup();
  }

  @Test
  public void indexAccount_publishSubscribe_OK() throws Exception {
    when(cmdProcessor.handle(any())).thenReturn(true);
    CompletableFuture<Boolean> result = forwarder.indexAccount(100, null);
    assertThat(result.get()).isTrue();
    verify(cmdProcessor, timeout(10000).times(1)).handle(any());
  }

  @Test
  public void indexAccount_publishSubscribe_Retries() throws Exception {
    when(cmdProcessor.handle(any())).thenReturn(false);
    CompletableFuture<Boolean> result = forwarder.indexAccount(100, null);
    assertThat(result.get()).isTrue();
    verify(cmdProcessor, timeout(10000).atLeast(2)).handle(any());
  }
}
