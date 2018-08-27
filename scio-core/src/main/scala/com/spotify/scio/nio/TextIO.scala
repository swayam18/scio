/*
 * Copyright 2016 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.scio.nio

import java.io.{BufferedInputStream, InputStream, SequenceInputStream}
import java.nio.channels.Channels
import java.util.Collections

import com.google.api.client.util.Charsets
import com.spotify.scio.ScioContext
import com.spotify.scio.io.Tap
import com.spotify.scio.util.ScioUtil
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.io.fs.MatchResult.Metadata
import org.apache.beam.sdk.io.{Compression, FileBasedSink, FileSystems, TextIO => BTextIO}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.io.IOUtils

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try

case class TextIO(path: String) extends ScioIO[String] {

  case class ReadParams(compression: Compression = Compression.AUTO)

  case class WriteParams(suffix: String = ".txt",
                         numShards: Int = 0,
                         compression: Compression = Compression.UNCOMPRESSED)

  type ReadP = ReadParams
  type WriteP = WriteParams

  def id: String = path

  def read(sc: ScioContext, params: ReadParams): SCollection[String] =
    sc.wrap(sc.applyInternal(BTextIO.read().from(path)
      .withCompression(params.compression))).setName(path)

  def write(data: SCollection[String], params: WriteParams): Future[Tap[String]] = {
    data.applyInternal(textOut(path, params))
    data.context.makeFuture(tap(ReadParams()))
  }

  def tap(params: ReadParams): Tap[String] = new Tap[String] {
    override def value: Iterator[String] = TextIO.textFile(ScioUtil.addPartSuffix(path))

    override def open(sc: ScioContext): SCollection[String] = {
      val textIO = TextIO(ScioUtil.addPartSuffix(path))
      val readParams = textIO.ReadParams(compression = params.compression)
      textIO.read(sc, readParams)
    }
  }

  private def textOut(path: String, params: WriteParams) =
    BTextIO.write()
      .to(pathWithShards(path))
      .withSuffix(params.suffix)
      .withNumShards(params.numShards)
      .withWritableByteChannelFactory(
        FileBasedSink.CompressionType.fromCanonical(params.compression))

  private[scio] def pathWithShards(path: String) = path.replaceAll("\\/+$", "") + "/part"
}

object TextIO {

  private[scio] def textFile(path: String): Iterator[String] = {
    val factory = new CompressorStreamFactory()

    def wrapInputStream(in: InputStream) = {
      val buffered = new BufferedInputStream(in)
      Try(factory.createCompressorInputStream(buffered)).getOrElse(buffered)
    }

    val input = getDirectoryInputStream(path, wrapInputStream)
    IOUtils.lineIterator(input, Charsets.UTF_8).asScala
  }

  private def getDirectoryInputStream(path: String,
                                      wrapperFn: InputStream => InputStream = identity)
  : InputStream = {
    val inputs = listFiles(path).map(getObjectInputStream).map(wrapperFn).asJava
    new SequenceInputStream(Collections.enumeration(inputs))
  }

  private def listFiles(path: String): Seq[Metadata] = FileSystems.`match`(path).metadata().asScala

  private def getObjectInputStream(meta: Metadata): InputStream =
    Channels.newInputStream(FileSystems.open(meta.resourceId()))
}
