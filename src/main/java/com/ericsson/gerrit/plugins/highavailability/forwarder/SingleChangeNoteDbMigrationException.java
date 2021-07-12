// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Arrays;

public class SingleChangeNoteDbMigrationException extends Exception {
  private static final long serialVersionUID = 1L;

  public SingleChangeNoteDbMigrationException(
      Change.Id changeId, Project.NameKey project, Throwable t) {
    super(
        String.format(
            "Error in NoteDb migration for change: %s," + " project: %s. %s",
            changeId, project, Arrays.toString(t.getStackTrace())));
  }
}
