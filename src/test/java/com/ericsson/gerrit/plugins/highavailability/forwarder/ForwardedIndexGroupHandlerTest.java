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

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexGroupHandlerTest {

  @Mock private GroupIndexer indexerMock;
  private ForwardedIndexGroupHandler handler;
  private AccountGroup.UUID uuid;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedIndexGroupHandler(indexerMock);
    uuid = AccountGroup.uuid("123");
  }

  @Test
  public void testSuccessfulIndexing() throws Exception {
    handler.index(uuid, Operation.INDEX, Optional.empty()).get(10, SECONDS);
    verify(indexerMock).index(uuid);
  }

  @Test
  public void deleteIsNotSupported() throws Exception {
    UnsupportedOperationException thrown =
        assertThrows(
            UnsupportedOperationException.class,
            () -> handler.index(uuid, Operation.DELETE, Optional.empty()).get(10, SECONDS));
    assertThat(thrown).hasMessageThat().contains("Delete from group index not supported");
  }

  @Test
  public void shouldSetAndUnsetForwardedContext() throws Exception {
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(indexerMock)
        .index(uuid);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(uuid, Operation.INDEX, Optional.empty()).get(10, SECONDS);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(uuid);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrown() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new IOException("someMessage");
                })
        .when(indexerMock)
        .index(uuid);

    assertThat(Context.isForwardedEvent()).isFalse();
    IOException thrown =
        assertThrows(
            IOException.class,
            () -> handler.index(uuid, Operation.INDEX, Optional.empty()).get(10, SECONDS));
    assertThat(thrown).hasMessageThat().isEqualTo("someMessage");
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(uuid);
  }
}
