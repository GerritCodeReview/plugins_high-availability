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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.TopicName;

@Singleton
public class ProjectSubscriptionNameFactory {
  private final String instanceId;
  private final Configuration pluginConfiguration;

  @Inject
  ProjectSubscriptionNameFactory(
      @GerritInstanceId String instanceId, Configuration pluginConfiguration) {
    this.instanceId = instanceId;
    this.pluginConfiguration = pluginConfiguration;
  }

  public ProjectSubscriptionName create(TopicName topic) {
    String subscriptionId = String.format("%s-%s", instanceId, topic.getTopic());
    return ProjectSubscriptionName.of(
        pluginConfiguration.pubSubGcp().gCloudProject(), subscriptionId);
  }
}
