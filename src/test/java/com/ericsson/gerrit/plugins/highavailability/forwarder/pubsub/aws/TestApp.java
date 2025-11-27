// Copyright (C) 2026 The Android Open Source Project
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

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandProcessor;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandsGson;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.ForwarderCommandsModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws.AwsPubSubForwarderModule.LocalStackModule;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.EventGson;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

class TestApp {
  private static final int MAX_RECEIVE_COUNT = 3;

  final String instanceId;
  final AwsPubSubTestSystem testSystem;
  CommandProcessor cmdProcessor = mock(CommandProcessor.class);

  @Inject private SnsAsyncClient snsClient;
  @Inject private SqsAsyncClient sqsClient;
  @Inject private SqsClient sqsClientSync;
  @Inject AwsPubSubForwarder forwarder;
  @Inject private SqsMessagePollerManager pollerManager;
  @Inject SqsMessagePoller.Factory pollerFactory;
  @Inject @DefaultTopic SqsQueueInfo defaultQueueInfo;
  @Inject @CommandsGson Gson gson;

  @Inject
  @Named("defaultDlq")
  SqsQueueInfo dlqInfo;

  TestApp(String instanceId, AwsPubSubTestSystem testSystem) {
    this.instanceId = instanceId;
    this.testSystem = testSystem;
  }

  void start() {
    Configuration cfg = mock(Configuration.class, RETURNS_DEEP_STUBS);
    when(cfg.pubSubAws().region()).thenReturn(testSystem.getRegion());
    when(cfg.pubSub().defaultTopic()).thenReturn(testSystem.getDefaultTopicName());
    when(cfg.pubSub().streamEventsTopic()).thenReturn(testSystem.getStreamEventsTopicName());
    when(cfg.pubSubAws().region()).thenReturn(testSystem.getRegion());
    when(cfg.pubSubAws().accessKeyIdLocation())
        .thenReturn(Path.of(testSystem.getAccessKeyLocation()));
    when(cfg.pubSubAws().secretAccessKeyLocation())
        .thenReturn(Path.of(testSystem.getSecretKeyLocation()));
    when(cfg.pubSubAws().maxReceiveCount()).thenReturn(MAX_RECEIVE_COUNT);
    when(cfg.pubSubAws().messageProcessingThreadPoolSize()).thenReturn(2);
    when(cfg.pubSubAws().visibilityTimeout()).thenReturn(Duration.ofSeconds(2));
    when(cfg.pubSubAws().waitTime()).thenReturn(Duration.ofSeconds(1));
    when(cfg.pubSubAws().maxNumberOfMessages()).thenReturn(10);

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

          @Provides
          @Singleton
          @Named("defaultDlq")
          SqsQueueInfo getDefaultDlqSqsQueue(
              SqsQueueCreator queueCreator, @DefaultTopic SqsQueueInfo defaultQueue)
              throws InterruptedException, ExecutionException {
            return queueCreator.createDlq(defaultQueue);
          }

          @Provides
          @Singleton
          SdkHttpClient getSdkHttpClient() {
            return AwsCrtHttpClient.builder()
                .maxConcurrency(50)
                .connectionTimeout(Duration.ofSeconds(3))
                .build();
          }

          @Provides
          @Singleton
          SqsClient getSqsClient(
              SdkHttpClient httpClient, AwsCredentialsProvider credentials, Region region) {
            return SqsClient.builder()
                .region(region)
                .endpointOverride(testSystem.getEndpoint())
                .credentialsProvider(credentials)
                .httpClient(httpClient)
                .build();
          }
        };

    AwsPubSubForwarderModule awsModule = new AwsPubSubForwarderModule();

    Module testModule;
    if (testSystem instanceof EmulatedAwsPubSubTestSystem) {
      String localStackEndpoint = testSystem.getEndpoint().toString();
      LocalStackModule localStackModule = new LocalStackModule(localStackEndpoint);
      testModule =
          Modules.combine(testSupportModule, Modules.override(awsModule).with(localStackModule));
    } else {
      testModule = Modules.combine(testSupportModule, awsModule);
    }

    Injector injector = Guice.createInjector(testModule);
    injector.injectMembers(this);
    pollerManager.start();
  }

  List<Message> receiveMessagesFromDlq() {
    return sqsClientSync
        .receiveMessage(
            req -> req.queueUrl(dlqInfo.url()).maxNumberOfMessages(10).waitTimeSeconds(1))
        .messages();
  }

  void stop() {
    pollerManager.stop();

    if (snsClient != null) {
      snsClient.close();
    }
    if (sqsClient != null) {
      sqsClient.close();
    }
  }
}
