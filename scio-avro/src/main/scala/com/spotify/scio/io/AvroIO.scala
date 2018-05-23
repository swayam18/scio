/*
 * Copyright 2018 Spotify AB.
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

package com.spotify.scio.io

import com.google.protobuf.Message
import org.apache.beam.sdk.transforms.{DoFn, SerializableFunction}
import org.apache.beam.sdk.transforms.DoFn.ProcessElement
import org.apache.beam.sdk.{io => beam}
import org.apache.avro.generic.GenericRecord
import org.apache.avro.Schema
import org.apache.avro.file.CodecFactory
import org.apache.avro.specific.SpecificRecordBase
import com.spotify.scio.ScioContext
import com.spotify.scio.values._
import com.spotify.scio.coders.AvroBytesUtil
<<<<<<< HEAD
import com.spotify.scio.Implicits._
import com.spotify.scio.util.ScioUtil
=======
import com.spotify.scio.util.ScioUtil
import com.spotify.scio.coders.{Coder, CoderMaterializer}
>>>>>>> 5f3acc85... Introduce static coders
import com.spotify.scio.avro.types.AvroType
import com.spotify.scio.avro.types.AvroType.HasAvroAnnotation

import scala.collection.JavaConverters._
import scala.concurrent.Future
<<<<<<< HEAD
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

final case class ObjectFileIO[T: ClassTag](path: String) extends ScioIO[T] {
=======
import scala.reflect.{ClassTag, classTag}
import scala.reflect.runtime.universe._

final case class ObjectFileIO[T: Coder](path: String) extends ScioIO[T] {
>>>>>>> 5f3acc85... Introduce static coders

  override type ReadP = Unit
  override type WriteP = ObjectFileIO.WriteParam

  /**
   * Get an SCollection for an object file using default serialization.
   *
   * Serialized objects are stored in Avro files to leverage Avro's block file format. Note that
   * serialization is not guaranteed to be compatible across Scio releases.
   */
  override def read(sc: ScioContext, params: ReadP): SCollection[T] = {
<<<<<<< HEAD
    val coder = sc.pipeline.getCoderRegistry.getScalaCoder[T](sc.options)
=======
    val coder = CoderMaterializer.beam(sc, Coder[T])
    implicit val bcoder = Coder.genericRecordCoder(AvroBytesUtil.schema)
>>>>>>> 5f3acc85... Introduce static coders
    AvroIO[GenericRecord](path, AvroBytesUtil.schema).read(sc, params)
      .parDo(new DoFn[GenericRecord, T] {
        @ProcessElement
        private[scio] def processElement(c: DoFn[GenericRecord, T]#ProcessContext): Unit = {
          c.output(AvroBytesUtil.decode(coder, c.element()))
        }
      })
  }

  /**
   * Save this SCollection as an object file using default serialization.
   *
   * Serialized objects are stored in Avro files to leverage Avro's block file format. Note that
   * serialization is not guaranteed to be compatible across Scio releases.
   */
  override def write(data: SCollection[T], params: WriteP): Future[Tap[T]] = {
<<<<<<< HEAD
    val elemCoder = data.getCoder[T]
=======
    val elemCoder = CoderMaterializer.beam(data.context, Coder[T])
    implicit val bcoder = Coder.genericRecordCoder(AvroBytesUtil.schema)
>>>>>>> 5f3acc85... Introduce static coders
    val bytes = data
      .parDo(new DoFn[T, GenericRecord] {
        @ProcessElement
        private[scio] def processElement(c: DoFn[T, GenericRecord]#ProcessContext): Unit =
          c.output(AvroBytesUtil.encode(elemCoder, c.element()))
      })
    AvroIO[GenericRecord](path, AvroBytesUtil.schema).write(bytes, params)
    data.context.makeFuture(tap(Unit))
  }

  override def tap(read: ReadP): Tap[T] = ObjectFileTap[T](ScioUtil.addPartSuffix(path))
}

object ObjectFileIO {
  type WriteParam = AvroIO.WriteParam
  val WriteParam = AvroIO.WriteParam
}

<<<<<<< HEAD
final case class ProtobufIO[T : ClassTag](path: String)
=======
final case class ProtobufIO[T : ClassTag : Coder](path: String)
>>>>>>> 5f3acc85... Introduce static coders
                                         (implicit ev: T <:< Message) extends ScioIO[T] {
  override type ReadP = Unit
  override type WriteP = ProtobufIO.WriteParam

  /**
   * Get an SCollection for a Protobuf file.
   *
   * Protobuf messages are serialized into `Array[Byte]` and stored in Avro files to leverage
   * Avro's block file format.
   */
  override def read(sc: ScioContext, params: ReadP): SCollection[T] =
    ObjectFileIO[T](path).read(sc, params)

  /**
   * Save this SCollection as a Protobuf file.
   *
   * Protobuf messages are serialized into `Array[Byte]` and stored in Avro files to leverage
   * Avro's block file format.
   */
  override def write(data: SCollection[T], params: WriteP): Future[Tap[T]] = {
    import me.lyh.protobuf.generic
<<<<<<< HEAD
    val schema = generic.Schema.of[Message](data.ct.asInstanceOf[ClassTag[Message]]).toJson
=======
    val schema = generic.Schema.of[Message](classTag[T].asInstanceOf[ClassTag[Message]]).toJson
>>>>>>> 5f3acc85... Introduce static coders
    val metadata = params.metadata ++ Map("protobuf.generic.schema" -> schema)
    ObjectFileIO[T](path).write(data, params.copy(metadata = metadata))
  }

  override def tap(read: ReadP): Tap[T] = ObjectFileTap[T](ScioUtil.addPartSuffix(path))
}

