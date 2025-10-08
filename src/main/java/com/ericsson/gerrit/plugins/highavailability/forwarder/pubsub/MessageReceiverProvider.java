// Copyright (C) 2024 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.pubsub.v1.PubsubMessage;

@Singleton
public class MessageReceiverProvider implements Provider<MessageReceiver> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Configuration config;
  private final PubSubMessageProcessor processor;

  @Inject
  public MessageReceiverProvider(Configuration config, PubSubMessageProcessor processor) {
    this.config = config;
    this.processor = processor;
  }

  @Override
  public MessageReceiver get() {
    return (PubsubMessage message, AckReplyConsumer consumer) -> {
      try {
        logger.atFine().log("Received message: %s", message);
        if (processor.handle(message)) {
          consumer.ack();
        } else {
          consumer.nack();
        }
      } catch (Exception e) {
        consumer.nack();
        logger.atSevere().withCause(e).log(
            "Exception when consuming message %s from topic %s [message: %s]",
            message.getMessageId(), config.pubSub().topic(), message.getData().toStringUtf8());
      }
    };
  }
}
