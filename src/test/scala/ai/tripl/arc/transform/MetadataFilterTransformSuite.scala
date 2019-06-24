// package ai.tripl.arc

// import java.net.URI

// import org.scalatest.FunSuite
// import org.scalatest.BeforeAndAfter

// import org.apache.commons.io.FileUtils
// import org.apache.commons.io.IOUtils

// import org.apache.spark.ml.{Pipeline, PipelineModel}
// import org.apache.spark.ml.classification.LogisticRegression
// import org.apache.spark.ml.feature.{HashingTF, Tokenizer}
// import org.apache.spark.sql._
// import org.apache.spark.sql.functions._

// import ai.tripl.arc.api._
// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util.log.LoggerFactory 

// import ai.tripl.arc.util._

// class MetadataFilterTransformSuite extends FunSuite with BeforeAndAfter {

//   var session: SparkSession = _  
//   val targetFile = FileUtils.getTempDirectoryPath() + "MetadataFilterTransformSuite.csv"   
//   val inputView = "inputView"
//   val outputView = "outputView"

//   before {
//     implicit val spark = SparkSession
//                   .builder()
//                   .master("local[*]")
//                   .config("spark.ui.port", "9999")
//                   .appName("Spark ETL Test")
//                   .getOrCreate()
//     spark.sparkContext.setLogLevel("ERROR")

//     // set for deterministic timezone
//     spark.conf.set("spark.sql.session.timeZone", "UTC")   

//     session = spark
//     import spark.implicits._

//     // recreate test dataset
//     FileUtils.deleteQuietly(new java.io.File(targetFile)) 
//     // Delimited does not support writing NullType
//     TestUtils.getKnownDataset.drop($"nullDatum").write.csv(targetFile)    
//   }

//   after {
//     session.stop()

//     // clean up test dataset
//     FileUtils.deleteQuietly(new java.io.File(targetFile))     
//   }

//   test("MetadataFilterTransform: end-to-end") {
//     implicit val spark = session
//     implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
//     implicit val arcContext = ARCContext(jobId=None, jobName=None, environment="test", environmentId=None, configUri=None, isStreaming=false, ignoreEnvironments=false, lifecyclePlugins=Nil, disableDependencyValidation=false)

//     // load csv
//     val extractDataset = spark.read.csv(targetFile)
//     extractDataset.createOrReplaceTempView(inputView)

//     // parse json schema to List[ExtractColumn]
//     val cols = ai.tripl.arc.util.MetadataSchema.parseJsonMetadata(TestUtils.getKnownDatasetMetadataJson)

//     // apply metadata
//     transform.TypingTransform.transform(
//       TypingTransform(
//         name="TypingTransform",
//         description=None,
//         cols=Right(cols.right.getOrElse(null)), 
//         inputView=inputView,
//         outputView=outputView, 
//         params=Map.empty,
//         persist=false,
//         failMode=FailModeTypePermissive,
//         numPartitions=None,
//         partitionBy=Nil           
//       )
//     )

//     val conf = s"""{
//       "stages": [
//         {
//           "type": "MetadataFilterTransform",
//           "name": "filter out Personally identifiable information (pii) fields",
//           "environments": [
//             "production",
//             "test"
//           ],
//           "inputURI": "${getClass.getResource("/conf/sql/").toString}/filter_private.sql",
//           "inputView": "${outputView}",
//           "outputView": "customer_safe",
//           "sqlParams": {
//             "private": "true"
//           }
//         }        
//       ]
//     }"""
    
//     val argsMap = collection.mutable.Map[String, String]()
//     val graph = ConfigUtils.Graph(Nil, Nil, false)
//     val pipelineEither = ConfigUtils.parseConfig(Left(conf), argsMap, graph, arcContext)

//     pipelineEither match {
//       case Left(_) => assert(false)
//       case Right((pipeline, _, _)) => ARC.run(pipeline)(spark, logger, arcContext)
//     }  
//   }  

//   test("MetadataFilterTransform: private=true") {
//     implicit val spark = session
//     import spark.implicits._
//     implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

//     // load csv
//     val extractDataset = spark.read.csv(targetFile)
//     extractDataset.createOrReplaceTempView(inputView)

//     // parse json schema to List[ExtractColumn]
//     val cols = ai.tripl.arc.util.MetadataSchema.parseJsonMetadata(TestUtils.getKnownDatasetMetadataJson)

//     transform.TypingTransform.transform(
//       TypingTransform(
//         name="TypingTransform",
//         description=None,
//         cols=Right(cols.right.getOrElse(null)), 
//         inputView=inputView,
//         outputView=outputView, 
//         params=Map.empty,
//         persist=false,
//         failMode=FailModeTypePermissive,
//         numPartitions=None,
//         partitionBy=Nil           
//       )
//     )

