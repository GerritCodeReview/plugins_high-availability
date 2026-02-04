package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.gcp;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandProcessor;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.ForwarderCommandsModule;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.EventGson;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

class TestApp {
  private final PubSubTestSystem testSystem;
  private final String instanceId;
  private String defaultTopicDltSubscriptionName;

  CommandProcessor cmdProcessor = mock(CommandProcessor.class);

  @Inject PubSubForwarder forwarder;
  @Inject private OnStartStop onStartStop;
  @Inject private SubscriptionAdminClient subscriptionAdminClient;
  @Inject private @GCloudProject String gCloudProject;

  TestApp(String instanceId, PubSubTestSystem testSystem) {
    this.instanceId = instanceId;
    this.testSystem = testSystem;
  }

  public void start() throws Exception {
    Configuration cfg = mock(Configuration.class, RETURNS_DEEP_STUBS);
    when(cfg.pubSubGcp().gCloudProject()).thenReturn(testSystem.getProjectId());
    when(cfg.pubSub().defaultTopic()).thenReturn(testSystem.getTopicName());
    when(cfg.pubSub().streamEventsTopic()).thenReturn(testSystem.getStreamEventsTopicName());
    when(cfg.pubSubGcp().privateKeyLocation()).thenReturn(testSystem.getPrivateKeyFilePath());
    when(cfg.pubSubGcp().subscriptionTimeout()).thenReturn(Duration.ofSeconds(30));
    when(cfg.pubSubGcp().shutdownTimeout()).thenReturn(Duration.ofSeconds(30));
    when(cfg.pubSubGcp().ackDeadline()).thenReturn(Duration.ofSeconds(10));
    when(cfg.pubSubGcp().retainAckedMessages()).thenReturn(false);
    when(cfg.pubSubGcp().messageRetentionDuration()).thenReturn(Duration.ofMinutes(10));
    when(cfg.pubSubGcp().maxDeliveryAttempts()).thenReturn(5);
    when(cfg.pubSubGcp().minimumBackoff()).thenReturn(Duration.ofSeconds(1));
    when(cfg.pubSubGcp().maximumBackoff()).thenReturn(Duration.ofSeconds(10));

    Module testSupportModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Configuration.class).toInstance(cfg);
            install(new ForwarderCommandsModule());
            bind(Gson.class)
                .annotatedWith(EventGson.class)
                .toInstance(new EventGsonProvider().get());
            bind(String.class).annotatedWith(GerritInstanceId.class).toInstance(instanceId);
            bind(CommandProcessor.class).toInstance(cmdProcessor);
          }
        };

    Module gcpModule;
    if (testSystem instanceof EmulatedPubSub) {
      gcpModule =
          GcpPubSubForwarderModule.createForEmulator(((EmulatedPubSub) testSystem).getHostPort());
    } else {
      gcpModule = GcpPubSubForwarderModule.createForRealPubSub();
    }

    Module testModule = Modules.combine(testSupportModule, gcpModule);
    Injector injector = Guice.createInjector(testModule);
    injector.injectMembers(this);
    String subscriptionId = "monitoring-" + testSystem.getTopicName() + "-dlt";
    defaultTopicDltSubscriptionName = ProjectSubscriptionName.format(gCloudProject, subscriptionId);
    onStartStop.start();
  }

  public List<ReceivedMessage> receiveMessagesFromDlt() {
    PullRequest pullRequest =
        PullRequest.newBuilder()
            .setSubscription(defaultTopicDltSubscriptionName)
            .setMaxMessages(10)
            .build();

    PullResponse pullResponse = subscriptionAdminClient.pullCallable().call(pullRequest);
    List<ReceivedMessage> messages = pullResponse.getReceivedMessagesList();
    ack(messages);
    return messages;
  }

  private void ack(List<ReceivedMessage> messages) {
    List<String> ackIds =
        messages.stream().map(ReceivedMessage::getAckId).collect(Collectors.toList());
    if (ackIds.isEmpty()) {
      return;
    }
    AcknowledgeRequest acknowledgeRequest =
        AcknowledgeRequest.newBuilder()
            .setSubscription(defaultTopicDltSubscriptionName)
            .addAllAckIds(ackIds)
            .build();
    subscriptionAdminClient.acknowledge(acknowledgeRequest);
  }

  public void drainDltMessages() {
    List<ReceivedMessage> messages;
    do {
      messages = receiveMessagesFromDlt();
      ack(messages);
    } while (!messages.isEmpty());
  }

  public void stop() throws Exception {
    onStartStop.stop();
    testSystem.cleanup();
  }
}
