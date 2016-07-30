/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.controllers

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.Status
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject, JsString, JsValue}
import play.api.mvc._
import play.api.test.{FakeHeaders, FakeRequest}
import reactivemongo.json.JSONSerializationPack
import reactivemongo.json.JSONSerializationPack.Document
import uk.gov.hmrc.fileupload.envelope.Envelope
import uk.gov.hmrc.fileupload.envelope.Service._
import uk.gov.hmrc.fileupload.file.Service._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.{ExecutionContext, Future}

class FileControllerSpec extends UnitSpec with WithFakeApplication with ScalaFutures {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  implicit val ec = ExecutionContext.global

  val failed = Future.failed(new Exception("not good"))

  case class TestJsonReadFile(id: JsValue = JsString("testid")) extends JSONReadFile {
    val pack = JSONSerializationPack
    val contentType: Option[String] = None
    val filename: Option[String] = None
    val chunkSize: Int = 0
    val length: Long = 0
    val uploadDate: Option[Long] = None
    val md5: Option[String] = None
    val metadata: Document = null
  }

  def parse = UploadParser.parse(null) _

  def newController(uploadBodyParser: (EnvelopeId, FileId) => BodyParser[Future[JSONReadFile]] = parse,
                    getMetadata: (EnvelopeId, FileId) => Future[GetMetadataResult] = (_,_) => failed,
                    retrieveFile: (EnvelopeId, FileId) => Future[GetFileResult] = (_,_) => failed,
                    getEnvelope: EnvelopeId => Future[Option[Envelope]] = _ => failed,
                    uploadFile: UploadedFileInfo => Future[UpsertFileToEnvelopeResult] = _ => failed,
                    updateMetadata: (EnvelopeId, FileId, Option[String], Option[String], Option[JsObject]) => Future[UpdateMetadataResult] = (_,_,_,_,_) => failed) =
    new FileController(uploadBodyParser = uploadBodyParser,
      getMetadata = getMetadata,
      retrieveFile = retrieveFile,
      getEnvelope = getEnvelope,
      uploadFile = uploadFile,
      updateMetadata = updateMetadata)

  "Upload a file" should {
    "return 200 after the file is added to the envelope" in {
      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelope", FakeHeaders(), body = Future.successful(TestJsonReadFile()))

      val envelope = Support.envelope

      val controller = newController(uploadFile = _ => Future.successful(Xor.right(UpsertFileSuccess)))
      val result = controller.upsertFile(envelope._id, FileId())(fakeRequest).futureValue

      result.header.status shouldBe Status.OK
    }

    "return 404 if envelope does not exist" in {
      val fakeRequest = new FakeRequest[Future[JSONReadFile]]("PUT", "/envelope", FakeHeaders(), body = Future.successful(TestJsonReadFile()))

      val envelope = Support.envelope

      val controller = newController(
       uploadFile = _ => Future.successful(Xor.left(UpsertFileEnvelopeNotFoundError))
      )
      val result: Result = controller.upsertFile(envelope._id, FileId())(fakeRequest).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }
  }

  "Download a file" should {
    "return 200 when file is found" in {
      val fileId = FileId("fileId")
      val envelope = Support.envelopeWithAFile(fileId)
      val envelopeId = envelope._id
      val fakeRequest = new FakeRequest[AnyContentAsJson]("GET", s"/envelope/$envelopeId/file/$fileId/content", FakeHeaders(), body = null)
      val fileFound: GetFileResult = Xor.Right(FileFoundResult(Some("myfile.txt"), 100, Enumerator.eof[ByteStream]))
      val controller = newController(retrieveFile = (_,_) => Future.successful(fileFound))

      val result = controller.downloadFile(envelopeId, fileId.value)(fakeRequest).futureValue

      result.header.status shouldBe Status.OK
      val headers = result.header.headers
      headers("Content-Length") shouldBe "100"
      headers("Content-Type") shouldBe "application/octet-stream"
      headers("Content-Disposition") shouldBe "attachment; filename=\"myfile.txt\""
    }

    "respond with 404 when a file is not found" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId("myFileId")
      val fakeRequest = new FakeRequest[AnyContentAsJson]("GET", s"/envelope/$envelopeId/file/$fileId/content", FakeHeaders(), body = null)

      val fileFound: GetFileResult = Xor.Left(GetFileNotFoundError)
      val controller = newController(
        retrieveFile = (_,_) => Future.successful(fileFound)
      )

      val result: Result = controller.downloadFile(envelopeId, fileId.value)(fakeRequest).futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }
  }
}
