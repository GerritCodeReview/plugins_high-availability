// Copyright (C) 2015 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.index.ChangeIndexedEvent;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class ChangeCheckerImpl implements ChangeChecker {
  private final ChangeNotes changeNotes;
  private final GitRepositoryManager gitRepoMgr;
  private final CommentsUtil commentsUtil;
  private SchemaFactory<ReviewDb> schemaFactory;
  private long computedChangeTs;

  public interface Factory {
    public ChangeChecker create(ChangeNotes changeNotes);
  }

  @Inject
  public ChangeCheckerImpl(
      GitRepositoryManager gitRepoMgr,
      CommentsUtil commentsUtil,
      SchemaFactory<ReviewDb> schemaFactory,
      @Assisted ChangeNotes changeNotes) {
    this.changeNotes = changeNotes;
    this.gitRepoMgr = gitRepoMgr;
    this.commentsUtil = commentsUtil;
    this.schemaFactory = schemaFactory;
  }

  @Override
  public boolean isChangeUpToDate(
      Optional<ChangeIndexedEvent> indexEvent, int changeTsGraceInterval)
      throws IOException, OrmException {
    computeLastChangeTs();
    return indexEvent
        .map(e -> computedChangeTs >= (e.eventCreatedOn - changeTsGraceInterval))
        .orElse(true);
  }

  @Override
  public long getComputedChangeTs() throws IOException, OrmException {
    if (computedChangeTs == 0L) {
      computeLastChangeTs();
    }
    return computedChangeTs;
  }

  private void computeLastChangeTs() throws IOException, OrmException {
    try (ReviewDb db = schemaFactory.open()) {
      Change change = changeNotes.getChange();
      Timestamp changeTs = change.getLastUpdatedOn();
      List<Comment> comments = null;
      try {
        comments = commentsUtil.draftByChange(db, changeNotes);
      } catch (OrmException e) {
      }
      if (comments != null) {
        for (Comment comment : comments) {
          Timestamp commentTs = comment.writtenOn;
          changeTs = commentTs.after(changeTs) ? commentTs : changeTs;
        }
      }
      computedChangeTs =
          Math.max(changeTs.getTime() / 1000, getTargetBranchCommitTimeEpoch(change));
    }
  }

  private long getTargetBranchCommitTimeEpoch(Change change) throws IOException {
    try (Repository repo = gitRepoMgr.openRepository(change.getProject())) {
      try (RevWalk revWalk = new RevWalk(repo)) {
        return revWalk.parseCommit(repo.resolve(change.getDest().get())).getCommitTime();
      }
    }
  }
}
