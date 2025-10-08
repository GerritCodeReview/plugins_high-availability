// Copyright (C) 2025 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.commands;

import com.google.gerrit.entities.Project;

public class DeleteAllProjectChangesFromIndex extends Command {
  static final String TYPE = "delete-all-project-changes-from-index";

  private final Project.NameKey projectName;

  public DeleteAllProjectChangesFromIndex(Project.NameKey projectName) {
    super(TYPE);
    this.projectName = projectName;
  }

  public String getProjectName() {
    return projectName.get();
  }
}
