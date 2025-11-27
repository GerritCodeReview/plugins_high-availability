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

public abstract class AwsPubSubTestSystem {

  public static AwsPubSubTestSystem create() {

    if (System.getenv("TEST_REAL_PUBSUB") == null) {
      return new EmulatedAwsPubSubTestSystem();
    }
    return new RealAwsPubSubTestSystem();
  }

  public abstract void start();

  public abstract void stop();

  public abstract URI getEndpoint();

  public abstract String getRegion();

  public abstract String getAccessKeyLocation();

  public abstract String getSecretKeyLocation();

  public abstract String getDefaultTopicArn();

  public abstract String getDefaultTopicName();

  public abstract String getStreamEventsTopicName();
}
