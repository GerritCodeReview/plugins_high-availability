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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.AddToProjectList;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.Command;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandsGson;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.EvictCache;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexAccount;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexChange;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexGroup;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexProject;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.PostEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.RemoveFromProjectList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

@Singleton
public class AwsPubSubForwarder implements Forwarder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Gson gson;
  private final String instanceId;
  private final SnsAsyncClient sns;
  private final String defaultTopicArn;
  private final String streamEventsTopicArn;

  @Inject
  AwsPubSubForwarder(
      @CommandsGson Gson gson,
      @GerritInstanceId String instanceId,
      SnsAsyncClient sns,
      @DefaultTopic String defaultTopicArn,
      @StreamEventsTopic String streamEventsTopicArn) {
    this.gson = gson;
    this.instanceId = instanceId;
    this.sns = sns;
    this.defaultTopicArn = defaultTopicArn;
    this.streamEventsTopicArn = streamEventsTopicArn;
  }

  @Override
  public CompletableFuture<Boolean> indexAccount(int accountId, IndexEvent indexEvent) {
    return execute(new IndexAccount(accountId), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> indexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Update(projectName, changeId), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> batchIndexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Update(projectName, changeId, true), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> deleteChangeFromIndex(int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Delete(changeId), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> indexGroup(String uuid, IndexEvent indexEvent) {
    return execute(new IndexGroup(uuid), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> indexProject(String projectName, IndexEvent indexEvent) {
    return execute(new IndexProject(projectName), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> send(Event event) {
    return execute(new PostEvent(event), streamEventsTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> evict(String cacheName, Object key) {
    return execute(new EvictCache(cacheName, gson.toJson(key)), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> addToProjectList(String projectName) {
    return execute(new AddToProjectList(projectName), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> removeFromProjectList(String projectName) {
    return execute(new RemoveFromProjectList(projectName), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Boolean> deleteAllChangesForProject(NameKey projectName) {
    // TODO
    return null;
  }

  private CompletableFuture<Boolean> execute(Command cmd, String topicArn) {
    String msg = gson.toJson(cmd);
    logger.atFine().log("Forwarding message: %s", msg);
    return sns.publish(
            req ->
                req.topicArn(topicArn)
                    .message(msg)
                    .messageAttributes(
                        Map.of(
                            "instanceId",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(instanceId)
                                .build())))
        .thenApply(r -> r.sdkHttpResponse().isSuccessful());
  }
}
