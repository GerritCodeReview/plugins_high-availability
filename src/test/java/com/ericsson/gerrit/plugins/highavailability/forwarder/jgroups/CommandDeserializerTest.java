// Copyright (C) 2017 The Android Open Source Project
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

import static com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups.MessageProcessor.gson;
import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.ProjectCreatedEvent;
import org.junit.Test;

public class CommandDeserializerTest {

  @Test
  public void updateChangeCommand() {
    Command cmd = gson.fromJson("{type : 'update-change', id: 100}", Command.class);
    assertThat(cmd).isInstanceOf(IndexChange.Update.class);
    IndexChange.Update update = (IndexChange.Update) cmd;
    assertThat(update.getId()).isEqualTo(100);
  }

  @Test
  public void deleteChangeCommand() {
    Command cmd = gson.fromJson("{type : 'delete-change', id: 100}", Command.class);
    assertThat(cmd).isInstanceOf(IndexChange.Delete.class);
    IndexChange.Delete update = (IndexChange.Delete) cmd;
    assertThat(update.getId()).isEqualTo(100);
  }

  @Test
  public void indexAccount() {
    Command cmd = gson.fromJson("{type : 'index-account', id: 100}", Command.class);
    assertThat(cmd).isInstanceOf(IndexAccount.class);
    IndexAccount index = (IndexAccount) cmd;
    assertThat(index.getId()).isEqualTo(100);
  }

  @Test
  public void evictCache() {
    Command cmd = gson.fromJson("{type : 'evict-cache', key: '100'}", Command.class);
    assertThat(cmd).isInstanceOf(EvictCache.class);
    EvictCache evict = (EvictCache) cmd;
    System.out.println(evict.getKey().getClass());
    assertThat(evict.getKey()).isEqualTo("100");
  }

  @Test
  public void postEvent() {
    Command cmd =
        gson.fromJson(
            "{event: {projectName : 'foo', headName : 'refs/heads/master', type : 'project-created', eventCreatedOn:1505898779}, type : 'post-event'}",
            Command.class);
    assertThat(cmd).isInstanceOf(PostEvent.class);
    Event e = ((PostEvent) cmd).getEvent();
    assertThat(e).isInstanceOf(ProjectCreatedEvent.class);
    assertThat(((ProjectCreatedEvent) e).projectName).isEqualTo("foo");
  }
}
