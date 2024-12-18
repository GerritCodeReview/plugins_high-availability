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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class ForwardedProjectListUpdateHandlerTest {

  private static final String PROJECT_NAME = "someProject";
  private static final String SOME_MESSAGE = "someMessage";
  private static final Project.NameKey PROJECT_KEY = Project.nameKey(PROJECT_NAME);
  @Mock private ProjectCache projectCacheMock;
  @Mock private GitRepositoryManager repoMgrMock;
  private ForwardedProjectListUpdateHandler handler;

  @Before
  public void setUp() throws Exception {
    handler = new ForwardedProjectListUpdateHandler(projectCacheMock, repoMgrMock);
  }

  @Test
  public void testSuccessfulAdd() throws Exception {
    handler.update(PROJECT_NAME, false);
    verify(projectCacheMock).onCreateProject(PROJECT_KEY);
  }

  @Test
  public void testSuccessfulRemove() throws Exception {
    handler.update(PROJECT_NAME, true);
    verify(projectCacheMock).remove(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextOnAdd() throws Exception {
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(projectCacheMock)
        .onCreateProject(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.update(PROJECT_NAME, false);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).onCreateProject(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextOnRemove() throws Exception {
    // this doAnswer is to allow to assert that context is set to forwarded
    // while cache eviction is called.
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  return null;
                })
        .when(projectCacheMock)
        .remove(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    handler.update(PROJECT_NAME, true);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).remove(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrownOnAdd() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new RuntimeException(SOME_MESSAGE);
                })
        .when(projectCacheMock)
        .onCreateProject(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> handler.update(PROJECT_NAME, false));
    assertThat(thrown).hasMessageThat().isEqualTo(SOME_MESSAGE);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).onCreateProject(PROJECT_KEY);
  }

  @Test
  public void shouldSetAndUnsetForwardedContextEvenIfExceptionIsThrownOnRemove() throws Exception {
    doAnswer(
            (Answer<Void>)
                invocation -> {
                  assertThat(Context.isForwardedEvent()).isTrue();
                  throw new RuntimeException(SOME_MESSAGE);
                })
        .when(projectCacheMock)
        .remove(PROJECT_KEY);

    assertThat(Context.isForwardedEvent()).isFalse();
    RuntimeException thrown =
        assertThrows(RuntimeException.class, () -> handler.update(PROJECT_NAME, true));
    assertThat(thrown).hasMessageThat().isEqualTo(SOME_MESSAGE);
    assertThat(Context.isForwardedEvent()).isFalse();

    verify(projectCacheMock).remove(PROJECT_KEY);
  }
}
