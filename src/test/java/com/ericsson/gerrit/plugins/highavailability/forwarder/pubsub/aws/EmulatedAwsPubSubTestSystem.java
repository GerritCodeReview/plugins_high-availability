// Copyright (C) 2026 The Android Open Source Project
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

import java.net.URI;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

public class EmulatedAwsPubSubTestSystem extends AwsPubSubTestSystem {
  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:3.0.2");
  private LocalStackContainer localstack;
  private String region = "us-east-1";
  private String accessKey = "test";
  private String secretKey = "test";

  @Override
  public void start() {
    localstack =
        new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);
    localstack.start();
  }

  @Override
  public void stop() {
    if (localstack != null) {
      localstack.stop();
      localstack.close();
    }
  }

  @Override
  public URI getEndpoint() {
    return localstack.getEndpoint();
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public String getAccessKeyLocation() {
    return accessKey;
  }

  @Override
  public String getSecretKeyLocation() {
    return secretKey;
  }

  @Override
  public String getDefaultTopicArn() {
    return null;
  }

  @Override
  public String getDefaultTopicName() {
    return "default";
  }

  @Override
  public String getStreamEventsTopicName() {
    return "stream-events";
  }
}
