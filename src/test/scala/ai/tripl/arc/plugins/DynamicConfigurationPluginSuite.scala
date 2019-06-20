package ai.tripl.arc.plugins

import ai.tripl.arc.api.API._
import ai.tripl.arc.util.ConfigUtils
import ai.tripl.arc.util.ConfigUtils._
import ai.tripl.arc.util.log.LoggerFactory
import org.apache.spark.sql.SparkSession
import org.scalatest.{BeforeAndAfter, FunSuite}


class DynamicConfigurationPluginSuite extends FunSuite with BeforeAndAfter {

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

  test("Read config with dynamic configuration plugin") {
    implicit val spark = session

    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = ARCContext(jobId=None, jobName=None, environment="test", environmentId=None, configUri=None, isStreaming=false, ignoreEnvironments=false, lifecyclePlugins=Nil, disableDependencyValidation=false)

    val argsMap = collection.mutable.HashMap[String, String]()

    val pipeline = ConfigUtils.parsePipeline(Option("classpath://conf/dynamic_config_plugin.conf"), argsMap, ConfigUtils.Graph(Nil, Nil, false), arcContext)

    pipeline match {
      case Right( (ETLPipeline(CustomStage(name, None, params, stage) :: Nil), _, _) ) =>
        assert(name === "custom plugin")
        val configParms = Map[String, String](
          "foo" -> "baz",
          "bar" -> "testValue"
        )
        assert(params === configParms)
        assert(stage.getClass.getName === "ai.tripl.arc.plugins.ArcCustomPipelineStage")
      case _ => fail("expected CustomStage")
    }
    
  }

  test("Test argsMap precedence") { 
    implicit val spark = session
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = ARCContext(jobId=None, jobName=None, environment="test", environmentId=None, configUri=None, isStreaming=false, ignoreEnvironments=false, lifecyclePlugins=Nil, disableDependencyValidation=false)

    val argsMap = collection.mutable.HashMap[String, String]("ARGS_MAP_VALUE" -> "before\"${arc.paramvalue}\"after")

    val pipeline = ConfigUtils.parsePipeline(Option("classpath://conf/dynamic_config_plugin_precendence.conf"), argsMap, ConfigUtils.Graph(Nil, Nil, false), arcContext)

    pipeline match {
      case Right( (ETLPipeline(CustomStage(name, None, params, stage) :: Nil), _, _) ) =>
        assert(name === "custom plugin")
        val configParms = Map[String, String](
          "foo" -> "beforeparamValueafter"
        )
        assert(params === configParms)
        assert(stage.getClass.getName === "ai.tripl.arc.plugins.ArcCustomPipelineStage")
      case _ => fail("expected CustomStage")
    } 
  }     

  test("Test missing plugin") { 
    implicit val spark = session
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = ARCContext(jobId=None, jobName=None, environment="test", environmentId=None, configUri=None, isStreaming=false, ignoreEnvironments=false, lifecyclePlugins=Nil, disableDependencyValidation=false)

    val argsMap = collection.mutable.HashMap[String, String]()

    val pipeline = ConfigUtils.parsePipeline(Option("classpath://conf/custom_plugin_missing.conf"), argsMap, ConfigUtils.Graph(Nil, Nil, false), arcContext)

    pipeline match {
      case Left(stageError) => {
        assert(stageError == 
        StageError(0, "unknown",3,List(
            ConfigError("stages", Some(3), "Unknown stage type: 'ai.tripl.arc.plugins.ThisWillNotBeFound'")
          )
        ) :: Nil)
      }
      case Right(_) => assert(false)
    } 
  }   

  test("Read config with dynamic configuration plugin environments true") {
    implicit val spark = session

    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = ARCContext(jobId=None, jobName=None, environment="production", environmentId=None, configUri=None, isStreaming=false, ignoreEnvironments=false, lifecyclePlugins=Nil, disableDependencyValidation=false)

    val argsMap = collection.mutable.HashMap[String, String]()

    val pipeline = ConfigUtils.parsePipeline(Option("classpath://conf/dynamic_config_plugin.conf"), argsMap, ConfigUtils.Graph(Nil, Nil, false), arcContext)

    pipeline match {
      case Right( (ETLPipeline(CustomStage(name, None, params, stage) :: Nil), _, _) ) =>
        assert(name === "custom plugin")
        val configParms = Map[String, String](
          "foo" -> "baz",
          "bar" -> "productionValue"
        )
        assert(params === configParms)
        assert(stage.getClass.getName === "ai.tripl.arc.plugins.ArcCustomPipelineStage")
      case _ => fail("expected CustomStage")
    }
    
  }    
}
