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

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.gerrit.server.StartupException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;

@Singleton
public class GCPPublisherFactory implements PublisherFactory {
  private final CredentialsProvider credentials;
  private final ExecutorProvider executor;

  @Inject
  public GCPPublisherFactory(
      CredentialsProvider credentials, @PublisherExecutorProvider ExecutorProvider executor) {
    this.credentials = credentials;
    this.executor = executor;
  }

  @Override
  public Publisher create(TopicName topic) {
    try {
      return Publisher.newBuilder(topic)
          .setExecutorProvider(executor)
          .setCredentialsProvider(credentials)
          .build();
    } catch (IOException e) {
      throw new StartupException("Failed to create publisher for PubSub.", e);
    }
  }
}
