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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jgroups.Address;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;
import org.jgroups.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class JGroupsForwarderTest {

  private static final int MAX_TRIES = 3;

  private static final Address A1 = new UUID(1, 1);
  private static final Address A2 = new UUID(2, 2);
  private static final Rsp<Object> RSP_OK = new Rsp<>(true);
  private static final Rsp<Object> RSP_FAIL = new Rsp<>(false);

  private MessageDispatcher dispatcher;
  private JGroupsForwarder forwarder;

  @Before
  public void setUp() throws Exception {
    Gson eventGson = new EventGsonProvider().get();
    Gson gson = new JGroupsForwarderModule().buildJGroupsGson(eventGson);
    Configuration cfg = mock(Configuration.class, RETURNS_DEEP_STUBS);
    when(cfg.jgroups().maxTries()).thenReturn(MAX_TRIES);
    when(cfg.jgroups().retryInterval()).thenReturn(1);

    dispatcher = mock(MessageDispatcher.class, RETURNS_DEEP_STUBS);
    when(dispatcher.getChannel().getView().size()).thenReturn(2);
    when(dispatcher.getChannel().getView().getMembers()).thenReturn(List.of(A1, A2));

    forwarder = new JGroupsForwarder(dispatcher, cfg, gson);
  }

  @Test
  public void castMessageOK_returnsTrue() throws Exception {
    RspList<Object> OK = new RspList<>(Map.of(A1, RSP_OK, A2, RSP_OK));
    when(dispatcher.castMessage(any(), any(), any())).thenReturn(OK);

    CompletableFuture<Boolean> result = forwarder.indexAccount(100, null);
    assertThat(result.get()).isTrue();
    verify(dispatcher, times(1)).castMessage(any(), any(), any());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void castMessageRetriesWithSucess_returnsTrue() throws Exception {
    RspList<Object> OK = new RspList<>(Map.of(A1, RSP_OK, A2, RSP_OK));
    RspList<Object> FAIL = new RspList<>(Map.of(A1, RSP_OK, A2, RSP_FAIL));
    when(dispatcher.castMessage(any(), any(), any())).thenReturn(FAIL, OK);

    CompletableFuture<Boolean> result = forwarder.indexAccount(100, null);
    assertThat(result.get()).isTrue();
    verify(dispatcher, times(2)).castMessage(any(), any(), any());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void castMessageFailsMaxTriesTimes_returnsFalse() throws Exception {
    RspList<Object> FAIL = new RspList<>(Map.of(A1, RSP_FAIL, A2, RSP_FAIL));
    // return FAIL x MAX_TRIES
    when(dispatcher.castMessage(any(), any(), any())).thenReturn(FAIL, FAIL, FAIL);

    CompletableFuture<Boolean> result = forwarder.indexAccount(100, null);
    assertThat(result.get()).isFalse();
    verify(dispatcher, times(MAX_TRIES)).castMessage(any(), any(), any());
  }
}
