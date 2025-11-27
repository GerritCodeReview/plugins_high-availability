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
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.Command;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandProcessor;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandsGson;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gson.Gson;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

public class SqsMessagePoller {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    SqsMessagePoller create(SqsQueueInfo queueInfo);
  }

  private final SqsAsyncClient sqs;
  private final SqsQueueInfo queueInfo;
  private final Gson gson;
  private final ExecutorService executor;
  private final CommandProcessor processor;
  private final String instanceId;
  private final Configuration.PubSubAws awsConfig;

  private volatile boolean running;
  private volatile CompletableFuture<?> inFlightSqsRequest;

  @AssistedInject
  public SqsMessagePoller(
      SqsAsyncClient sqs,
      @CommandsGson Gson gson,
      @MessageProcessingExecutor ExecutorService executor,
      CommandProcessor processor,
      Configuration configuration,
      @GerritInstanceId String instanceId,
      @Assisted SqsQueueInfo queueInfo) {
    this.sqs = sqs;
    this.gson = gson;
    this.executor = executor;
    this.processor = processor;
    this.instanceId = instanceId;
    this.queueInfo = queueInfo;
    this.awsConfig = configuration.pubSubAws();
  }

  public void start() {
    running = true;
    CompletableFuture.runAsync(this::pollLoop, executor);
  }

  private void pollLoop() {
    if (!running) {
      return;
    }

    inFlightSqsRequest =
        sqs.receiveMessage(
                req ->
                    req.queueUrl(queueInfo.url())
                        .visibilityTimeout((int) awsConfig.visibilityTimeout().getSeconds())
                        .waitTimeSeconds((int) awsConfig.waitTime().getSeconds())
                        .maxNumberOfMessages(awsConfig.maxNumberOfMessages())
                        .messageAttributeNames("All"))
            .thenAcceptAsync(this::processResponseAsync, executor)
            .whenComplete(
                (res, ex) -> {
                  if (ex != null) {
                    logger.atSevere().withCause(ex).log(
                        "Error when polling messages from SQS queue %s", queueInfo);
                  }
                  pollLoop();
                });
  }

  private void processResponseAsync(ReceiveMessageResponse response) {
    logger.atFine().log(
        "processResponseAsync: Received %d messages from SQS queue %s",
        response.messages().size(), queueInfo);
    for (Message msg : response.messages()) {
      executor.submit(() -> processMessage(msg));
    }
  }

  @VisibleForTesting
  void processMessage(Message msg) {
    if (instanceId.equals(msg.messageAttributes().get("instanceId").stringValue())) {
      logger.atWarning().log(
          "Skipping message %s since it was sent by this instance %s", msg.messageId(), instanceId);
      sqs.deleteMessage(req -> req.queueUrl(queueInfo.url()).receiptHandle(msg.receiptHandle()));
      return;
    }
    logger.atInfo().log("Processing SQS message: %s", msg);
    if (processor.handle(getCommand(msg))) {
      // Delete message, it was processed successfully
      logger.atFine().log("Deleting SQS message: %s", msg);
      sqs.deleteMessage(req -> req.queueUrl(queueInfo.url()).receiptHandle(msg.receiptHandle()));
    }
  }

  private Command getCommand(Message msg) {
    try {
      return gson.fromJson(msg.body(), Command.class);
    } catch (RuntimeException e) {
      logger.atSevere().withCause(e).log("Error parsing message %s", msg);
      throw e;
    }
  }

  /** Stop polling gracefully */
  public void stop() {
    running = false;

    if (inFlightSqsRequest != null) {
      inFlightSqsRequest.cancel(true);
    }
  }
}
