/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload.controllers.constraints

import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.{EnvelopeFilesConstraints, EnvelopeConstraintsUserSetting}
import uk.gov.hmrc.fileupload.infrastructure.EnvelopeConstraintsConfiguration
import uk.gov.hmrc.fileupload.write.envelope._

class EnvelopeFilesConstraintsRequestSpec extends EventBasedGWTSpec[EnvelopeCommand, Envelope] with ApplicationComponents {

  override val handler = new EnvelopeHandler(envelopeConstraintsConfigure)

  override val defaultStatus: Envelope = Envelope()

  val fakeDateTime = new DateTime(0)
  val fakeUrl = "http://www.callback-url.com"
  val fakeData: JsObject = Json.obj("foo" -> "bar")

  val envelopeId = EnvelopeId("envelopeId-1")
  val fileId = FileId("fileId-1")
  val fileRefId = FileRefId("fileRefId-1")

  val envelopeCreatedByDefaultStatus = EnvelopeCreated(envelopeId, Some(fakeUrl), Some(fakeDateTime),
    Some(fakeData), Some(defaultConstraints))

  val envelopeCreatedByMaxSizePerFile = EnvelopeCreated(envelopeId, Some(fakeUrl), Some(fakeDateTime),
    Some(fakeData), Some(EnvelopeFilesConstraints(defaultMaxItems, acceptedMaxSize, acceptedMaxSizePerItem)))

  val envelopeCreatedByMaxSizeEnvelope = EnvelopeCreated(envelopeId, Some(fakeUrl), Some(fakeDateTime),
    Some(fakeData), Some(EnvelopeFilesConstraints(defaultMaxItems, acceptedMaxSize, defaultMaxSizePerItem)))

  val createEnvelopeRequestWithoutMaxNoFilesConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(None, Some("25MB"),
      Some("10MB")), envelopeConstraintsConfigure).right.get)
  }

  val createEnvelopeRequestWithoutMaxSizeConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(Some(100), None,
      Some("10MB")), envelopeConstraintsConfigure).right.get)
  }

  val createEnvelopeRequestWithoutMaxSizePerItemConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(Some(100), Some("25MB"),
      None), envelopeConstraintsConfigure).right.get)
  }

  val createEnvelopeRequestWithoutTypeConstraints: Option[EnvelopeFilesConstraints] = {
    Some(EnvelopeConstraintsConfiguration.validateEnvelopeFilesConstraints(EnvelopeConstraintsUserSetting(Some(100), Some("25MB"),
      Some("10MB"), None), envelopeConstraintsConfigure).right.get)
  }

  feature("CreateEnvelope with constraints") {

    scenario("Create new envelope with out set max. no. of files constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutMaxNoFilesConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    scenario("Create new envelope with out the constraint for Max size per envelope") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutMaxSizeConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    scenario("Create new envelope with out the constraint for Max size per item") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutMaxSizePerItemConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    scenario("Create new envelope with out the constraint for content type") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          createEnvelopeRequestWithoutTypeConstraints),
        envelopeCreatedByDefaultStatus
      )
    }

    scenario("Create new envelope with number of items exceeding limit") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(EnvelopeFilesConstraints(101, defaultMaxSize, defaultMaxSizePerItem))),
        InvalidMaxItemCountConstraintError
      )
    }

    scenario("Create new envelope with number of items < 1") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(EnvelopeFilesConstraints(0, defaultMaxSize, defaultMaxSizePerItem))),
        InvalidMaxItemCountConstraintError
      )
    }

    scenario("Create new envelope not over bounds max size per item constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(EnvelopeFilesConstraints(defaultMaxItems, acceptedMaxSize, acceptedMaxSizePerItem))),
        envelopeCreatedByMaxSizePerFile
      )
    }

    scenario("Create new envelope not over bounds max size constraint") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(EnvelopeFilesConstraints(defaultMaxItems, acceptedMaxSize, defaultMaxSizePerItem))),
        envelopeCreatedByMaxSizeEnvelope
      )
    }

    scenario("Create new envelope with out valid content type") {
      givenWhenThen(
        --,
        CreateEnvelope(envelopeId, Some(fakeUrl), Some(fakeDateTime), Some(fakeData),
          Some(EnvelopeFilesConstraints(defaultMaxItems, defaultMaxSize, defaultMaxSizePerItem))),
        envelopeCreatedByDefaultStatus
      )
    }

  }
}