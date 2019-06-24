// package ai.tripl.arc.extract

// import java.lang._

// import scala.collection.JavaConverters._

// import org.apache.spark.sql._
// import org.apache.spark.sql.functions._
// import org.apache.spark.sql.types._
// import org.apache.spark.storage.StorageLevel

// import ai.tripl.arc.api._
// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util.ConfigUtils.{ConfigError, Errors}
// import ai.tripl.arc.util._

// object DelimitedExtract {

//   def extract(extract: DelimitedExtract)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger, arcContext: ARCContext): Option[DataFrame] = {
//     import spark.implicits._
//     val startTime = System.currentTimeMillis() 
//     val stageDetail = new java.util.HashMap[String, Object]()
//     stageDetail.put("type", extract.getType)
//     stageDetail.put("name", extract.name)
//     for (description <- extract.description) {
//       stageDetail.put("description", description)    
//     }     
//     stageDetail.put("persist", Boolean.valueOf(extract.persist))
//     stageDetail.put("outputView", extract.outputView)
//     stageDetail.put("contiguousIndex", Boolean.valueOf(extract.contiguousIndex))

//     val options: Map[String, String] = extract.basePath match {
//       case Some(basePath) => Delimited.toSparkOptions(extract.settings) + ("basePath" -> basePath)
//       case None => Delimited.toSparkOptions(extract.settings)
//     }

//     val inputValue = extract.input match {
//       case Left(view) => view
//       case Right(glob) => glob
//     }

//     stageDetail.put("input", inputValue)  
//     stageDetail.put("options", options.asJava)

//     logger.info()
//       .field("event", "enter")
//       .map("stage", stageDetail)      
//       .log()    
    
//     // try to get the schema
//     val optionSchema = try {
//       ExtractUtils.getSchema(extract.cols)(spark, logger)
//     } catch {
//       case e: Exception => throw new Exception(e) with DetailException {
//         override val detail = stageDetail          
//       }      
//     }        

//     val df = try {
//       if (arcContext.isStreaming) {
//         extract.input match {
//           case Right(glob) => {
//             CloudUtils.setHadoopConfiguration(extract.authentication)

//             optionSchema match {
//               case Some(schema) => spark.readStream.options(options).schema(schema).csv(glob)
//               case None => throw new Exception("CSVExtract requires 'schemaURI' or 'schemaView' to be set if Arc is running in streaming mode.")
//             }       
//           }
//           case Left(view) => throw new Exception("CSVExtract does not support the use of 'inputView' if Arc is running in streaming mode.")
//         }
//       } else {      
//         extract.input match {
//           case Right(glob) =>
//             CloudUtils.setHadoopConfiguration(extract.authentication)

//             try {
//               spark.read.options(options).csv(glob)
//             } catch {
//               // the file that is read is empty
//               case e: AnalysisException if (e.getMessage == "Unable to infer schema for CSV. It must be specified manually.;") =>
//                 spark.emptyDataFrame
//               // the file does not exist at all
//               case e: AnalysisException if (e.getMessage.contains("Path does not exist")) =>
//                 spark.emptyDataFrame
//               case e: Exception => throw e
//             }
            
//           case Left(view) => {
//             extract.inputField match {
//               case Some(inputField) => spark.read.options(options).csv(spark.table(view).select(col(inputField).as("value")).as[String])
//               case None => spark.read.options(options).csv(spark.table(view).as[String])
//             }
//           }
//         }   
//       }      
//     } catch { 
//       case e: Exception => throw new Exception(e) with DetailException {
//         override val detail = stageDetail          
//       }    
//     }

//     // if incoming dataset has 0 columns then create empty dataset with correct schema
//     val emptyDataframeHandlerDF = try {
//       if (df.schema.length == 0) {
//         stageDetail.put("records", Integer.valueOf(0))
//         optionSchema match {
//           case Some(schema) => spark.createDataFrame(spark.sparkContext.emptyRDD[Row], schema)
//           case None => throw new Exception(s"DelimitedExtract has produced 0 columns and no schema has been provided to create an empty dataframe.")
//         }
//       } else {
//         df
//       }
//     } catch {
//       case e: Exception => throw new Exception(e.getMessage) with DetailException {
//         override val detail = stageDetail          
//       }      
//     }      

//     // add internal columns data _filename, _index
//     val sourceEnrichedDF = ExtractUtils.addInternalColumns(emptyDataframeHandlerDF, extract.contiguousIndex)

//     // set column metadata if exists
//     val enrichedDF = optionSchema match {
//         case Some(schema) => MetadataUtils.setMetadata(sourceEnrichedDF, schema)
//         case None => sourceEnrichedDF   
//     }
         
//     // repartition to distribute rows evenly
//     val repartitionedDF = extract.partitionBy match {
//       case Nil => { 
//         extract.numPartitions match {
//           case Some(numPartitions) => enrichedDF.repartition(numPartitions)
//           case None => enrichedDF
//         }   
//       }
//       case partitionBy => {
//         // create a column array for repartitioning
//         val partitionCols = partitionBy.map(col => df(col))
//         extract.numPartitions match {
//           case Some(numPartitions) => enrichedDF.repartition(numPartitions, partitionCols:_*)
//           case None => enrichedDF.repartition(partitionCols:_*)
//         }
//       }
//     } 
//     repartitionedDF.createOrReplaceTempView(extract.outputView)

//     if (!repartitionedDF.isStreaming) {
//       stageDetail.put("inputFiles", Integer.valueOf(repartitionedDF.inputFiles.length))
//       stageDetail.put("outputColumns", Integer.valueOf(repartitionedDF.schema.length))
//       stageDetail.put("numPartitions", Integer.valueOf(repartitionedDF.rdd.partitions.length))

//       if (extract.persist) {
//         repartitionedDF.persist(StorageLevel.MEMORY_AND_DISK_SER)
//         stageDetail.put("records", java.lang.Long.valueOf(repartitionedDF.count))
//       }      
//     }

//     logger.info()
//       .field("event", "exit")
//       .field("duration", System.currentTimeMillis() - startTime)
//       .map("stage", stageDetail)      
//       .log()

//     Option(repartitionedDF)
//   }
// }