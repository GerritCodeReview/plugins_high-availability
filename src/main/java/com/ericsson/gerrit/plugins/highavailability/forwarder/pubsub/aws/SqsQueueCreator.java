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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Creates SQS queue and subscribes it to an SNS topic. Also creates a dead-letter queue and
 * configures redrive policy.
 */
@Singleton
public class SqsQueueCreator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String instanceId;
  private final SnsAsyncClient sns;
  private final SqsAsyncClient sqs;
  private final Configuration config;

  @Inject
  SqsQueueCreator(
      @GerritInstanceId String instanceId,
      SnsAsyncClient sns,
      SqsAsyncClient sqs,
      Configuration config) {
    this.instanceId = instanceId;
    this.sns = sns;
    this.sqs = sqs;
    this.config = config;
  }

  public SqsQueueInfo create(String queueName, String topicArn)
      throws InterruptedException, ExecutionException {
    SqsQueueInfo queueInfo = createQueue(queueName);
    initializeDQL(queueInfo);
    initializeSubscription(queueInfo, topicArn);

    return queueInfo;
  }

  private SqsQueueInfo createQueue(String queueName)
      throws InterruptedException, ExecutionException {
    String queueUrl =
        sqs.createQueue(req -> req.queueName(queueName))
            .thenApply(CreateQueueResponse::queueUrl)
            .get();

    // Get queue ARN
    String queueArn =
        sqs.getQueueAttributes(
                req -> req.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
            .thenApply(r -> r.attributes().get(QueueAttributeName.QUEUE_ARN))
            .get();

    return new SqsQueueInfo(queueName, queueUrl, queueArn);
  }

  private void initializeDQL(SqsQueueInfo sourceQueue)
      throws InterruptedException, ExecutionException {
    String dlqUrl =
        sqs.createQueue(rep -> rep.queueName(sourceQueue.name() + "-dlq"))
            .thenApply(rsp -> rsp.queueUrl())
            .get();

    String dlqArn =
        sqs.getQueueAttributes(
                req -> req.queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
            .thenApply(rsp -> rsp.attributes().get(QueueAttributeName.QUEUE_ARN))
            .get();

    int maxReceiveCount = config.pubSubAws().maxReceiveCount();
    String redrivePolicy =
        String.format(
            """
            {"maxReceiveCount":"%d", "deadLetterTargetArn":"%s"}
            """,
            maxReceiveCount, dlqArn);

    sqs.setQueueAttributes(
            req ->
                req.queueUrl(sourceQueue.url())
                    .attributes(Map.of(QueueAttributeName.REDRIVE_POLICY, redrivePolicy)))
        .get();
  }

  private void initializeSubscription(SqsQueueInfo queueInfo, String topicArn)
      throws InterruptedException, ExecutionException {
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
            .formatted(queueInfo.arn(), topicArn);

    sqs.setQueueAttributes(
        req -> req.queueUrl(queueInfo.url()).attributes(Map.of(QueueAttributeName.POLICY, policy)));

    SubscribeResponse sub =
        sns.subscribe(
                req ->
                    req.topicArn(topicArn)
                        .protocol("sqs")
                        .endpoint(queueInfo.arn())
                        .returnSubscriptionArn(true))
            .get();

    String subscriptionArn = sub.subscriptionArn();

    sns.setSubscriptionAttributes(
        req ->
            req.subscriptionArn(subscriptionArn)
                .attributeName("RawMessageDelivery")
                .attributeValue("true"));

    logger.atInfo().log(
        "Subscribed SQS queue %s to SNS topic %s with subscription ARN %s",
        queueInfo, topicArn, subscriptionArn);

    // exclude own messages by setting a filter policy
    String filterPolicy =
        """
        {
          "instanceId": [{ "anything-but": "%s" }]
        }
        """
            .formatted(instanceId);

    sns.setSubscriptionAttributes(
        req ->
            req.subscriptionArn(subscriptionArn)
                .attributeName("FilterPolicy")
                .attributeValue(filterPolicy));
  }
}
