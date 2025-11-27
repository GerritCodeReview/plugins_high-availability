package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

@Singleton
public class SqsMessagePoller implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SqsAsyncClient sqs;
  private final SqsQueueInfo queueInfo;

  private transient boolean running;

  @Inject
  public SqsMessagePoller(SqsAsyncClient sqs, @DefaultQueue SqsQueueInfo queueInfo) {
    this.sqs = sqs;
    this.queueInfo = queueInfo;
  }

  @Override
  public void start() {
    running = true;
    pollLoop();
  }

  private void pollLoop() {
    if (!running) return;

    ReceiveMessageRequest request =
        ReceiveMessageRequest.builder()
            .queueUrl(queueInfo.url())
            .waitTimeSeconds(20)
            .maxNumberOfMessages(10)
            .messageAttributeNames("All")
            .build();

    sqs.receiveMessage(request)
        .thenAccept(
            response -> {
              List<Message> messages = response.messages();
              if (!messages.isEmpty()) {
                messages.forEach(this::handleMessageAsync);
              }
            })
        .whenComplete(
            (res, ex) -> {
              if (ex != null) {
                logger.atSevere().withCause(ex).log(
                    "Error when polling messages from SQS queue %s", queueInfo);
              }
              // Continue polling regardless of success or failure
              pollLoop();
            });
  }

  /** Process a single message asynchronously */
  private void handleMessageAsync(Message msg) {
    logger.atInfo().log("Received message: %s", msg.body());
    // TODO: Add actual message processing logic here
  }

  /** Stop polling gracefully */
  @Override
  public void stop() {
    running = false;
  }
}
