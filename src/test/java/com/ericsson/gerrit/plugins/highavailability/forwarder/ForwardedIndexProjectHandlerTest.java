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
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.project.ProjectIndexer;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedIndexProjectHandlerTest {

  @Mock private ProjectIndexer indexerMock;
  private ForwardedIndexProjectHandler handler;
  private Project.NameKey nameKey;

  @Before
  public void setUp() {
    handler = new ForwardedIndexProjectHandler(indexerMock);
    nameKey = Project.nameKey("project/name");
  }

  @Test
  public void testSuccessfulIndexing() throws Exception {
    handler.index(nameKey, Operation.INDEX, Optional.empty()).get(10, SECONDS);
    verify(indexerMock).index(nameKey);
  }

  @Test
  public void deleteIsNotSupported() throws Exception {
    UnsupportedOperationException thrown =
        assertThrows(
            UnsupportedOperationException.class,
            () -> handler.index(nameKey, Operation.DELETE, Optional.empty()).get(10, SECONDS));
    assertThat(thrown).hasMessageThat().contains("Delete from project index not supported");
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
        .index(nameKey);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.index(nameKey, Operation.INDEX, Optional.empty()).get(10, SECONDS);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(nameKey);
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
        .index(nameKey);

    assertThat(Context.isForwardedEvent()).isFalse();
    IOException thrown =
        assertThrows(
            IOException.class,
            () -> handler.index(nameKey, Operation.INDEX, Optional.empty()).get(10, SECONDS));
    assertThat(thrown).hasMessageThat().isEqualTo("someMessage");
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(indexerMock).index(nameKey);
  }
}
