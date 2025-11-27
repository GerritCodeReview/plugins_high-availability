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
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.DeleteAllProjectChangesFromIndex;
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
import java.time.Instant;
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
  public CompletableFuture<Result> indexAccount(int accountId, IndexEvent indexEvent) {
    return execute(new IndexAccount(accountId, indexEvent.eventCreatedOn), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> indexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(
        new IndexChange.Update(projectName, changeId, indexEvent.eventCreatedOn), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> batchIndexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(
        new IndexChange.BatchUpdate(projectName, changeId, indexEvent.eventCreatedOn),
        defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> deleteChangeFromIndex(int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Delete(changeId, indexEvent.eventCreatedOn), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> indexGroup(String uuid, IndexEvent indexEvent) {
    return execute(new IndexGroup(uuid, indexEvent.eventCreatedOn), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> indexProject(String projectName, IndexEvent indexEvent) {
    return execute(new IndexProject(projectName, indexEvent.eventCreatedOn), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> send(Event event) {
    return execute(new PostEvent(event, Instant.now()), streamEventsTopicArn);
  }

  @Override
  public CompletableFuture<Result> evict(String cacheName, Object key) {
    return execute(new EvictCache(cacheName, gson.toJson(key), Instant.now()), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> addToProjectList(String projectName) {
    return execute(new AddToProjectList(projectName, Instant.now()), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> removeFromProjectList(String projectName) {
    return execute(new RemoveFromProjectList(projectName, Instant.now()), defaultTopicArn);
  }

  @Override
  public CompletableFuture<Result> deleteAllChangesForProject(NameKey projectName) {
    return execute(
        new DeleteAllProjectChangesFromIndex(projectName, Instant.now()), defaultTopicArn);
  }

  private CompletableFuture<Result> execute(Command cmd, String topicArn) {
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
        .thenApply(r -> new Result(cmd.type, r.sdkHttpResponse().isSuccessful()));
  }
}
