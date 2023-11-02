// Copyright (C) 2018 The Android Open Source Project
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
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Index a project using {@link ProjectIndexer}. This class is meant to be used on the receiving
 * side of the {@link Forwarder} since it will prevent indexed project to be forwarded again causing
 * an infinite forwarding loop between the 2 nodes. It will also make sure no concurrent indexing is
 * done for the same project name.
 */
@Singleton
public class ForwardedIndexProjectHandler extends ForwardedIndexingHandler<Project.NameKey> {
  private final ProjectIndexer indexer;

  @Inject
  ForwardedIndexProjectHandler(ProjectIndexer indexer) {
    this.indexer = indexer;
  }

  @Override
  protected CompletableFuture<Boolean> doIndex(
      Project.NameKey projectName, Optional<IndexEvent> indexEvent) {
    indexer.index(projectName);
    log.atFine().log("Project %s successfully indexed", projectName);
    return CompletableFuture.completedFuture(true);
  }

  @Override
  protected CompletableFuture<Boolean> doDelete(
      Project.NameKey projectName, Optional<IndexEvent> indexEvent) {
    throw new UnsupportedOperationException("Delete from project index not supported");
  }
}