//     val transformed = transform.MetadataFilterTransform.transform(
//       MetadataFilterTransform(
//         name="MetadataFilterTransform", 
//         description=None,
//         inputView=outputView,
//         inputURI=new URI(targetFile),
//         sql=s"SELECT * FROM metadata WHERE metadata.private=false",
//         outputView=outputView,
//         persist=false,
//         sqlParams=Map.empty,
//         params=Map.empty,
//         numPartitions=None,
//         partitionBy=Nil           
//       )
//     ).get

//     val actual = transformed.drop($"_errors")
//     val expected = TestUtils.getKnownDataset.select($"booleanDatum", $"longDatum", $"stringDatum")

//     assert(TestUtils.datasetEquality(expected, actual))
//   }  

//   test("MetadataFilterTransform: securityLevel") {
//     implicit val spark = session
//     import spark.implicits._
//     implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

//     // load csv
//     val extractDataset = spark.read.csv(targetFile)
//     extractDataset.createOrReplaceTempView(inputView)

//     // parse json schema to List[ExtractColumn]
//     val cols = ai.tripl.arc.util.MetadataSchema.parseJsonMetadata(TestUtils.getKnownDatasetMetadataJson)

//     transform.TypingTransform.transform(
//       TypingTransform(
//         name="TypingTransform",
//         description=None,
//         cols=Right(cols.right.getOrElse(null)), 
//         inputView=inputView,
//         outputView=outputView, 
//         params=Map.empty,
//         persist=false,
//         failMode=FailModeTypePermissive,
//         numPartitions=None,
//         partitionBy=Nil           
//       )
//     )

//     val transformed = transform.MetadataFilterTransform.transform(
//       MetadataFilterTransform(
//         name="MetadataFilterTransform", 
//         description=None,
//         inputView=outputView,
//         inputURI=new URI(targetFile),
//         sql=s"SELECT * FROM metadata WHERE metadata.securityLevel <= 4",
//         outputView=outputView,
//         persist=false,
//         sqlParams=Map.empty,
//         params=Map.empty,
//         numPartitions=None,
//         partitionBy=Nil           
//       )
//     ).get

//     val actual = transformed.drop($"_errors")
//     val expected = TestUtils.getKnownDataset.select($"booleanDatum", $"dateDatum", $"decimalDatum", $"longDatum", $"stringDatum")

//     assert(TestUtils.datasetEquality(expected, actual))
//   }   

//   test("MetadataFilterTransform: Structured Streaming") {
//     implicit val spark = session
//     import spark.implicits._
//     implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

//     val dataset = TestUtils.getKnownStringDataset.drop("nullDatum")
//     dataset.createOrReplaceTempView("knownData")

//     val readStream = spark
//       .readStream
//       .format("rate")
//       .option("rowsPerSecond", "1")
//       .load

//     readStream.createOrReplaceTempView("readstream")

//     // cross join to the rate stream purely to register this dataset as a 'streaming' dataset.
//     val input = spark.sql(s"""
//     SELECT knownData.*
//     FROM knownData
//     CROSS JOIN readstream ON true
//     """)

//     input.createOrReplaceTempView(inputView)

//     // parse json schema to List[ExtractColumn]
//     val cols = ai.tripl.arc.util.MetadataSchema.parseJsonMetadata(TestUtils.getKnownDatasetMetadataJson)

//     val transformDataset = transform.TypingTransform.transform(
//       TypingTransform(
//         name="dataset",
//         description=None,
//         cols=Right(cols.right.getOrElse(Nil)), 
//         inputView=inputView,
//         outputView=outputView, 
//         params=Map.empty,
//         persist=false,
//         failMode=FailModeTypePermissive,
//         numPartitions=None,
//         partitionBy=Nil           
//       )
//     ).get

//     val transformed = transform.MetadataFilterTransform.transform(
//       MetadataFilterTransform(
//         name="MetadataFilterTransform", 
//         description=None,
//         inputView=outputView,
//         inputURI=new URI(targetFile),
//         sql=s"SELECT * FROM metadata WHERE metadata.securityLevel <= 4",
//         outputView=outputView,
//         persist=false,
//         sqlParams=Map.empty,
//         params=Map.empty,
//         numPartitions=None,
//         partitionBy=Nil           
//       )
//     ).get

//     val writeStream = transformed
//       .writeStream
//       .queryName("transformed") 
//       .format("memory")
//       .start

//     val df = spark.table("transformed")

//     try {
//       Thread.sleep(2000)
//       assert(df.schema.map(_.name).toSet.equals(Array("booleanDatum", "dateDatum", "decimalDatum", "longDatum", "stringDatum").toSet))
//     } finally {
//       writeStream.stop
//     }
//   }      
// }
