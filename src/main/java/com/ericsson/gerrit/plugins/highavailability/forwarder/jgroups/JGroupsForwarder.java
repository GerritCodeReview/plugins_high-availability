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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.JGroups;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwarderMetricsRegistry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.failsafe.FailsafeExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import org.jgroups.Address;
import org.jgroups.ObjectMessage;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;

@Singleton
public class JGroupsForwarder implements Forwarder {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final MessageDispatcher dispatcher;
  private final JGroups jgroupsConfig;
  private final Gson gson;
  private final FailsafeExecutor<Boolean> executor;
  private final ForwarderMetricsRegistry metricsRegistry;

  @Inject
  JGroupsForwarder(
      MessageDispatcher dispatcher,
      Configuration cfg,
      @JGroupsGson Gson gson,
      @JGroupsForwarderExecutor FailsafeExecutor<Boolean> executor,
      ForwarderMetricsRegistry metricsRegistry) {
    this.dispatcher = dispatcher;
    this.jgroupsConfig = cfg.jgroups();
    this.gson = gson;
    this.executor = executor;

    this.metricsRegistry = metricsRegistry;
  }

  @Override
  public CompletableFuture<Boolean> indexAccount(int accountId, IndexEvent indexEvent) {
    return execute(new IndexAccount(accountId), indexEvent.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Boolean> indexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Update(projectName, changeId), indexEvent.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Boolean> batchIndexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.BatchUpdate(projectName, changeId), indexEvent.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Boolean> deleteChangeFromIndex(int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Delete(changeId), indexEvent.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Boolean> indexGroup(String uuid, IndexEvent indexEvent) {
    return execute(new IndexGroup(uuid), indexEvent.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Boolean> indexProject(String projectName, IndexEvent indexEvent) {
    return execute(new IndexProject(projectName), indexEvent.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Boolean> send(Event event) {
    return execute(new PostEvent(event), event.eventCreatedOn);
  }

  @Override
  public CompletableFuture<Boolean> evict(String cacheName, Object key) {
    return execute(new EvictCache(cacheName, gson.toJson(key)), Instant.now().toEpochMilli());
  }

  @Override
  public CompletableFuture<Boolean> addToProjectList(String projectName) {
    return execute(new AddToProjectList(projectName), Instant.now().toEpochMilli());
  }

  @Override
  public CompletableFuture<Boolean> removeFromProjectList(String projectName) {
    return execute(new RemoveFromProjectList(projectName), Instant.now().toEpochMilli());
  }

  @Override
  public CompletableFuture<Boolean> deleteAllChangesForProject(Project.NameKey projectName) {
    return execute(new DeleteAllProjectChangesFromIndex(projectName), Instant.now().toEpochMilli());
  }

  private CompletableFuture<Boolean> execute(Command cmd, long requestStart) {
    return executor
        .getAsync(() -> executeOnce(cmd))
        .thenApplyAsync(
            result -> {
              metricsRegistry.get(cmd.type).recordResult(result);
              metricsRegistry
                  .get(cmd.type)
                  .recordLatency(Instant.now().toEpochMilli() - requestStart);
              return result;
            });
  }

  private boolean executeOnce(Command cmd) {
    String json = gson.toJson(cmd);
    try {
      logJGroupsInfo();

      if (dispatcher.getChannel().getView().size() < 2) {
        log.atFine().log("Less than two members in cluster, not sending %s", json);
        return false;
      }

      log.atFine().log("Sending %s", json);
      RequestOptions options =
          new RequestOptions(ResponseMode.GET_FIRST, jgroupsConfig.timeout().toMillis());
      RspList<Object> list = dispatcher.castMessage(null, new ObjectMessage(null, json), options);

      log.atFine().log("Received response list length = %s", list.size());
      if (list.isEmpty()) {
        return false;
      }

      for (Entry<Address, Rsp<Object>> e : list.entrySet()) {
        log.atFine().log("Response object %s", e);
        if (!Boolean.TRUE.equals(e.getValue().getValue())) {
          log.atWarning().log(
              "Received a non TRUE response from receiver %s: %s",
              e.getKey(), e.getValue().getValue());
          return false;
        }
      }
      log.atFine().log("Successfully sent message %s", json);
      return true;
    } catch (Exception e) {
      log.atWarning().withCause(e).log("Forwarding %s failed", json);
      return false;
    }
  }

  private void logJGroupsInfo() {
    log.atFine().log("My address: %s", dispatcher.getChannel().getAddress());
    List<Address> members = dispatcher.getChannel().getView().getMembers();
    for (Address m : members) {
      log.atFine().log("Member: %s", m);
    }
  }
}
