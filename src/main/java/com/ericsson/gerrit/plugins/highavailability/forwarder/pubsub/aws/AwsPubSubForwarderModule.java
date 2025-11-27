package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.TopicNames;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

public class AwsPubSubForwarderModule extends LifecycleModule {
  private static final String LOCALSTACK = "LOCALSTACK";

  public static Module create() {
    String localStackEndpoint = getLocalStackEndpoint();
    if (Strings.isNullOrEmpty(getLocalStackEndpoint())) {
      return new AwsPubSubForwarderModule();
    }
    return Modules.override(new AwsPubSubForwarderModule())
        .with(new LocalStackModule(localStackEndpoint));
  }

  private static String getLocalStackEndpoint() {
    return Optional.ofNullable(System.getenv(LOCALSTACK)).orElse(System.getProperty(LOCALSTACK));
  }

  private AwsPubSubForwarderModule() {}

  @Override
  protected void configure() {
    bind(Forwarder.class).to(AwsPubSubForwarder.class);
    bind(SqsQueueCreator.class);

    install(
        new FactoryModule() {
          @Override
          protected void configure() {
            factory(SqsMessagePoller.Factory.class);
          }
        });

    listener().to(SqsMessagePollerManager.class);
  }

  @Provides
  @Singleton
  Region getRegion(Configuration config) {
    return Region.of(config.pubSubAws().region());
  }

  @Provides
  @Singleton
  SdkAsyncHttpClient getSdkAsyncHttpClient() {
    return AwsCrtAsyncHttpClient.builder()
        .maxConcurrency(50)
        .connectionTimeout(Duration.ofSeconds(3))
        .build();
  }

  @Provides
  @Singleton
  AwsCredentialsProvider getCredentialsProvider(Configuration config) throws IOException {
    String accessKey = Files.readString(config.pubSubAws().accessKeyIdLocation()).trim();
    String secretKey = Files.readString(config.pubSubAws().secretAccessKeyLocation()).trim();
    return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
  }

  @Provides
  @Singleton
  SnsAsyncClient getSnsAsyncClient(
      SdkAsyncHttpClient httpClient, AwsCredentialsProvider credentials, Region region) {
    return SnsAsyncClient.builder()
        .region(region)
        .credentialsProvider(credentials)
        .httpClient(httpClient)
        .build();
  }

  @Provides
  @Singleton
  SqsAsyncClient getSqsAsyncClient(
      SdkAsyncHttpClient httpClient, AwsCredentialsProvider credentials, Region region) {
    return SqsAsyncClient.builder()
        .region(region)
        .credentialsProvider(credentials)
        .httpClient(httpClient)
        .build();
  }

  @Provides
  @Singleton
  @DefaultTopic
  String getDefaultSnsTopicArn(SnsAsyncClient sns, TopicNames topicNames)
      throws InterruptedException, ExecutionException {
    // NOTE: this will create the topic if it does not exist, no need for get or create
    return sns.createTopic(req -> req.name(topicNames.defaultTopic()))
        .thenApply(CreateTopicResponse::topicArn)
        .get();
  }

  @Provides
  @Singleton
  @StreamEventsTopic
  String getStreamEventSnsTopicArn(SnsAsyncClient sns, TopicNames topicNames)
      throws InterruptedException, ExecutionException {
    // NOTE: this will create the topic if it does not exist, no need for get or create
    return sns.createTopic(req -> req.name(topicNames.streamEventsTopic()))
        .thenApply(CreateTopicResponse::topicArn)
        .get();
  }

  @Provides
  @Singleton
  @DefaultTopic
  SqsQueueInfo getDefaultSqsQueue(
      SqsQueueCreator queueCreator,
      TopicNames topicNames,
      @DefaultTopic String defaultTopicArn,
      @GerritInstanceId String instanceId)
      throws InterruptedException, ExecutionException {
    return queueCreator.create(instanceId + "-" + topicNames.defaultTopic(), defaultTopicArn);
  }

  @Provides
  @Singleton
  @StreamEventsTopic
  SqsQueueInfo getStreamEventsSqsQueue(
      SqsQueueCreator queueCreator,
      TopicNames topicNames,
      @StreamEventsTopic String streamEventsTopicArn,
      @GerritInstanceId String instanceId)
      throws InterruptedException, ExecutionException {
    return queueCreator.create(
        instanceId + "-" + topicNames.streamEventsTopic(), streamEventsTopicArn);
  }

  @Provides
  @Singleton
  @MessageProcessingExecutor
  ExecutorService getMessageProcessingExecutor(Configuration config) {
    return Executors.newFixedThreadPool(config.pubSubAws().messageProcessingThreadPoolSize());
  }

  static class LocalStackModule extends AbstractModule {
    private final String localStackEndpoint;

    public LocalStackModule(String localStackEndpoint) {
      this.localStackEndpoint = localStackEndpoint;
    }

    @Override
    protected void configure() {
      bind(Region.class).toInstance(Region.EU_CENTRAL_1);
      bind(AwsCredentialsProvider.class)
          .toInstance(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
    }

    @Provides
    @Singleton
    SnsAsyncClient getSnsAsyncClient(
        SdkAsyncHttpClient httpClient, AwsCredentialsProvider credentials, Region region) {
      return SnsAsyncClient.builder()
          .region(region)
          .endpointOverride(URI.create(localStackEndpoint))
          .credentialsProvider(credentials)
          .httpClient(httpClient)
          .build();
    }

    @Provides
    @Singleton
    SqsAsyncClient getSqsAsyncClient(
        SdkAsyncHttpClient httpClient, AwsCredentialsProvider credentials, Region region) {
      return SqsAsyncClient.builder()
          .region(region)
          .endpointOverride(URI.create(localStackEndpoint))
          .credentialsProvider(credentials)
          .httpClient(httpClient)
          .build();
    }
  }
}
