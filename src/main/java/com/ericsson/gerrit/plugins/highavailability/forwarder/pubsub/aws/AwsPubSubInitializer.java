package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

@Singleton
public class AwsPubSubInitializer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String instanceId;
  private final SqsAsyncClient sqs;
  private final String defaultTopicArn;
  private final SqsQueueInfo defaultQueue;

  @Inject
  AwsPubSubInitializer(
      @GerritInstanceId String instanceId,
      SqsAsyncClient sqs,
      @DefaultTopicArn String defaultTopicArn,
      @DefaultQueue SqsQueueInfo defaultQueue) {
    this.instanceId = instanceId;
    this.sqs = sqs;
    this.defaultTopicArn = defaultTopicArn;
    this.defaultQueue = defaultQueue;
  }

  public void initialize() {
    initializeSubscription();
  }

  private void initializeSubscription() {
    // allow the default SNS topic to send messages to the default SQS queue
    String policy =
        """
        {
          "Version": "2012-10-17",
          "Statement": [
            {
              "Effect": "Allow",
              "Principal": {"Service": "sns.amazonaws.com"},
              "Action": "sqs:SendMessage",
              "Resource": "%s",
              "Condition": {
                "ArnEquals": {"aws:SourceArn": "%s"}
              }
            }
          ]
        }
        """
            .formatted(defaultQueue.url(), defaultTopicArn);

    sqs.setQueueAttributes(
        SetQueueAttributesRequest.builder()
            .queueUrl(defaultQueue.url())
            .attributes(Map.of(QueueAttributeName.POLICY, policy))
            .build());

    // subscribe the SQS queue to the SNS topic
    SnsClient sns = SnsClient.builder().build();

    SubscribeResponse sub =
        sns.subscribe(
            SubscribeRequest.builder()
                .topicArn(defaultTopicArn)
                .protocol("sqs")
                .endpoint(defaultQueue.arn())
                .returnSubscriptionArn(true)
                .build());

    String subscriptionArn = sub.subscriptionArn();

    // exclude own messages by setting a filter policy
    String filterPolicy =
        """
        {
          "instanceId": [{ "anything-but": "%s" }]
        }
        """
            .formatted(instanceId);

    sns.setSubscriptionAttributes(
        SetSubscriptionAttributesRequest.builder()
            .subscriptionArn(subscriptionArn)
            .attributeName("FilterPolicy")
            .attributeValue(filterPolicy)
            .build());
  }
}
