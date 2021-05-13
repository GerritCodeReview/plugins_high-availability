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

package plugins.high-availability.src.test.scala.com.ericsson.gerrit.plugins.highavailability.scenarios

import com.google.gerrit.scenarios.GitSimulation

class CreateProjectUsingHAGerritTwice extends GitSimulation {
  private val projectName = className

  private val createProject = new CreateProjectUsingHAGerrit1(projectName)
  private val deleteProject = new DeleteProjectUsingHAGerrit(projectName)
  private val createItAgain = new CreateProjectUsingHAGerrit1(projectName)
  private val verifyProject = new CloneUsingHAGerrit2(projectName)
  private val deleteItAfter = new DeleteProjectUsingHAGerrit(projectName)

  setUp(
    createProject.test.inject(
      nothingFor(stepWaitTime(createProject) seconds),
      atOnceUsers(single)
    ),
    deleteProject.test.inject(
      nothingFor(stepWaitTime(deleteProject) seconds),
      atOnceUsers(single)
    ),
    createItAgain.test.inject(
      nothingFor(stepWaitTime(createItAgain) seconds),
      atOnceUsers(single)
    ),
    verifyProject.test.inject(
      nothingFor(stepWaitTime(verifyProject) seconds),
      atOnceUsers(single)
    ).protocols(gitProtocol),
    deleteItAfter.test.inject(
      nothingFor(stepWaitTime(deleteItAfter) seconds),
      atOnceUsers(single)
    ),
  ).protocols(httpProtocol)
}
