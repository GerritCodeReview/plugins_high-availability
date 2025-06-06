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

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.CacheKeyJsonParser;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

public class CommandDeserializerTest {

  private Gson gson;
  private CacheKeyJsonParser cacheKeyParser;

  @Before
  public void setUp() {
    Gson eventGson = new EventGsonProvider().get();
    this.gson = new JGroupsForwarderModule().buildJGroupsGson(eventGson);
    this.cacheKeyParser = new CacheKeyJsonParser(eventGson);
  }

  @Test
  public void indexAccount() {
    Command cmd = gson.fromJson("{type: 'index-account', id: 100}", Command.class);
    assertThat(cmd).isInstanceOf(IndexAccount.class);
    IndexAccount index = (IndexAccount) cmd;
    assertThat(index.getId()).isEqualTo(100);
  }

  @Test
  public void updateChangeCommand() {
    Command cmd =
        gson.fromJson("{type: 'update-change', projectName: 'foo', id: 100}", Command.class);
    assertThat(cmd).isInstanceOf(IndexChange.Update.class);
    IndexChange.Update update = (IndexChange.Update) cmd;
    assertThat(update.getId()).isEqualTo("foo~100");
    assertThat(update.isBatch()).isFalse();
  }

  @Test
  public void batchUpdateChangeCommand() {
    Command cmd =
        gson.fromJson(
            "{type: 'update-change', projectName: 'foo', id: 100, batchMode: 'true'}",
            Command.class);
    assertThat(cmd).isInstanceOf(IndexChange.Update.class);
    IndexChange.Update update = (IndexChange.Update) cmd;
    assertThat(update.getId()).isEqualTo("foo~100");
    assertThat(update.isBatch()).isTrue();
  }

  @Test
  public void deleteChangeCommand() {
    Command cmd = gson.fromJson("{type: 'delete-change', id: 100}", Command.class);
    assertThat(cmd).isInstanceOf(IndexChange.Delete.class);
    IndexChange.Delete delete = (IndexChange.Delete) cmd;
    assertThat(delete.getId()).isEqualTo("~100");

    cmd = gson.fromJson("{type: 'delete-change', projectName: 'foo', id: 100}", Command.class);
    assertThat(cmd).isInstanceOf(IndexChange.Delete.class);
    delete = (IndexChange.Delete) cmd;
    assertThat(delete.getId()).isEqualTo("foo~100");
  }

  @Test
  public void indexGroup() {
    Command cmd = gson.fromJson("{type: 'index-group', uuid: 'foo'}", Command.class);
    assertThat(cmd).isInstanceOf(IndexGroup.class);
    IndexGroup index = (IndexGroup) cmd;
    assertThat(index.getUuid()).isEqualTo("foo");
  }

  @Test
  public void indexProject() {
    Command cmd = gson.fromJson("{type: 'index-project', projectName: 'foo'}", Command.class);
    assertThat(cmd).isInstanceOf(IndexProject.class);
    IndexProject index = (IndexProject) cmd;
    assertThat(index.getProjectName()).isEqualTo("foo");
  }

  @Test
  public void postEvent() {
    Command cmd =
        gson.fromJson(
            "{event: {projectName : 'foo', headName : 'refs/heads/master', type :"
                + " 'project-created', eventCreatedOn:1505898779}, type : 'post-event'}",
            Command.class);
    assertThat(cmd).isInstanceOf(PostEvent.class);
    Event e = ((PostEvent) cmd).getEvent();
    assertThat(e).isInstanceOf(ProjectCreatedEvent.class);
    assertThat(((ProjectCreatedEvent) e).projectName).isEqualTo("foo");
  }

  @Test
  public void evictCache() {
    Project.NameKey nameKey = Project.nameKey("foo");
    String keyJson = gson.toJson(nameKey);
    Command cmd =
        gson.fromJson(
            String.format(
                "{type: '%s', cacheName: '%s', keyJson: '%s'}",
                EvictCache.TYPE, Constants.PROJECTS, keyJson),
            EvictCache.class);
    assertThat(cmd).isInstanceOf(EvictCache.class);
    EvictCache evict = (EvictCache) cmd;

    Object cacheKey = cacheKeyParser.fromJson(Constants.PROJECTS, evict.getKeyJson());
    assertThat(cacheKey).isInstanceOf(Project.NameKey.class);
    assertThat(cacheKey).isEqualTo(nameKey);
  }

  @Test
  public void addToProjectList() {
    Command cmd = gson.fromJson("{type: 'add-to-project-list', projectName: 'foo'}", Command.class);
    assertThat(cmd).isInstanceOf(AddToProjectList.class);
    AddToProjectList addToProjectList = (AddToProjectList) cmd;
    assertThat(addToProjectList.getProjectName()).isEqualTo("foo");
  }

  @Test
  public void removeFromProjectList() {
    Command cmd =
        gson.fromJson("{type: 'remove-from-project-list', projectName: 'foo'}", Command.class);
    assertThat(cmd).isInstanceOf(RemoveFromProjectList.class);
    RemoveFromProjectList removeFromProjectList = (RemoveFromProjectList) cmd;
    assertThat(removeFromProjectList.getProjectName()).isEqualTo("foo");
  }
}
