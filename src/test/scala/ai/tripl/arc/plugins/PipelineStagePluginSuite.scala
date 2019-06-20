package ai.tripl.arc.plugins

import ai.tripl.arc.api.API._
import ai.tripl.arc.util.ConfigUtils
import ai.tripl.arc.util.log.LoggerFactory
import ai.tripl.arc.api.API._
import org.apache.spark.sql.SparkSession
import org.scalatest.{BeforeAndAfter, FunSuite}

class PipelineStagePluginSuite extends FunSuite with BeforeAndAfter {

  var session: SparkSession = _

  before {
    val spark = SparkSession
      .builder()
      .master("local[*]")
      .appName("Spark ETL Test")
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    // set for deterministic timezone
    spark.conf.set("spark.sql.session.timeZone", "UTC")   

    session = spark
  }

  after {
    session.stop()
  }

  test("Read config with custom pipeline stage") {
    implicit val spark = session

    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = ARCContext(jobId=None, jobName=None, environment="test", environmentId=None, configUri=None, isStreaming=false, ignoreEnvironments=false, lifecyclePlugins=Nil, disableDependencyValidation=false)

    val argsMap = collection.mutable.HashMap[String, String]()

    val pipeline = ConfigUtils.parsePipeline(Option("classpath://conf/custom_plugin.conf"), argsMap, ConfigUtils.Graph(Nil, Nil, false), arcContext)

    pipeline match {
      case Right( (ETLPipeline(CustomStage(name, None, params, stage) :: Nil), _, _) ) =>
        assert(name === "custom plugin")
        val configParms = Map[String, String](
          "foo" -> "bar"
        )
        assert(params === configParms)
        assert(stage.getClass.getName === "ai.tripl.arc.plugins.ArcCustomPipelineStage")
      case _ => fail("expected CustomStage")
    }
  }

}
