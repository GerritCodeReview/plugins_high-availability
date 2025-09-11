// Copyright (C) 2017 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.events.Event;
import java.util.concurrent.CompletableFuture;

/** Forward indexing, stream events and cache evictions to the other primary */
public interface Forwarder {

  /**
   * Forward a account indexing event to the other primary.
   *
   * @param accountId the account to index.
   * @param indexEvent the details of the index event.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> indexAccount(int accountId, IndexEvent indexEvent);

  /**
   * Forward a change indexing event to the other primary.
   *
   * @param projectName the project of the change to index.
   * @param changeId the change to index.
   * @param indexEvent the details of the index event.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> indexChange(String projectName, int changeId, IndexEvent indexEvent);

  /**
   * Forward a change indexing event to the other primary using batch index endpoint.
   *
   * @param projectName the project of the change to index.
   * @param changeId the change to index.
   * @param indexEvent the details of the index event.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> batchIndexChange(
      String projectName, int changeId, IndexEvent indexEvent);

  /**
   * Forward a delete change from index event to the other primary.
   *
   * @param changeId the change to remove from the index.
   * @param indexEvent the details of the index event.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> deleteChangeFromIndex(int changeId, IndexEvent indexEvent);

  /**
   * Forward a group indexing event to the other primary.
   *
   * @param uuid the group to index.
   * @param indexEvent the details of the index event.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> indexGroup(String uuid, IndexEvent indexEvent);

  /**
   * Forward a project indexing event to the other primary.
   *
   * @param projectName the project to index.
   * @param indexEvent the details of the index event.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> indexProject(String projectName, IndexEvent indexEvent);

  /**
   * Forward a stream event to the other primary.
   *
   * @param event the event to forward.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> send(Event event);

  /**
   * Forward a cache eviction event to the other primary.
   *
   * @param cacheName the name of the cache to evict an entry from.
   * @param key the key identifying the entry to evict from the cache.
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> evict(String cacheName, Object key);

  /**
   * Forward an addition to the project list cache to the other primary.
   *
   * @param projectName the name of the project to add to the project list cache
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> addToProjectList(String projectName);

  /**
   * Forward a removal from the project list cache to the other primary.
   *
   * @param projectName the name of the project to remove from the project list cache
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> removeFromProjectList(String projectName);

  /**
   * Forward the removal of all project changes from index to the other primary.
   *
   * @param projectName the name of the project whose changes should be removed from the index
   * @return {@link CompletableFuture} of true if successful, otherwise {@link CompletableFuture} of
   *     false.
   */
  CompletableFuture<Boolean> deleteAllChangesForProject(Project.NameKey projectName);
}
