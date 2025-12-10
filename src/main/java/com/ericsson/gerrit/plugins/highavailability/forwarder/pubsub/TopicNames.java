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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class TopicNames {
  private final String defaultTopic;
  private final String streamEventsTopic;

  @Inject
  public TopicNames(Configuration config) {
    defaultTopic = config.pubSub().defaultTopic();
    streamEventsTopic = config.pubSub().streamEventsTopic();
  }

  public String defaultTopic() {
    return defaultTopic;
  }

  public String streamEventsTopic() {
    return streamEventsTopic;
  }

  public List<String> all() {
    return List.of(defaultTopic, streamEventsTopic);
  }
}
