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

import static com.google.common.net.MediaType.JSON_UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedEventHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetrics;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.common.net.MediaType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gerrit.server.events.EventTypes;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventRestApiServletTest {
  private static final String ERR_MSG = "some Error";

  @Mock private ForwardedEventHandler forwardedEventHandlerMock;
  @Mock private HttpServletRequest requestMock;
  @Mock private HttpServletResponse responseMock;
  @Mock private ProcessorMetricsRegistry metricsRegistryMock;
  @Mock private ProcessorMetrics metrics;
  private EventRestApiServlet eventRestApiServlet;
  private Gson gson = new EventGsonProvider().get();

  @BeforeClass
  public static void setup() {
    EventTypes.register(RefReplicationDoneEvent.TYPE, RefReplicationDoneEvent.class);
  }

  @Before
  public void createEventsRestApiServlet() throws Exception {
    when(metricsRegistryMock.get(any())).thenReturn(metrics);
    eventRestApiServlet =
        new EventRestApiServlet(forwardedEventHandlerMock, gson, metricsRegistryMock);
    when(requestMock.getContentType()).thenReturn(MediaType.JSON_UTF_8.toString());
  }

  @Test
  public void testDoPostRefReplicationDoneEvent() throws Exception {
    String event =
        "{\"project\":\"gerrit/some-project\",\"ref\":"
            + "\"refs/changes/76/669676/2\",\"nodesCount\":1,\"type\":"
            + "\"ref-replication-done\",\"eventCreatedOn\":1451415011}";
    when(requestMock.getReader()).thenReturn(new BufferedReader(new StringReader(event)));

    eventRestApiServlet.doPost(requestMock, responseMock);

    verify(forwardedEventHandlerMock).dispatch(any(RefReplicationDoneEvent.class));
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void testDoPostDispatcherFailure() throws Exception {
    String event =
        "{\"project\":\"gerrit/some-project\",\"ref\":"
            + "\"refs/changes/76/669676/2\",\"nodesCount\":1,\"type\":"
            + "\"ref-replication-done\",\"eventCreatedOn\":1451415011}";
    when(requestMock.getReader()).thenReturn(new BufferedReader(new StringReader(event)));

    EventDispatcher dispatcher = Mockito.mock(EventDispatcher.class);
    doThrow(new PermissionBackendException(ERR_MSG))
        .when(dispatcher)
        .postEvent(any(RefReplicationDoneEvent.class));
    ForwardedEventHandler forwardedEventHandler = new ForwardedEventHandler(dispatcher);
    eventRestApiServlet = new EventRestApiServlet(forwardedEventHandler, gson, metricsRegistryMock);
    eventRestApiServlet.doPost(requestMock, responseMock);
    verify(responseMock).setStatus(SC_NO_CONTENT);
  }

  @Test
  public void testDoPostBadRequest() throws Exception {
    doThrow(new IOException(ERR_MSG)).when(requestMock).getReader();
    eventRestApiServlet.doPost(requestMock, responseMock);
    verify(responseMock).sendError(SC_BAD_REQUEST, ERR_MSG);
  }

  @Test
  public void testDoPostWrongMediaType() throws Exception {
    when(requestMock.getContentType()).thenReturn(MediaType.APPLICATION_XML_UTF_8.toString());
    eventRestApiServlet.doPost(requestMock, responseMock);
    verify(responseMock)
        .sendError(
            SC_UNSUPPORTED_MEDIA_TYPE, "Expecting " + JSON_UTF_8.toString() + " content type");
  }

  @Test
  public void testDoPostErrorWhileSendingErrorMessage() throws Exception {
    doThrow(new IOException(ERR_MSG)).when(requestMock).getReader();
    doThrow(new IOException("someOtherError"))
        .when(responseMock)
        .sendError(SC_BAD_REQUEST, ERR_MSG);
    eventRestApiServlet.doPost(requestMock, responseMock);
  }

  static class RefReplicationDoneEvent extends RefEvent {
    public static final String TYPE = "ref-replication-done";
    public final String project;
    public final String ref;
    public final int nodesCount;

    public RefReplicationDoneEvent(String project, String ref, int nodesCount) {
      super(TYPE);
      this.project = project;
      this.ref = ref;
      this.nodesCount = nodesCount;
    }

    @Override
    public Project.NameKey getProjectNameKey() {
      return Project.nameKey(project);
    }

    @Override
    public String getRefName() {
      return ref;
    }
  }
}
