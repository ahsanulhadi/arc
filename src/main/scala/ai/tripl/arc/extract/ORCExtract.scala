package ai.tripl.arc.extract

import scala.collection.JavaConverters._

import org.apache.spark.sql._

import ai.tripl.arc.api.API._
import ai.tripl.arc.config._
import ai.tripl.arc.config.Error._
import ai.tripl.arc.plugins.PipelineStagePlugin
import ai.tripl.arc.util.CloudUtils
import ai.tripl.arc.util.DetailException
import ai.tripl.arc.util.EitherUtils._
import ai.tripl.arc.util.ExtractUtils
import ai.tripl.arc.util.MetadataUtils
import ai.tripl.arc.util.Utils

class ORCExtract extends PipelineStagePlugin {

  val version = Utils.getFrameworkVersion

  def instantiate(index: Int, config: com.typesafe.config.Config)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger, arcContext: ARCContext): Either[List[ai.tripl.arc.config.Error.StageError], PipelineStage] = {
    import ai.tripl.arc.config.ConfigReader._
    import ai.tripl.arc.config.ConfigUtils._
    implicit val c = config

    val expectedKeys = "type" :: "name" :: "description" :: "environments" :: "inputURI" :: "outputView" :: "authentication" :: "contiguousIndex" :: "numPartitions" :: "partitionBy" :: "persist" :: "schemaURI" :: "schemaView" :: "params" :: "basePath" :: "watermark" :: Nil
    val name = getValue[String]("name")
    val description = getOptionalValue[String]("description")
    val parsedGlob = getValue[String]("inputURI") |> parseGlob("inputURI") _
    val outputView = getValue[String]("outputView")
    val persist = getValue[java.lang.Boolean]("persist", default = Some(false))
    val numPartitions = getOptionalValue[Int]("numPartitions")
    val partitionBy = getValue[StringList]("partitionBy", default = Some(Nil))
    val authentication = readAuthentication("authentication")
    val contiguousIndex = getValue[java.lang.Boolean]("contiguousIndex", default = Some(true))
    val extractColumns = if(c.hasPath("schemaURI")) getValue[String]("schemaURI") |> parseURI("schemaURI") _ |> getExtractColumns("schemaURI", authentication) _ else Right(List.empty)
    val schemaView = if(c.hasPath("schemaView")) getValue[String]("schemaView") else Right("")
    val basePath = getOptionalValue[String]("basePath")
    val watermark = readWatermark("watermark")
    val params = readMap("params", c)
    val invalidKeys = checkValidKeys(c)(expectedKeys)

    (name, description, extractColumns, schemaView, parsedGlob, outputView, persist, numPartitions, authentication, contiguousIndex, partitionBy, basePath, invalidKeys, watermark) match {
      case (Right(name), Right(description), Right(extractColumns), Right(schemaView), Right(parsedGlob), Right(outputView), Right(persist), Right(numPartitions), Right(authentication), Right(contiguousIndex), Right(partitionBy), Right(basePath), Right(invalidKeys), Right(watermark)) =>
        val schema = if(c.hasPath("schemaView")) Left(schemaView) else Right(extractColumns)

        val stage = ORCExtractStage(
          plugin=this,
          name=name,
          description=description,
          schema=schema,
          outputView=outputView,
          input=parsedGlob,
          authentication=authentication,
          params=params,
          persist=persist,
          numPartitions=numPartitions,
          partitionBy=partitionBy,
          basePath=basePath,
          contiguousIndex=contiguousIndex,
          watermark=watermark
        )

        authentication.foreach { authentication => stage.stageDetail.put("authentication", authentication.method) }
        basePath.foreach { stage.stageDetail.put("basePath", _) }
        stage.stageDetail.put("contiguousIndex", java.lang.Boolean.valueOf(contiguousIndex))
        stage.stageDetail.put("inputURI", parsedGlob)
        stage.stageDetail.put("outputView", outputView)
        stage.stageDetail.put("params", params.asJava)
        stage.stageDetail.put("persist", java.lang.Boolean.valueOf(persist))
        for (watermark <- watermark) {
          val watermarkMap = new java.util.HashMap[String, Object]()
          watermarkMap.put("eventTime", watermark.eventTime)
          watermarkMap.put("delayThreshold", watermark.delayThreshold)
          stage.stageDetail.put("watermark", watermarkMap)
        }

        Right(stage)
      case _ =>
        val allErrors: Errors = List(name, description, extractColumns, schemaView, parsedGlob, outputView, persist, numPartitions, authentication, contiguousIndex, partitionBy, basePath, invalidKeys, watermark).collect{ case Left(errs) => errs }.flatten
        val stageName = stringOrDefault(name, "unnamed stage")
        val err = StageError(index, stageName, c.origin.lineNumber, allErrors)
        Left(err :: Nil)
    }

  }

}

