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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class ChangeCheckerImpl implements ChangeChecker {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final GitRepositoryManager gitRepoMgr;
  private final CommentsUtil commentsUtil;
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
      CommentsUtil commentsUtil,
      ChangeFinder changeFinder,
      OneOffRequestContext oneOffReqCtx,
      @Assisted String changeId) {
    this.changeFinder = changeFinder;
    this.gitRepoMgr = gitRepoMgr;
    this.commentsUtil = commentsUtil;
    this.oneOffReqCtx = oneOffReqCtx;
    this.changeId = changeId;
  }

  @Override
  public Optional<IndexEvent> newIndexEvent() throws OrmException {
    return getComputedChangeTs()
        .map(
            ts -> {
              IndexEvent event = new IndexEvent();
              event.eventCreatedOn = ts;
              event.targetSha = getBranchTargetSha();
              return event;
            });
  }

  @Override
  public Optional<ChangeNotes> getChangeNotes() throws OrmException {
    try (ManualRequestContext ctx = oneOffReqCtx.open()) {
      changeNotes = Optional.ofNullable(changeFinder.findOne(changeId));
      return changeNotes;
    }
  }

  @Override
  public boolean isChangeUpToDate(Optional<IndexEvent> indexEvent) throws OrmException {
    getComputedChangeTs();
    log.atFine().log("Checking change %s against index event %s", this, indexEvent);
    if (!computedChangeTs.isPresent()) {
      log.atWarning().log("Unable to compute last updated ts for change %s", changeId);
      return false;
    }

    if (indexEvent.isPresent() && indexEvent.get().targetSha == null) {
      return indexEvent.map(e -> (computedChangeTs.get() >= e.eventCreatedOn)).orElse(true);
    }

    return indexEvent
        .map(
            e ->
                (computedChangeTs.get() > e.eventCreatedOn)
                    || (computedChangeTs.get() == e.eventCreatedOn)
                        && (Objects.equals(getBranchTargetSha(), e.targetSha)))
        .orElse(true);
  }

  @Override
  public Optional<Long> getComputedChangeTs() throws OrmException {
    if (!computedChangeTs.isPresent()) {
      computedChangeTs = computeLastChangeTs();
    }
    return computedChangeTs;
  }

  @Override
  public String toString() {
    try {
      return "change-id="
          + changeId
          + "@"
          + getComputedChangeTs().map(IndexEvent::format)
          + "/"
          + getBranchTargetSha();
    } catch (OrmException e) {
      log.atSevere().withCause(e).log("Unable to render change %s", changeId);
      return "change-id=" + changeId;
    }
  }

  private String getBranchTargetSha() {
    try (Repository repo = gitRepoMgr.openRepository(changeNotes.get().getProjectName())) {
      String refName = changeNotes.get().getChange().getDest().get();
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

  private Optional<Long> computeLastChangeTs() throws OrmException {
    return getChangeNotes().map(this::getTsFromChangeAndDraftComments);
  }

  private long getTsFromChangeAndDraftComments(ChangeNotes notes) {
    Change change = notes.getChange();
    Timestamp changeTs = change.getLastUpdatedOn();
    try {
      for (Comment comment : commentsUtil.draftByChange(changeNotes.get())) {
        Timestamp commentTs = comment.writtenOn;
        changeTs = commentTs.after(changeTs) ? commentTs : changeTs;
      }
    } catch (OrmException e) {
      log.atWarning().withCause(e).log("Unable to access draft comments for change %s", change);
    }
    return changeTs.getTime() / 1000;
  }
}
