// Copyright (C) 2020 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.event;

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.GsonProvider;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gson.Gson;
import org.junit.Test;

public class EventDeserializerTest {
  private static final String PATCH_SET_CREATED_EVENT_LEGACY_PROJECT_KEY =
      "{\n"
          + "  \"uploader\": {\n"
          + "    \"name\": \"uploader\",\n"
          + "    \"email\": \"uploader@somewhere.com\",\n"
          + "    \"username\": \"uploader\"\n"
          + "  },\n"
          + "  \"change\": {\n"
          + "    \"project\": \"myproject\",\n"
          + "    \"branch\": \"mybranch\",\n"
          + "    \"id\": \"Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef\",\n"
          + "    \"number\": 1000,\n"
          + "    \"url\": \"http://somewhere.com\",\n"
          + "    \"commitMessage\": \"This is a test commit message\",\n"
          + "    \"createdOn\": 1254344400,\n"
          + "    \"status\": \"NEW\"\n"
          + "  },\n"
          + "  \"project\": {\n"
          + "    \"name\": \"myproject\"\n"
          + "  },\n"
          + "  \"refName\": \"refs/heads/mybranch\",\n"
          + "  \"changeKey\": {\n"
          + "    \"id\": \"Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef\"\n"
          + "  },\n"
          + "  \"type\": \"patchset-created\",\n"
          + "  \"eventCreatedOn\": 1254344401\n"
          + "}";

  private static final String PATCH_SET_CREATED_EVENT_NEW_PROJECT_KEY =
      "{\n"
          + "   \"uploader\":{\n"
          + "      \"name\":\"uploader\",\n"
          + "      \"email\":\"uploader@somewhere.com\",\n"
          + "      \"username\":\"uploader\"\n"
          + "   },\n"
          + "   \"change\":{\n"
          + "      \"project\":\"myproject\",\n"
          + "      \"branch\":\"mybranch\",\n"
          + "      \"id\":\"Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef\",\n"
          + "      \"number\":1000,\n"
          + "      \"url\":\"http://somewhere.com\",\n"
          + "      \"commitMessage\":\"This is a test commit message\",\n"
          + "      \"createdOn\":1254344400,\n"
          + "      \"status\":\"NEW\"\n"
          + "   },\n"
          + "   \"project\":\"myproject\",\n"
          + "   \"refName\":\"refs/heads/mybranch\",\n"
          + "   \"changeKey\":{\n"
          + "      \"id\":\"Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef\"\n"
          + "   },\n"
          + "   \"type\":\"patchset-created\",\n"
          + "   \"eventCreatedOn\":1254344401\n"
          + "}";

  private final Gson gson = new GsonProvider().get();

  @Test
  public void deserializePatchSetCreatedEventLegacyProjectKey() {
    PatchSetCreatedEvent e =
        gson.fromJson(PATCH_SET_CREATED_EVENT_LEGACY_PROJECT_KEY, PatchSetCreatedEvent.class);
    assertThat(e.getProjectNameKey().get()).isEqualTo("myproject");
  }

  @Test
  public void deserializePatchSetCreatedEventNewProjectKey() {
    PatchSetCreatedEvent e =
        gson.fromJson(PATCH_SET_CREATED_EVENT_NEW_PROJECT_KEY, PatchSetCreatedEvent.class);
    assertThat(e.getProjectNameKey().get()).isEqualTo("myproject");
  }
}
