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

package com.ericsson.gerrit.plugins.highavailability.indexsync;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfo;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import dev.failsafe.function.CheckedSupplier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

public class IndexSyncRunner implements CheckedSupplier<Boolean> {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  interface Factory {
    IndexSyncRunner create(String age);
  }

  private final Provider<Set<PeerInfo>> peerInfoProvider;
  private final CloseableHttpClient httpClient;
  private final String pluginRelativePath;
  private final QueryChangesResponseHandler queryChangesResponseHandler;
  private final ChangeIndexer.Factory changeIndexerFactory;
  private final ListeningExecutorService executor;
  private final ChangeIndexCollection changeIndexes;
  private final ChangeQueryBuilder queryBuilder;
  private final Provider<ChangeQueryProcessor> queryProcessorProvider;
  private final ChangeFinder changeFinder;
  private final String age;

  @AssistedInject
  IndexSyncRunner(
      Provider<Set<PeerInfo>> peerInfoProvider,
      CloseableHttpClient httpClient,
      @PluginName String pluginName,
      QueryChangesResponseHandler queryChangesResponseHandler,
      ChangeIndexer.Factory changeIndexerFactory,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      ChangeIndexCollection changeIndexes,
      ChangeQueryBuilder queryBuilder,
      Provider<ChangeQueryProcessor> queryProcessorProvider,
      ChangeFinder changeFinder,
      @Assisted String age) {
    this.peerInfoProvider = peerInfoProvider;
    this.httpClient = httpClient;
    this.pluginRelativePath = Joiner.on("/").join("plugins", pluginName);
    this.queryChangesResponseHandler = queryChangesResponseHandler;
    this.changeIndexerFactory = changeIndexerFactory;
    this.executor = executor;
    this.changeIndexes = changeIndexes;
    this.queryBuilder = queryBuilder;
    this.queryProcessorProvider = queryProcessorProvider;
    this.changeFinder = changeFinder;
    this.age = age;
  }

  @Override
  public Boolean get() {
    log.atFine().log("Starting indexSync");
    Set<PeerInfo> peers = peerInfoProvider.get();
    if (peers.size() == 0) {
      return false;
    }

    ChangeIndexer indexer = changeIndexerFactory.create(executor, changeIndexes, false);
    // NOTE: this loop will stop as soon as the initial sync is performed from one peer
    for (PeerInfo peer : peers) {
      if (syncFrom(peer, indexer)) {
        log.atFine().log("Finished indexSync");
        return true;
      }
    }

    return false;
  }

  private boolean syncFrom(PeerInfo peer, ChangeIndexer indexer) {
    log.atFine().log("Syncing index with %s", peer.getDirectUrl());
    String peerUrl = peer.getDirectUrl();
    String uri =
        Joiner.on("/").join(peerUrl, pluginRelativePath, "query/changes.updated.since", age);
    HttpGet queryRequest = new HttpGet(uri);
    List<String> ids;
    try {
      log.atFine().log("Executing %s", queryRequest);
      ids = httpClient.execute(queryRequest, queryChangesResponseHandler);
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Error while querying changes from %s", uri);
      return false;
    }

    try {
      List<ListenableFuture<Boolean>> indexingTasks = new ArrayList<>(ids.size());
      for (String id : ids) {
        indexingTasks.add(indexAsync(id, indexer));
      }
      Futures.allAsList(indexingTasks).get();
    } catch (InterruptedException | ExecutionException e) {
      log.atSevere().withCause(e).log("Error while reindexing %s", ids);
      return false;
    }

    syncChangeDeletions(ids, indexer);

    return true;
  }

  private ListenableFuture<Boolean> indexAsync(String id, ChangeIndexer indexer) {
    List<String> fields = Splitter.on("~").splitToList(id);
    if (fields.size() != 2) {
      throw new IllegalArgumentException(String.format("Unexpected change ID format %s", id));
    }
    Project.NameKey projectName = Project.nameKey(fields.get(0));
    Integer changeNumber = Ints.tryParse(fields.get(1));
    if (changeNumber == null) {
      throw new IllegalArgumentException(
          String.format("Unexpected change number format %s", fields.get(1)));
    }
    log.atInfo().log("Scheduling async reindex of: %s", id);
    return indexer.asyncReindexIfStale(projectName, Change.id(changeNumber));
  }

  private void syncChangeDeletions(List<String> theirChanges, ChangeIndexer indexer) {
    Set<String> ourChanges = queryLocalIndex();
    for (String d : Sets.difference(ourChanges, ImmutableSet.copyOf(theirChanges))) {
      deleteIfMissingInNoteDb(d, indexer);
    }
  }

  private Set<String> queryLocalIndex() {
    ChangeQueryProcessor queryProcessor = queryProcessorProvider.get();
    queryProcessor.enforceVisibility(false);
    queryProcessor.setNoLimit(true);
    Predicate<ChangeData> predicate = Predicate.not(queryBuilder.age(age));
    QueryResult<ChangeData> result;
    try {
      result = queryProcessor.query(predicate);
    } catch (QueryParseException e) {
      throw new RuntimeException(e);
    }

    ImmutableList<ChangeData> cds = result.entities();
    return cds.stream()
        .map(cd -> cd.project().get() + "~" + cd.getId().get())
        .collect(Collectors.toSet());
  }

  private void deleteIfMissingInNoteDb(String id, ChangeIndexer indexer) {
    List<ChangeNotes> changeNotes = changeFinder.find(id);
    if (changeNotes.isEmpty()) {
      List<String> parts = Splitter.on("~").splitToList(id);
      Project.NameKey project = Project.nameKey(parts.get(0));
      Change.Id changeId = Change.id(Integer.parseInt(parts.get(1)));
      log.atInfo().log("Change %s present in index but not in noteDb. Deleting from index", id);
      indexer.deleteAsync(project, changeId);
    }
  }
}
