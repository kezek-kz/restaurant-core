package kezek.restaurant.core.aws

import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.joda.time.DateTime

import scala.concurrent.Future

trait S3Client {
  def upload(byteSource: Source[ByteString, Any],
             key: String,
             fileInfo: FileInfo): Future[String]

  def delete(key: String): Future[Unit]
}
