// Copyright (C) 2017 Ericsson
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

import com.google.gerrit.httpd.plugins.HttpPluginModule;

public class RestForwarderServletModule extends HttpPluginModule {
  @Override
  protected void configureServlets() {
    serveRegex("/index/account/\\d+$").with(IndexAccountRestApiServlet.class);
    serveRegex("/index/change/\\d+$").with(IndexChangeRestApiServlet.class);
    serveRegex("/index/group/\\w+$").with(IndexGroupRestApiServlet.class);
    serve("/event").with(EventRestApiServlet.class);
    serve("/cache/project_list/*").with(ProjectListApiServlet.class);
    serve("/cache/*").with(CacheRestApiServlet.class);
  }
}
