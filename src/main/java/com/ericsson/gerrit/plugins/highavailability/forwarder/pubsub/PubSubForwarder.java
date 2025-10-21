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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub;

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
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PubSubForwarder implements Forwarder {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final Publisher defaultPublisher;
  private final Publisher streamEventsPublisher;
  private final Gson gson;
  private final String instanceId;

  @Inject
  PubSubForwarder(
      @DefaultTopic Publisher defaultPublisher,
      @StreamEventsTopic Publisher streamEventsPublisher,
      @CommandsGson Gson gson,
      @GerritInstanceId String instanceId) {
    this.defaultPublisher = defaultPublisher;
    this.streamEventsPublisher = streamEventsPublisher;
    this.gson = gson;
    this.instanceId = instanceId;
  }

  @Override
  public CompletableFuture<Boolean> indexAccount(int accountId, IndexEvent indexEvent) {
    return execute(new IndexAccount(accountId), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> indexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Update(projectName, changeId), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> batchIndexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Update(projectName, changeId, true), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> deleteChangeFromIndex(int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Delete(changeId), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> indexGroup(String uuid, IndexEvent indexEvent) {
    return execute(new IndexGroup(uuid), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> indexProject(String projectName, IndexEvent indexEvent) {
    return execute(new IndexProject(projectName), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> send(Event event) {
    return execute(new PostEvent(event), streamEventsPublisher);
  }

  @Override
  public CompletableFuture<Boolean> evict(String cacheName, Object key) {
    return execute(new EvictCache(cacheName, gson.toJson(key)), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> addToProjectList(String projectName) {
    return execute(new AddToProjectList(projectName), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> removeFromProjectList(String projectName) {
    return execute(new RemoveFromProjectList(projectName), defaultPublisher);
  }

  @Override
  public CompletableFuture<Boolean> deleteAllChangesForProject(Project.NameKey projectName) {
    // TODO
    return null;
  }

  private PubsubMessage buildMessage(Command cmd) {
    return PubsubMessage.newBuilder()
        .putAttributes("instanceId", instanceId)
        .setData(ByteString.copyFromUtf8(gson.toJson(cmd)))
        .build();
  }

  private CompletableFuture<Boolean> execute(Command cmd, Publisher publisher) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    String msg = gson.toJson(cmd);
    log.atInfo().log("Publishing message: %s", msg);
    ApiFutures.addCallback(
        publisher.publish(buildMessage(cmd)),
        new ApiFutureCallback<String>() {

          @Override
          public void onFailure(Throwable t) {
            log.atSevere().withCause(t).log("Failed to publish message: %s", msg);
            future.complete(false);
          }

          @Override
          public void onSuccess(String result) {
            log.atFine().log("Published message (id: %s): %s", result, msg);
            future.complete(true);
          }
        },
        MoreExecutors.directExecutor());
    return future;
  }
}
