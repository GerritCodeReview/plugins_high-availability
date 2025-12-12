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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

public enum EventType {
  CACHE_EVICTION,
  EVENT_SENT,
  INDEX_ACCOUNT_UPDATE,
  INDEX_CHANGE_DELETION,
  INDEX_CHANGE_DELETION_ALL_OF_PROJECT,
  INDEX_CHANGE_UPDATE,
  INDEX_CHANGE_UPDATE_BATCH,
  INDEX_GROUP_UPDATE,
  INDEX_PROJECT_UPDATE,
  PROJECT_LIST_ADDITION,
  PROJECT_LIST_DELETION
}
