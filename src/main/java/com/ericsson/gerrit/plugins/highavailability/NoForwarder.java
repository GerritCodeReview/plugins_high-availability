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

package com.ericsson.gerrit.plugins.highavailability;

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.events.Event;
import java.util.concurrent.CompletableFuture;

public class NoForwarder implements Forwarder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public CompletableFuture<Result> indexAccount(int accountId, IndexEvent indexEvent) {
    logger.atWarning().log("NoForwarder: indexAccount called with accountId=%d", accountId);
    return recoverableFailure(EventType.INDEX_ACCOUNT_UPDATE);
  }

  @Override
  public CompletableFuture<Result> indexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return recoverableFailure(EventType.INDEX_CHANGE_UPDATE);
  }

  @Override
  public CompletableFuture<Result> batchIndexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return recoverableFailure(EventType.INDEX_CHANGE_UPDATE_BATCH);
  }

  @Override
  public CompletableFuture<Result> deleteChangeFromIndex(int changeId, IndexEvent indexEvent) {
    return recoverableFailure(EventType.INDEX_CHANGE_DELETION);
  }

  @Override
  public CompletableFuture<Result> indexGroup(String uuid, IndexEvent indexEvent) {
    return recoverableFailure(EventType.INDEX_GROUP_UPDATE);
  }

  @Override
  public CompletableFuture<Result> indexProject(String projectName, IndexEvent indexEvent) {
    return recoverableFailure(EventType.INDEX_PROJECT_UPDATE);
  }

  @Override
  public CompletableFuture<Result> send(Event event) {
    return recoverableFailure(EventType.EVENT_SENT);
  }

  @Override
  public CompletableFuture<Result> evict(String cacheName, Object key) {
    return recoverableFailure(EventType.CACHE_EVICTION);
  }

  @Override
  public CompletableFuture<Result> addToProjectList(String projectName) {
    return recoverableFailure(EventType.PROJECT_LIST_ADDITION);
  }

  @Override
  public CompletableFuture<Result> removeFromProjectList(String projectName) {
    return recoverableFailure(EventType.PROJECT_LIST_DELETION);
  }

  @Override
  public CompletableFuture<Result> deleteAllChangesForProject(NameKey projectName) {
    return recoverableFailure(EventType.INDEX_CHANGE_DELETION_ALL_OF_PROJECT);
  }

  private CompletableFuture<Result> recoverableFailure(EventType eventType) {
    return CompletableFuture.completedFuture(new Result(eventType, false, true));
  }
}
