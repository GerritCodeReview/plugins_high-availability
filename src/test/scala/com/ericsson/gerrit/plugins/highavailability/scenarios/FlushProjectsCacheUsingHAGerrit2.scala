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

package com.ericsson.gerrit.plugins.highavailability.scenarios

import com.google.gerrit.scenarios.CacheFlushSimulation
import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class FlushProjectsCacheUsingHAGerrit2 extends CacheFlushSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private val default: ClusterDefault = new ClusterDefault
  private val projectName = className

  override def relativeRuntimeWeight = 2

  override def replaceOverride(in: String): String = {
    replaceProperty("http_port2", default.httpPort2, in)
  }

  private val flushCache: ScenarioBuilder = scenario(uniqueName)
    .feed(data)
    .exec(httpRequest)

  private val createProject = new CreateProjectUsingHAGerrit1(projectName)
  private val getCacheEntriesAfterProject = new GetProjectsCacheEntries(this)
  private val checkCacheEntriesAfterFlush = new CheckProjectsCacheFlushEntriesUsingHAGerrit1(this)
  private val deleteProject = new DeleteProjectUsingHAGerrit(projectName)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    getCacheEntriesAfterProject.test.inject(
      nothingFor(stepWaitTime(getCacheEntriesAfterProject) seconds),
      atOnceUsers(single)
    ),
    flushCache.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ),
    checkCacheEntriesAfterFlush.test.inject(
      nothingFor(stepWaitTime(checkCacheEntriesAfterFlush) seconds),
      atOnceUsers(single)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
