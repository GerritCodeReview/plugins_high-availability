// Copyright (C) 2016 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexChangeRestApiServletTest {
  private static final int CHANGE_NUMBER = 1;
  private static final java.util.Optional<LocalDateTime> NO_TS = java.util.Optional.empty();


  @Mock private ForwardedIndexChangeHandler handlerMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;
  @Mock private IndexTs indexTs;

    private Change.Id id;
  private IndexChangeRestApiServlet servlet;

  @Before
  public void setUpMocks() {
    servlet = new IndexChangeRestApiServlet(handlerMock, indexTs);
    id = new Change.Id(CHANGE_NUMBER);
    when(requestMock.getPathInfo()).thenReturn("/index/change/" + CHANGE_NUMBER);
  }

  @Test
  public void changeIsIndexed() throws Exception {
    servlet.doPost(requestMock, responseMock);
    verify(handlerMock, times(1)).index(id, Operation.INDEX);
    verify(responseMock).setStatus(SC_NO_CONTENT);
    verifyIndexTsUpdated();
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    servlet.doDelete(requestMock, responseMock);
    verify(handlerMock, times(1)).index(id, Operation.DELETE);
    verify(responseMock).setStatus(SC_NO_CONTENT);
    verifyDeleteTsUpdated();
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexChange() throws Exception {
    doThrow(new IOException("io-error")).when(handlerMock).index(id, Operation.INDEX);
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, "io-error");
    verifyZeroInteractions(indexTs);
  }

  @Test
  public void indexerThrowsOrmExceptionTryingToIndexChange() throws Exception {
    doThrow(new OrmException("some message")).when(handlerMock).index(id, Operation.INDEX);
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_NOT_FOUND, "Error trying to find change");
    verifyZeroInteractions(indexTs);
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    doThrow(new IOException("io-error")).when(handlerMock).index(id, Operation.INDEX);
    doThrow(new IOException("someError")).when(responseMock).sendError(SC_CONFLICT, "io-error");
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, "io-error");
  }

  private void verifyIndexTsUpdated() {
    verify(indexTs, times(1))
        .update(
            IndexName.CHANGE,
            Operation.INDEX,
            CHANGE_NUMBER,
            java.util.Optional.of(change.getLastUpdatedOn().toLocalDateTime()));
  }

  private void verifyDeleteTsUpdated() {
    verify(indexTs, times(1)).update(IndexName.CHANGE, Operation.DELETE, CHANGE_NUMBER, NO_TS);
  }
}
