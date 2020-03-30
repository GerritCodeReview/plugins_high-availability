// Copyright (C) 2020 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.replication.events.Deserializer;
import com.google.common.io.CharStreams;
import com.google.common.net.MediaType;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class ReplicationRestApiServlet extends AbstractRestApiServlet {
  private static final Logger log = LoggerFactory.getLogger(ReplicationRestApiServlet.class);
  private static final long serialVersionUID = -1L;

  @Inject
  ReplicationRestApiServlet() {}

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    setHeaders(rsp);
    try {
      if (!MediaType.parse(req.getContentType()).is(JSON_UTF_8)) {
        sendError(rsp, SC_UNSUPPORTED_MEDIA_TYPE, "Expecting " + JSON_UTF_8 + " content type");
        return;
      }

      ProjectEvent receivedEvent = getEventFromRequest(req);
      log.info(
          "Received project event "
              + receivedEvent.getClass().getSimpleName()
              + " for project "
              + receivedEvent.getProjectName());
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      log.error("Unable decode ProjectEvent", e);
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    }
  }

  private static ProjectEvent getEventFromRequest(HttpServletRequest req) throws IOException {
    String jsonEvent = CharStreams.toString(req.getReader());
    String requestURI = req.getRequestURI();
    String className = Url.decode(requestURI.substring(requestURI.lastIndexOf('/') + 1));
    Gson gson =
        new GsonBuilder()
            .registerTypeAdapter(ProjectEvent.class, new Deserializer(className))
            .create();
    return gson.fromJson(jsonEvent, ProjectEvent.class);
  }
}