case class ORCExtractStage(
    plugin: ORCExtract,
    name: String,
    description: Option[String],
    schema: Either[String, List[ExtractColumn]],
    outputView: String,
    input: String,
    authentication: Option[Authentication],
    params: Map[String, String],
    persist: Boolean,
    numPartitions: Option[Int],
    partitionBy: List[String],
    contiguousIndex: Boolean,
    basePath: Option[String],
    watermark: Option[Watermark]
  ) extends PipelineStage {

  override def execute()(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger, arcContext: ARCContext): Option[DataFrame] = {
    ORCExtractStage.execute(this)
  }
}

object ORCExtractStage {

  def execute(stage: ORCExtractStage)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger, arcContext: ARCContext): Option[DataFrame] = {

    // try to get the schema
    val optionSchema = try {
      ExtractUtils.getSchema(stage.schema)(spark, logger)
    } catch {
      case e: Exception => throw new Exception(e) with DetailException {
        override val detail = stage.stageDetail
      }
    }

    CloudUtils.setHadoopConfiguration(stage.authentication)

    // if incoming dataset is empty create empty dataset with a known schema
    val df = try {
      if (arcContext.isStreaming) {
        optionSchema match {
          case Some(schema) => {
            stage.watermark match {
              case Some(watermark) => Right(spark.readStream.option("mergeSchema", "true").schema(schema).orc(stage.input).withWatermark(watermark.eventTime, watermark.delayThreshold))
              case None => Right(spark.readStream.option("mergeSchema", "true").schema(schema).orc(stage.input))
            }
          }
          case None => throw new Exception("ORCExtract requires 'schemaURI' or 'schemaView' to be set if Arc is running in streaming mode.")
        }
      } else {
        stage.basePath match {
          case Some(basePath) => Right(spark.read.option("mergeSchema", "true").option("basePath", basePath).orc(stage.input))
          case None => Right(spark.read.option("mergeSchema", "true").orc(stage.input))
        }
      }
    } catch {
      case e: AnalysisException if (e.getMessage == "Unable to infer schema for ORC. It must be specified manually.;") =>
        Left(FileNotFoundExtractError(Option(stage.input)))
      case e: AnalysisException if (e.getMessage.contains("Path does not exist")) =>
        Left(PathNotExistsExtractError(Option(stage.input)))

      case e: AnalysisException  =>
        println(e.getMessage())
        throw new Exception(e)
      case e: Exception => throw new Exception(e) with DetailException {
        override val detail = stage.stageDetail
      }
    }

    // if incoming dataset has 0 columns then try to create empty dataset with correct schema
    // or throw enriched error message
    val emptyDataframeHandlerDF = try {
      df match {
        case Right(df) =>
          if (df.schema.length == 0) {
            optionSchema match {
              case Some(structType) => spark.createDataFrame(spark.sparkContext.emptyRDD[Row], structType)
              case None => throw new Exception(EmptySchemaExtractError(Some(stage.input)).getMessage)
            }
          } else {
            df
          }
        case Left(error) => {
          stage.stageDetail.put("records", java.lang.Integer.valueOf(0))
          optionSchema match {
            case Some(s) => spark.createDataFrame(spark.sparkContext.emptyRDD[Row], s)
            case None => throw new Exception(error.getMessage)
          }
        }
      }
    } catch {
      case e: Exception => throw new Exception(e.getMessage) with DetailException {
        override val detail = stage.stageDetail
      }
    }

    // add internal columns data _filename, _index
    val sourceEnrichedDF = ExtractUtils.addInternalColumns(emptyDataframeHandlerDF, stage.contiguousIndex)

    // set column metadata if exists
    val enrichedDF = optionSchema match {
        case Some(schema) => MetadataUtils.setMetadata(sourceEnrichedDF, schema)
        case None => sourceEnrichedDF
    }
    // repartition to distribute rows evenly
    val repartitionedDF = stage.partitionBy match {
      case Nil => {
        stage.numPartitions match {
          case Some(numPartitions) => enrichedDF.repartition(numPartitions)
          case None => enrichedDF
        }
      }
      case partitionBy => {
        // create a column array for repartitioning
        val partitionCols = partitionBy.map(col => enrichedDF(col))
        stage.numPartitions match {
          case Some(numPartitions) => enrichedDF.repartition(numPartitions, partitionCols:_*)
          case None => enrichedDF.repartition(partitionCols:_*)
        }
      }
    }
    if (arcContext.immutableViews) repartitionedDF.createTempView(stage.outputView) else repartitionedDF.createOrReplaceTempView(stage.outputView)

    if (!repartitionedDF.isStreaming) {
      stage.stageDetail.put("inputFiles", java.lang.Integer.valueOf(repartitionedDF.inputFiles.length))
      stage.stageDetail.put("outputColumns", java.lang.Integer.valueOf(repartitionedDF.schema.length))
      stage.stageDetail.put("numPartitions", java.lang.Integer.valueOf(repartitionedDF.rdd.partitions.length))

      if (stage.persist) {
        repartitionedDF.persist(arcContext.storageLevel)
        stage.stageDetail.put("records", java.lang.Long.valueOf(repartitionedDF.count))
      }
    }

    Option(repartitionedDF)
  }

}
