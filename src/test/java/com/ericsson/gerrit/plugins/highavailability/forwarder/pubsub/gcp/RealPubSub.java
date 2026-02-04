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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.gcp;

import com.google.api.client.util.Strings;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.pubsub.v1.TopicName;
import java.io.FileInputStream;

public class RealPubSub extends PubSubTestSystem {
  private static RealPubSub INSTANCE;

  public static RealPubSub create() {
    if (INSTANCE == null) {
      String keyPath = getEnv("GOOGLE_APPLICATION_CREDENTIALS");
      String project = getEnv("GCP_PROJECT");
      String topic = System.getenv("PUBSUB_TOPIC");
      String streamEventsTopic = System.getenv("PUBSUB_STREAM_EVENTS_TOPIC");
      if (Strings.isNullOrEmpty(topic)) {
        INSTANCE = new RealPubSub(keyPath, project);
      } else {
        INSTANCE = new RealPubSub(keyPath, project, topic, streamEventsTopic);
      }
    }
    return INSTANCE;
  }

  private static String getEnv(String name) {
    String value = System.getenv(name);
    if (Strings.isNullOrEmpty(value)) {
      throw new IllegalStateException("Environment variable " + name + " is not set");
    }
    return value;
  }

  private final String keyPath;
  private final TopicName topicName;
  private final TopicName streamEventsTopicName;

  private RealPubSub(String keyPath, String project) {
    this(
        keyPath,
        project,
        "gerrit-pubsub-" + System.currentTimeMillis(),
        "gerrit-stream-events-" + System.currentTimeMillis());
  }

  private RealPubSub(String keyPath, String project, String topic, String streamEventsTopic) {
    this.keyPath = keyPath;
    this.topicName = TopicName.of(project, topic);
    this.streamEventsTopicName = TopicName.of(project, streamEventsTopic);
  }

  @Override
  public void reset() throws Exception {}

  @Override
  void cleanup() throws Exception {}

  @Override
  String getProjectId() {
    return topicName.getProject();
  }

  @Override
  String getTopicName() {
    return topicName.getTopic();
  }

  @Override
  String getStreamEventsTopicName() {
    return streamEventsTopicName.getTopic();
  }

  @Override
  String getPrivateKeyFilePath() {
    return keyPath;
  }

  @Override
  CredentialsProvider getCredentials() throws Exception {
    return FixedCredentialsProvider.create(
        ServiceAccountCredentials.fromStream(new FileInputStream(keyPath)));
  }
}
