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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class ChangeCheckerImpl implements ChangeChecker {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final GitRepositoryManager gitRepoMgr;
  private final OneOffRequestContext oneOffReqCtx;
  private final String changeId;
  private final ChangeFinder changeFinder;
  private Optional<Long> computedChangeTs = Optional.empty();
  private Optional<ChangeNotes> changeNotes = Optional.empty();

  public interface Factory {
    ChangeChecker create(String changeId);
  }

  @Inject
  public ChangeCheckerImpl(
      GitRepositoryManager gitRepoMgr,
      ChangeFinder changeFinder,
      OneOffRequestContext oneOffReqCtx,
      @Assisted String changeId) {
    this.changeFinder = changeFinder;
    this.gitRepoMgr = gitRepoMgr;
    this.oneOffReqCtx = oneOffReqCtx;
    this.changeId = changeId;
  }

  @Override
  public Optional<IndexEvent> newIndexEvent() throws IOException {
    Optional<Long> changeTs = getComputedChangeTs();
    if (!changeTs.isPresent()) {
      return Optional.empty();
    }

    long ts = changeTs.get();

    IndexEvent event = new IndexEvent();
    event.eventCreatedOn = ts;
    try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName())) {
      event.targetSha = getBranchTargetSha();
      event.metaSha = getMetaSha(repo);
      return Optional.of(event);
    } catch (IOException e) {
      log.atSevere().withCause(e).log(
          "Unable to create index event for project %s", changeNotes.get().getProjectName());
      throw e;
    }
  }

  @Override
  public Optional<ChangeNotes> getChangeNotes() {
    try (ManualRequestContext ctx = oneOffReqCtx.open()) {
      changeNotes = changeFinder.findOne(changeId);
      return changeNotes;
    }
  }

  @Override
  public boolean isChangeUpToDate(Optional<IndexEvent> indexEventOption) throws IOException {
    getComputedChangeTs();
    log.atFine().log("Checking change %s against index event %s", this, indexEventOption);
    if (!computedChangeTs.isPresent()) {
      log.atWarning().log("Unable to compute last updated ts for change %s", changeId);
      return false;
    }
    try {
      if (indexEventOption.isPresent()) {
        try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName())) {
          IndexEvent indexEvent = indexEventOption.get();
          return (computedChangeTs.get() > indexEvent.eventCreatedOn)
              || (computedChangeTs.get() == indexEvent.eventCreatedOn)
                  && (Objects.isNull(indexEvent.targetSha)
                      || repositoryHas(repo, indexEvent.targetSha))
                  && (Objects.isNull(indexEvent.targetSha)
                      || repositoryHas(repo, indexEvent.metaSha));
        }
      }
      return true;

    } catch (IOException ex) {
      log.atWarning().log("Unable to read meta sha for change %s", changeId);
      return false;
    }
  }

  @Override
  public Optional<Long> getComputedChangeTs() {
    if (!computedChangeTs.isPresent()) {
      computedChangeTs = computeLastChangeTs();
    }
    return computedChangeTs;
  }

  @Override
  public String toString() {
    try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName())) {
      return "change-id="
          + changeId
          + "@"
          + getComputedChangeTs().map(IndexEvent::format)
          + "/target:"
          + getBranchTargetSha()
          + "/meta:"
          + getMetaSha(repo);
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Unable to render change %s", changeId);
      return "change-id=" + changeId;
    }
  }

  private String getBranchTargetSha() {
    try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName())) {
      String refName = changeNotes.get().getChange().getDest().branch();
      Ref ref = repo.exactRef(refName);
      if (ref == null) {
        log.atWarning().log("Unable to find target ref %s for change %s", refName, changeId);
        return null;
      }
      return ref.getTarget().getObjectId().getName();
    } catch (IOException e) {
      log.atWarning().withCause(e).log(
          "Unable to resolve target branch SHA for change %s", changeId);
      return null;
    }
  }

  private boolean repositoryHas(Repository repo, String sha1ToCheck) {
    try {
      return repo.parseCommit(ObjectId.fromString(sha1ToCheck)) != null;
    } catch (IOException e) {
      log.atWarning().withCause(e).log(
          "Unable to find SHA1 %s for change %s", sha1ToCheck, changeId);
      return false;
    }
  }

  private Optional<Long> computeLastChangeTs() {
    return getChangeNotes().map(this::getTsFromChange);
  }

  private String getMetaSha(Repository repo) throws IOException {
    String refName = RefNames.changeMetaRef(changeNotes.get().getChange().getId());
    Ref ref = repo.exactRef(refName);
    if (ref == null) {
      throw new IOException(
          String.format("Unable to find meta ref %s for change %s", refName, changeId));
    }
    return ref.getTarget().getObjectId().getName();
  }

  private long getTsFromChange(ChangeNotes notes) {
    Change change = notes.getChange();
    return change.getLastUpdatedOn().toEpochMilli();
  }
}
