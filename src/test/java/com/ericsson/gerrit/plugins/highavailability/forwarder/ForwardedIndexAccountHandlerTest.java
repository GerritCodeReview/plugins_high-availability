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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexAccountHandlerTest {

  @Mock private AccountIndexer indexerMock;
  private ForwardedIndexAccountHandler handler;
  private Account.Id id;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedIndexAccountHandler(indexerMock);
    id = Account.id(123);
  }

  @Test
  public void testSuccessfulIndexing() throws Exception {
    handler.index(id).get(10, SECONDS);
    verify(indexerMock).index(id);
  }

  @Test
  public void forwardedEventFlagIsSetOnExecutorThreadDuringIndex() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(indexerMock)
        .index(id);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(id).get(10, SECONDS);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(id);
  }

  @Test
  public void forwardedEventFlagIsUnsetAfterIndexingException() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new IOException("someMessage");
                })
        .when(indexerMock)
        .index(id);

    assertThat(Context.isForwardedEvent()).isFalse();
    IOException thrown = assertThrows(IOException.class, () -> handler.index(id).get(10, SECONDS));
    assertThat(thrown).hasMessageThat().isEqualTo("someMessage");
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(id);
  }

  @Test
  public void inFlightGuardPreventsAndThenAllowsReindex() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThrows(InFlightIndexedException.class, () -> handler.index(id));
                  return null;
                })
        .when(indexerMock)
        .index(id);

    handler.index(id).get(10, SECONDS);

    handler.index(id).get(10, SECONDS);
    verify(indexerMock, org.mockito.Mockito.times(2)).index(id);
  }
}
