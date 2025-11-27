package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

public class AwsPubSubForwarderModule extends LifecycleModule {
  private static final String LOCALSTACK = "http://localhost:4566";
  private static final String DEFAULT_TOPIC_NAME = "gerrit";

  @Override
  protected void configure() {
    bind(Forwarder.class).to(AwsPubSubForwarder.class);
    listener().to(AwsOnStartStop.class);
    // TODO
  }

  @Provides
  @Singleton
  AwsCredentialsProvider getSnsCredentialsProvider() {
    // TODO real credentials in production
    return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
  }

  @Provides
  @Singleton
  SnsAsyncClient getSnsAsyncClient(AwsCredentialsProvider credentials) {
    // TODO real endpoint in production
    return SnsAsyncClient.builder()
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create(LOCALSTACK))
        .credentialsProvider(credentials)
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .build();
  }

  @Provides
  @Singleton
  @DefaultTopicArn
  String getDefaultSnsTopicArn(SnsAsyncClient sns) throws InterruptedException, ExecutionException {
    // NOTE: this will create the topic if it does not exist, no need for get or create
    return sns.createTopic(CreateTopicRequest.builder().name(DEFAULT_TOPIC_NAME).build())
        .thenApply(CreateTopicResponse::topicArn)
        .get();
  }

  @Provides
  @Singleton
  SqsAsyncClient getSqsAsyncClient(AwsCredentialsProvider credentials) {
    // TODO real endpoint in production
    return SqsAsyncClient.builder()
        .region(Region.US_EAST_1)
        .endpointOverride(URI.create(LOCALSTACK))
        .credentialsProvider(credentials)
        .httpClientBuilder(NettyNioAsyncHttpClient.builder())
        .build();
  }

  @Provides
  @Singleton
  @DefaultQueue
  SqsQueueInfo getDefaultSqsQueueArn(SqsAsyncClient sqs)
      throws InterruptedException, ExecutionException {
    CompletableFuture<String> queueUrlF =
        sqs.createQueue(CreateQueueRequest.builder().queueName("AsyncQueue").build())
            .thenApply(CreateQueueResponse::queueUrl);

    String queueUrl = queueUrlF.get();

    // Get queue ARN
    String queueArn =
        sqs.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build())
            .thenApply(r -> r.attributes().get(QueueAttributeName.QUEUE_ARN))
            .get();
    return new SqsQueueInfo(queueUrl, queueArn);
  }
}
