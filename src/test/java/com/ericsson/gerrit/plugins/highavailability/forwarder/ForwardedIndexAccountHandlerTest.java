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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.index.account.AccountIndexer;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexAccountHandlerTest {

  @Rule public ExpectedException exception = ExpectedException.none();
  @Mock private AccountIndexer indexerMock;
  private ForwardedIndexAccountHandler handler;
  private Account.Id id;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedIndexAccountHandler(indexerMock);
    id = new Account.Id(123);
  }

  @Test
  public void testSuccessfulIndexing() throws Exception {
    handler.index(id, Operation.INDEX);
    verify(indexerMock).index(id);
  }

  @Test
  public void deleteIsNotSupported() throws Exception {
    exception.expect(UnsupportedOperationException.class);
    exception.expectMessage("Delete from account index not supported");
    handler.index(id, Operation.DELETE);
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
        .index(id);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(id, Operation.INDEX);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(id);
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
        .index(id);

    assertThat(Context.isForwardedEvent()).isFalse();
    try {
      handler.index(id, Operation.INDEX);
      fail("should have thrown an IOException");
    } catch (IOException e) {
      assertThat(e.getMessage()).isEqualTo("someMessage");
    }
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(id);
  }
}
