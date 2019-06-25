package ai.tripl.arc

import java.net.URI

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter


import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.spark.sql._
import org.apache.spark.sql.functions._

import ai.tripl.arc.api._
import ai.tripl.arc.api.API._
import ai.tripl.arc.util.log.LoggerFactory 

import ai.tripl.arc.util.TestUtils

class DelimitedLoadSuite extends FunSuite with BeforeAndAfter {

  var session: SparkSession = _  
  val targetFile = FileUtils.getTempDirectoryPath() + "extract.csv" 
  val outputView = "dataset"

  before {
    implicit val spark = SparkSession
                  .builder()
                  .master("local[*]")
                  .config("spark.ui.port", "9999")
                  .config("spark.sql.streaming.checkpointLocation", "/tmp/checkpoint")
                  .appName("Spark ETL Test")
                  .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    // set for deterministic timezone
    spark.conf.set("spark.sql.session.timeZone", "UTC")    

    session = spark

    // ensure target removed
    FileUtils.deleteQuietly(new java.io.File(targetFile)) 
  }

  after {
    session.stop()

    // clean up test dataset
    FileUtils.deleteQuietly(new java.io.File(targetFile))     
  }

  test("DelimitedLoad") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = TestUtils.getARCContext(isStreaming=false)

    val dataset = TestUtils.getKnownDataset
    dataset.createOrReplaceTempView(outputView)

    load.DelimitedLoadStage.execute(
      load.DelimitedLoadStage(
        plugin=new load.DelimitedLoad,
        name=outputView, 
        description=None,
        inputView=outputView, 
        outputURI=new URI(targetFile), 
        settings=new Delimited(header=true, sep=Delimiter.Comma),
        partitionBy=Nil, 
        numPartitions=None, 
        authentication=None, 
        saveMode=SaveMode.Overwrite, 
        params=Map.empty
      )
    )

    // Delimited will load everything as StringType
    // Delimited does not support writing NullType
    val expected = TestUtils.getKnownDataset
      .withColumn("booleanDatum", $"booleanDatum".cast("string"))
      .withColumn("dateDatum", $"dateDatum".cast("string"))
      .withColumn("decimalDatum", $"decimalDatum".cast("string"))
      .withColumn("doubleDatum", $"doubleDatum".cast("string"))
      .withColumn("integerDatum", $"integerDatum".cast("string"))
      .withColumn("longDatum", $"longDatum".cast("string"))
      .withColumn("timestampDatum", from_unixtime(unix_timestamp($"timestampDatum"), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
      .drop($"nullDatum")
    val actual = spark.read.option("header", true).csv(targetFile)

    assert(TestUtils.datasetEquality(expected, actual))
  }  

  test("DelimitedLoad: partitionBy") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = TestUtils.getARCContext(isStreaming=false)

    val dataset = TestUtils.getKnownDataset
    dataset.createOrReplaceTempView(outputView)
    assert(dataset.select(spark_partition_id()).distinct.count === 1)      

    load.DelimitedLoadStage.execute(
      load.DelimitedLoadStage(
        plugin=new load.DelimitedLoad,
        name=outputView, 
        description=None,
        inputView=outputView, 
        outputURI=new URI(targetFile), 
        settings=new Delimited(header=true, sep=Delimiter.Comma),
        partitionBy="booleanDatum" :: Nil, 
        numPartitions=None, 
        authentication=None, 
        saveMode=SaveMode.Overwrite, 
        params=Map.empty
      )
    )

    val actual = spark.read.csv(targetFile)
    assert(actual.select(spark_partition_id()).distinct.count === 2)
  }  

  test("DelimitedLoad: Structured Streaming") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    implicit val arcContext = TestUtils.getARCContext(isStreaming=true)

    val readStream = spark
      .readStream
      .format("rate")
      .option("rowsPerSecond", "1")
      .load

    readStream.createOrReplaceTempView(outputView)

    load.DelimitedLoadStage.execute(
      load.DelimitedLoadStage(
        plugin=new load.DelimitedLoad,
        name=outputView, 
        description=None,
        inputView=outputView, 
        outputURI=new URI(targetFile), 
        settings=new Delimited(header=true, sep=Delimiter.Comma),
        partitionBy=Nil, 
        numPartitions=None, 
        authentication=None, 
        saveMode=SaveMode.Overwrite, 
        params=Map.empty
      )
    )

    Thread.sleep(2000)
    spark.streams.active.foreach(streamingQuery => streamingQuery.stop)

    val actual = spark.read.option("header", "true").csv(targetFile)
    assert(actual.schema.map(_.name).toSet.equals(Array("timestamp", "value").toSet))
  }    

}
