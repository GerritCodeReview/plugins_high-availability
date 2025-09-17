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

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;

public class AddToProjectList extends Command {
  static final EventType TYPE = EventType.ADD_TO_PROJECT_LIST;

  private final String projectName;

  public AddToProjectList(String projectName, long eventCreatedOn) {
    super(TYPE, eventCreatedOn);
    this.projectName = projectName;
  }

  public String getProjectName() {
    return projectName;
  }
}
