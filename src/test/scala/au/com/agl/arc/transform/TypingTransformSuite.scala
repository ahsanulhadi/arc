package au.com.agl.arc

import java.net.URI

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

import au.com.agl.arc.api._
import au.com.agl.arc.api.API._
import au.com.agl.arc.util.log.LoggerFactory 

import au.com.agl.arc.util._

class TypingTransformSuite extends FunSuite with BeforeAndAfter {

  // currently assuming local file system
  var session: SparkSession = _  
  val targetFile = FileUtils.getTempDirectoryPath() + "extract.csv" 
  val emptyDirectory = FileUtils.getTempDirectoryPath() + "empty.csv" 
  val inputView = "intputView"
  val outputView = "outputView"

  before {
    implicit val spark = SparkSession
                  .builder()
                  .master("local[*]")
                  .config("spark.ui.port", "9999")
                  .appName("Spark ETL Test")
                  .getOrCreate()
    spark.sparkContext.setLogLevel("FATAL")

    session = spark
    import spark.implicits._

    // set for deterministic timezone
    spark.conf.set("spark.sql.session.timeZone", "UTC")   

    // recreate test dataset
    FileUtils.deleteQuietly(new java.io.File(targetFile)) 
    // Delimited does not support writing NullType
    TestDataUtils.getKnownDataset.drop($"nullDatum").write.csv(targetFile)     
  }

  after {
    // clean up test dataset
    FileUtils.deleteQuietly(new java.io.File(targetFile))     
    session.stop() 
  }

  test("TypingTransform") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    // load csv
    val extractDataset = spark.read.csv(targetFile)
    extractDataset.createOrReplaceTempView(inputView)

    // parse json schema to List[ExtractColumn]
    val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(TestDataUtils.getKnownDatasetMetadataJson)

    val actual = transform.TypingTransform.transform(
      TypingTransform(
        name="dataset",
        cols=Right(cols.right.getOrElse(Nil)), 
        inputView=inputView,
        outputView=outputView, 
        params=Map.empty,
        persist=false,
        failMode=None
      )
    ).get

    // add errors array to schema using udf
    val errorStructType: StructType =
      StructType(
        StructField("field", StringType, false) ::
        StructField("message", StringType, false) :: Nil
      )
    val addErrors = org.apache.spark.sql.functions.udf(() => new Array(0), ArrayType(errorStructType) )

    val expected = TestDataUtils.getKnownDataset
      .drop($"nullDatum")
      .withColumn("_errors", addErrors())

    assert(TestDataUtils.datasetEquality(expected, actual))

    // test metadata
    val booleanDatumMetadata = actual.schema.fields(actual.schema.fieldIndex("booleanDatum")).metadata

    assert(booleanDatumMetadata.getBoolean("booleanMeta") == true)
    assert(booleanDatumMetadata.getBooleanArray("booleanArrayMeta").deep == Array(true, false).deep)

    assert(booleanDatumMetadata.getLong("longMeta") == 10)
    assert(booleanDatumMetadata.getLongArray("longArrayMeta").deep == Array(10, 20).deep)

    assert(booleanDatumMetadata.getDouble("doubleMeta") == 0.141)
    assert(booleanDatumMetadata.getDoubleArray("doubleArrayMeta").deep == Array(0.141, 0.52).deep)

