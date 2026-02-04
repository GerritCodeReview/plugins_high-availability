// Copyright (C) 2023 The Android Open Source Project
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

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

public class EmulatedPubSub extends PubSubTestSystem {
  public static final String PROJECT_ID = "test";
  public static final String DEFAULT_TOPIC = "gerrit";
  public static final String STREAM_EVENTS_TOPIC = "stream-events";
  public static final TopicName TOPIC_NAME = TopicName.of(PROJECT_ID, DEFAULT_TOPIC);
  public static final TopicName STREAM_EVENTS_TOPIC_NAME =
      TopicName.of(PROJECT_ID, STREAM_EVENTS_TOPIC);

  private final PubSubEmulatorContainer container;
  private final FixedTransportChannelProvider channelProvider;
  private final String hostPort;

  public EmulatedPubSub() throws Exception {
    container =
        new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:470.0.0-emulators"));
    container.start();
    hostPort = container.getEmulatorEndpoint();
    ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
    channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
    System.out.println(
        String.format(
            "PubSub emulator container started and is listening on %s",
            container.getEmulatorEndpoint()));
  }

  @Override
  public void reset() throws Exception {}

  @Override
  public void cleanup() throws Exception {
    container.stop();
    container.close();
  }

  public String getHostPort() {
    return hostPort;
  }

  @Override
  String getProjectId() {
    return PROJECT_ID;
  }

  @Override
  String getTopicName() {
    return DEFAULT_TOPIC;
  }

  @Override
  String getStreamEventsTopicName() {
    return STREAM_EVENTS_TOPIC;
  }

  @Override
  String getPrivateKeyFilePath() {
    return null;
  }

  @Override
  CredentialsProvider getCredentials() throws Exception {
    return NoCredentialsProvider.create();
  }
}