object ProtobufIO {
  type WriteParam = AvroIO.WriteParam
  val WriteParam = AvroIO.WriteParam
}

<<<<<<< HEAD
final case class AvroIO[T: ClassTag](path: String, schema: Schema = null) extends ScioIO[T] {
=======
final case class AvroIO[T: ClassTag : Coder](path: String, schema: Schema = null) extends ScioIO[T] {
>>>>>>> 5f3acc85... Introduce static coders

  override type ReadP = Unit
  override type WriteP = AvroIO.WriteParam

  private def avroOut[U](sc: SCollection[T],
                         write: beam.AvroIO.Write[U],
                         path: String, numShards: Int, suffix: String,
                         codec: CodecFactory,
                         metadata: Map[String, AnyRef]) =
    write
      .to(sc.pathWithShards(path))
      .withNumShards(numShards)
      .withSuffix(suffix + ".avro")
      .withCodec(codec)
      .withMetadata(metadata.asJava)

  override def testId: String = s"AvroIO($path)"

  /**
   * Get an SCollection for an Avro file. `schema` must be not null if `T` is of type
   * [[org.apache.avro.generic.GenericRecord GenericRecord]].
   */
  override def read(sc: ScioContext, params: ReadP): SCollection[T] = {
    val cls = ScioUtil.classOf[T]
    val t = if (classOf[SpecificRecordBase] isAssignableFrom cls) {
      beam.AvroIO.read(cls).from(path)
    } else {
      beam.AvroIO.readGenericRecords(schema).from(path).asInstanceOf[beam.AvroIO.Read[T]]
    }
    sc.wrap(sc.applyInternal(t)).setName(path)
  }

  /**
   * Save this SCollection as an Avro file. `schema` must be not null if `T` is of type
   * [[org.apache.avro.generic.GenericRecord GenericRecord]].
   */
  override def write(data: SCollection[T], params: WriteP): Future[Tap[T]] = {
    val cls = ScioUtil.classOf[T]
    val t = if (classOf[SpecificRecordBase] isAssignableFrom cls) {
      beam.AvroIO.write(cls)
    } else {
      beam.AvroIO.writeGenericRecords(schema).asInstanceOf[beam.AvroIO.Write[T]]
    }
    data.applyInternal(
      avroOut(data, t, path, params.numShards, params.suffix, params.codec, params.metadata))
    data.context.makeFuture(tap(Unit))
  }

  override def tap(read: ReadP): Tap[T] = AvroTap[T](ScioUtil.addPartSuffix(path), schema)
}

object AvroIO {
  final case class WriteParam(
    numShards: Int = 0,
    suffix: String = "",
    codec: CodecFactory = CodecFactory.deflateCodec(6),
    metadata: Map[String, AnyRef] = Map.empty)
}

object AvroTyped {

<<<<<<< HEAD
  final case class AvroIO[T : ClassTag : TypeTag](path: String)
=======
  final case class AvroIO[T : ClassTag : TypeTag : Coder](path: String)
>>>>>>> 5f3acc85... Introduce static coders
                                                 (implicit ev: T <:< HasAvroAnnotation)
    extends ScioIO[T] {

    override type ReadP = Unit
    override type WriteP = com.spotify.scio.io.AvroIO.WriteParam

    private def typedAvroOut[U](sc: SCollection[T],
                                write: beam.AvroIO.TypedWrite[U, Void, GenericRecord],
                                path: String, numShards: Int, suffix: String,
                                codec: CodecFactory,
                                metadata: Map[String, AnyRef]) =
      write
        .to(sc.pathWithShards(path))
        .withNumShards(numShards)
        .withSuffix(suffix + ".avro")
        .withCodec(codec)
        .withMetadata(metadata.asJava)

    /**
     * Get a typed SCollection from an Avro schema.
     *
     * Note that `T` must be annotated with
     * [[com.spotify.scio.avro.types.AvroType AvroType.fromSchema]],
     * [[com.spotify.scio.avro.types.AvroType AvroType.fromPath]], or
     * [[com.spotify.scio.avro.types.AvroType AvroType.toSchema]].
     */
    override def read(sc: ScioContext, params: ReadP): SCollection[T] = {
      val avroT = AvroType[T]
      val t = beam.AvroIO.readGenericRecords(avroT.schema).from(path)
      sc.wrap(sc.applyInternal(t)).setName(path).map(avroT.fromGenericRecord)
    }

    /**
     * Save this SCollection as an Avro file. Note that element type `T` must be a case class
     * annotated with [[com.spotify.scio.avro.types.AvroType AvroType.toSchema]].
     */
    override def write(data: SCollection[T], params: WriteP): Future[Tap[T]] = {
      val avroT = AvroType[T]
      val t = beam.AvroIO.writeCustomTypeToGenericRecords()
        .withFormatFunction(new SerializableFunction[T, GenericRecord] {
          override def apply(input: T): GenericRecord = avroT.toGenericRecord(input)
        })
        .withSchema(avroT.schema)
      data.applyInternal(
        typedAvroOut(data, t, path, params.numShards, params.suffix, params.codec, params.metadata))
      data.context.makeFuture(tap(Unit))
    }

    override def tap(read: ReadP): Tap[T] = {
      val avroT = AvroType[T]
<<<<<<< HEAD
=======
      implicit val bcoder = Coder.genericRecordCoder(avroT.schema)
>>>>>>> 5f3acc85... Introduce static coders
      AvroTap[GenericRecord](ScioUtil.addPartSuffix(path), avroT.schema)
        .map(avroT.fromGenericRecord)
    }
  }

}
