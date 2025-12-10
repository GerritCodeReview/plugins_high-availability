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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.gcp;

import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.TopicNames;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.pubsub.v1.TopicName;
import java.util.List;

@Singleton
public class SubscriptionNames {

  private final List<String> names;
  private final String gCloudProject;
  private final String instanceId;

  public static String getSubscriptionName(String instanceId, String topic) {
    return instanceId + "-" + topic;
  }

  @Inject
  SubscriptionNames(
      @GerritInstanceId String instanceId,
      @GCloudProject String gCloudProject,
      TopicNames topicNames) {
    this.instanceId = instanceId;
    this.gCloudProject = gCloudProject;
    this.names = initializeNames(topicNames);
  }

  public String nameFor(TopicName topic) {
    return instanceId + "-" + topic.getTopic();
  }

  public List<String> all() {
    return names;
  }

  private List<String> initializeNames(TopicNames topicNames) {
    return topicNames.all().stream()
        .map(name -> TopicName.of(gCloudProject, name))
        .map(topic -> instanceId + "-" + topic.getTopic())
        .toList();
  }
}
