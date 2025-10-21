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
import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;

public abstract class PubSubTestSystem {
  final Configuration cfg;

  public static PubSubTestSystem create(Configuration cfg) throws Exception {
    PubSubTestSystem testSystem;
    if (System.getenv("TEST_REAL_PUBSUB") == null) {
      testSystem = new EmulatedPubSub(cfg);
    } else {
      testSystem = RealPubSub.create(cfg);
    }
    testSystem.reset();
    return testSystem;
  }

  protected PubSubTestSystem(Configuration cfg) {
    this.cfg = cfg;
  }

  abstract String getProjectId();

  abstract CredentialsProvider getCredentials() throws Exception;

  abstract TopicAdminClient getTopicAdminClient() throws Exception;

  abstract SubscriptionAdminClient getSubscriptionAdminClient() throws Exception;

  abstract Publisher getPublisher() throws Exception;

  abstract Publisher getStreamEventsPublisher() throws Exception;

  abstract Subscriber getSubscriber(PubSubMessageProcessor processor, String instanceId)
      throws Exception;

  abstract void reset() throws Exception;

  abstract void cleanup() throws Exception;
}
