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

import com.google.gerrit.scenarios.GitSimulation
import io.gatling.core.Predef.{atOnceUsers, _}
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder

import scala.concurrent.duration._

class CloneUsingHAGerrit2 extends GitSimulation {
  private val data: FeederBuilder = jsonFile(resource).convert(keys).queue
  private var projectName = className

  def this(projectName: String) {
    this()
    this.projectName = projectName
  }

  override def replaceOverride(in: String): String = {
    val next = replaceProperty("http_port2", 8082, in)
    replaceKeyWith("_project", projectName, next)
  }

  val test: ScenarioBuilder = scenario(uniqueName)
    .feed(data)
    .exec(gitRequest)

  private val createProject = new CreateProjectUsingHAGerrit1(projectName)
  private val deleteProject = new DeleteProjectUsingHAGerrit(projectName)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    test.inject(
      nothingFor(stepWaitTime(this) seconds),
      atOnceUsers(single)
    ).protocols(gitProtocol),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}