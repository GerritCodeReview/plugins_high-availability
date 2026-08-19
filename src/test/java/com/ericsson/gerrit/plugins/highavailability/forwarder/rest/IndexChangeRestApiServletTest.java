// Copyright (C) 2016 The Android Open Source Project
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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ChangeIndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetrics;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
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
  private static final String PROJECT_NAME = "test/project";
  private static final String PROJECT_NAME_URL_ENC = "test%2Fproject";
  private static final String CHANGE_ID = PROJECT_NAME + "~" + CHANGE_NUMBER;
  private static final String IO_ERROR = "io-error";
  private static final Gson GSON = new Gson();

  @Mock private ForwardedIndexChangeHandler handlerMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;
  @Mock private ProcessorMetricsRegistry metricsRegistryMock;
  @Mock private ProcessorMetrics metrics;

  private IndexChangeRestApiServlet servlet;

  @Before
  public void setUpMocks() throws IOException {
    when(metricsRegistryMock.get(any())).thenReturn(metrics);
    servlet = new IndexChangeRestApiServlet(handlerMock, new Gson(), metricsRegistryMock);
    when(requestMock.getRequestURI())
        .thenReturn("http://gerrit.com/index/change/" + PROJECT_NAME_URL_ENC + "~" + CHANGE_NUMBER);
    when(requestMock.getContentType()).thenReturn("application/json");
  }

  private void setRequestBody(ChangeIndexEvent event) throws IOException {
    String json = GSON.toJson(event);
    when(requestMock.getReader()).thenReturn(new BufferedReader(new StringReader(json)));
  }

  @Test
  public void changeIsIndexed() throws Exception {
    setRequestBody(new ChangeIndexEvent(Instant.now(), null, "deadbeef"));
    servlet.doPost(requestMock, responseMock);
    verify(handlerMock, times(1)).index(eq(CHANGE_ID), any(ChangeIndexEvent.class));
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void changeIsDeletedFromIndex() throws Exception {
    when(requestMock.getReader()).thenReturn(new BufferedReader(new StringReader("")));
    servlet.doDelete(requestMock, responseMock);
    verify(handlerMock, times(1)).delete(eq(CHANGE_ID));
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void indexerThrowsIOExceptionTryingToIndexChange() throws Exception {
    setRequestBody(new ChangeIndexEvent(Instant.now(), null, "deadbeef"));
    doThrow(new IOException(IO_ERROR))
        .when(handlerMock)
        .index(eq(CHANGE_ID), any(ChangeIndexEvent.class));
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, IO_ERROR);
  }

  @Test
  public void sendErrorThrowsIOException() throws Exception {
    setRequestBody(new ChangeIndexEvent(Instant.now(), null, "deadbeef"));
    doThrow(new IOException(IO_ERROR))
        .when(handlerMock)
        .index(eq(CHANGE_ID), any(ChangeIndexEvent.class));
    doThrow(new IOException("someError")).when(responseMock).sendError(SC_CONFLICT, IO_ERROR);
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_CONFLICT, IO_ERROR);
  }

  @Test
  public void missingMetaShaReturnsBadRequest() throws Exception {
    when(requestMock.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));
    servlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_BAD_REQUEST, "metaSha is required for change index events");
  }
}