    assert(booleanDatumMetadata.getString("stringMeta") == "string")
    assert(booleanDatumMetadata.getStringArray("stringArrayMeta").deep == Array("string0", "string1").deep)
  }  

  test("TypingTransform: failMode - failfast") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    // get a corrupt dataset dataset
    // filter for a single row to have deterministic outcome
    // booleanDatum deliberately broken
    // timestamp will also fail due to format
    val extractDataset = TestDataUtils.getKnownStringDataset.filter("booleanDatum = true").drop("nullDatum").withColumn("booleanDatum", lit("bad"))
    extractDataset.createOrReplaceTempView(inputView)

    // parse json schema to List[ExtractColumn]
    val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(TestDataUtils.getKnownDatasetMetadataJson)


    // try without providing column metadata
    val thrown0 = intercept[Exception with DetailException] {
      val actual = transform.TypingTransform.transform(
        TypingTransform(
          name="dataset",
          cols=Right(cols.right.getOrElse(Nil)), 
          inputView=inputView,
          outputView=outputView, 
          params=Map.empty,
          persist=false,
          failMode=Option(FailModeTypeFailFast)
        )
      ).get
      actual.count
    }

    assert(thrown0.getMessage.contains("TypingTransform with failMode equal to 'failfast' cannot continue due to row with error(s): [[booleanDatum,Unable to convert 'bad' to boolean using provided true values: ['true'] or false values: ['false']], [timestampDatum,Unable to convert '2017-12-20 21:46:54' to timestamp using formatters ['yyyy-MM-dd'T'HH:mm:ss.SSSXXX'] and timezone 'UTC']]."))

  }    

  test("TypingTransform: metadata bad array") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val meta = """
    [
      {
        "id": "982cbf60-7ba7-4e50-a09b-d8624a5c49e6",
        "name": "booleanDatum",
        "description": "booleanDatum",
        "type": "boolean",
        "trim": false,
        "nullable": false,
        "nullableValues": [
            "",
            "null"
        ],
        "trueValues": [
            "true"
        ],
        "falseValues": [
            "false"
        ],
        "metadata": {
            "booleanArrayMeta": [true, false, "derp"]
        }
      }
    ]
    """
    
    // parse json schema to List[ExtractColumn]
    val thrown = intercept[IllegalArgumentException] {
      val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(meta)
    }

    assert(thrown.getMessage.contains("Metadata in field 'booleanDatum' cannot contain arrays of different types"))
  }  

  test("TypingTransform: metadata bad type object") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val meta = """
    [
      {
        "id": "982cbf60-7ba7-4e50-a09b-d8624a5c49e6",
        "name": "booleanDatum",
        "description": "booleanDatum",
        "type": "boolean",
        "trim": false,
        "nullable": false,
        "nullableValues": [
            "",
            "null"
        ],
        "trueValues": [
            "true"
        ],
        "falseValues": [
            "false"
        ],
        "metadata": {
            "booleanArrayMeta": {"derp": true}
        }
      }
    ]
    """
    
    // parse json schema to List[ExtractColumn]
    val thrown = intercept[IllegalArgumentException] {
      val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(meta)
    }
    assert(thrown.getMessage.contains("Metadata in field 'booleanDatum' cannot contain nested `objects`."))
  }  

  test("TypingTransform: metadata bad type null") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val meta = """
    [
      {
        "id": "982cbf60-7ba7-4e50-a09b-d8624a5c49e6",
        "name": "booleanDatum",
        "description": "booleanDatum",
        "type": "boolean",
        "trim": false,
        "nullable": false,
        "nullableValues": [
            "",
            "null"
        ],
        "trueValues": [
            "true"
        ],
        "falseValues": [
            "false"
        ],
        "metadata": {
            "booleanArrayMeta": null
        }
      }
    ]
    """
    
    // parse json schema to List[ExtractColumn]
    val thrown = intercept[IllegalArgumentException] {
      val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(meta)
    }
    assert(thrown.getMessage.contains("Metadata in field 'booleanDatum' cannot contain `null` values."))
  }   

  test("TypingTransform: metadata bad type same name as column") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val meta = """
    [
      {
        "id": "982cbf60-7ba7-4e50-a09b-d8624a5c49e6",
        "name": "booleanDatum",
        "description": "booleanDatum",
        "type": "boolean",
        "trim": false,
        "nullable": false,
        "nullableValues": [
            "",
            "null"
        ],
        "trueValues": [
            "true"
        ],
        "falseValues": [
            "false"
        ],
        "metadata": {
            "booleanDatum": null
        }
      }
    ]
    """
    
    // parse json schema to List[ExtractColumn]
    val thrown = intercept[IllegalArgumentException] {
      val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(meta)
    }
    assert(thrown.getMessage.contains("Metadata in field 'booleanDatum' cannot contain key with same name as column."))
  }    

  test("TypingTransform: metadata bad type multiple") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val meta = """
    [
      {
        "id": "982cbf60-7ba7-4e50-a09b-d8624a5c49e6",
        "name": "booleanDatum",
        "description": "booleanDatum",
        "type": "boolean",
        "trim": false,
        "nullable": false,
        "nullableValues": [
            "",
            "null"
        ],
        "trueValues": [
            "true"
        ],
        "falseValues": [
            "false"
        ],
        "metadata": {
            "badArray": [1, 1.1],
            "nullField": null
        }
      }
    ]
    """
    
    // parse json schema to List[ExtractColumn]
    val thrown = intercept[IllegalArgumentException] {
      val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(meta)
    }
    assert(thrown.getMessage.contains("Metadata in field 'booleanDatum' cannot contain `number` arrays of different types (all must be integers or all doubles)."))
    assert(thrown.getMessage.contains("Metadata in field 'booleanDatum' cannot contain `null` values."))
  }   

  test("TypingTransform: Execute with Structured Streaming" ) {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val dataset = TestDataUtils.getKnownStringDataset.drop("nullDatum")
    dataset.createOrReplaceTempView("knownData")

    val readStream = spark
      .readStream
      .format("rate")
      .option("rowsPerSecond", "1")
      .load

    readStream.createOrReplaceTempView("readstream")

    // cross join to the rate stream purely to register this dataset as a 'streaming' dataset.
    val input = spark.sql(s"""
    SELECT knownData.*
    FROM knownData
    CROSS JOIN readstream ON true
    """)

    input.createOrReplaceTempView(inputView)

    // parse json schema to List[ExtractColumn]
    val cols = au.com.agl.arc.util.MetadataSchema.parseJsonMetadata(TestDataUtils.getKnownDatasetMetadataJson)

    val transformDataset = transform.TypingTransform.transform(
      TypingTransform(
        name="dataset",
        cols=Right(cols.right.getOrElse(Nil)), 
        inputView=inputView,
        outputView=outputView, 
        params=Map.empty,
        persist=false,
        failMode=None
      )
    ).get

    val writeStream = transformDataset
      .writeStream
      .queryName("transformed") 
      .format("memory")
      .start

    val df = spark.table("transformed")

    try {
      Thread.sleep(2000)
      // will fail if typing does not work
      df.first.getBoolean(0)
    } finally {
      writeStream.stop
    }
  }   
}