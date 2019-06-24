// package ai.tripl.arc.extract

// import java.lang._
// import java.net.URI
// import java.util.Properties
// import scala.collection.JavaConverters._

// import org.apache.spark.sql._
// import org.apache.spark.sql.functions._
// import org.apache.spark.sql.types._
// import org.apache.spark.storage.StorageLevel

// import ai.tripl.arc.api._
// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util._

// object TextExtract {

//   def extract(extract: TextExtract)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger, arcContext: ARCContext): Option[DataFrame] = {
//     import spark.implicits._
//     val startTime = System.currentTimeMillis() 
//     val stageDetail = new java.util.HashMap[String, Object]()
//     stageDetail.put("type", extract.getType)
//     stageDetail.put("name", extract.name)
//     for (description <- extract.description) {
//       stageDetail.put("description", description)    
//     }      
//     stageDetail.put("input", extract.input)  
//     stageDetail.put("outputView", extract.outputView)  
//     stageDetail.put("persist", Boolean.valueOf(extract.persist))
//     stageDetail.put("contiguousIndex", Boolean.valueOf(extract.contiguousIndex))
//     stageDetail.put("multiLine", Boolean.valueOf(extract.multiLine))

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
//         CloudUtils.setHadoopConfiguration(extract.authentication)

//         optionSchema match {
//           case Some(schema) => spark.readStream.schema(schema).text(extract.input)
//           case None => throw new Exception("JSONExtract requires 'schemaURI' to be set if Arc is running in streaming mode.")
//         }             
//       } else {
//         CloudUtils.setHadoopConfiguration(extract.authentication)
//         // spark does not cope well reading many small files into json directly from hadoop file systems
//         // by reading first as text time drops by ~75%
//         // this will not throw an error for empty directory (but will for missing directory)
//         try {
//           if (extract.multiLine) {
//             extract.basePath match {
//               case Some(basePath) => spark.read.option("mergeSchema", "true").option("basePath", basePath).parquet(extract.input)
//               case None => spark.read.option("wholetext", "true").textFile(extract.input).toDF  
//             }  
//           } else {
//             spark.read.option("wholetext", "false").textFile(extract.input).toDF
//           }
//         } catch {
//           case e: org.apache.spark.sql.AnalysisException if (e.getMessage.contains("Path does not exist")) => {
//             spark.emptyDataFrame
//           }
//           case e: Exception => throw e
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
//           case Some(s) => spark.createDataFrame(spark.sparkContext.emptyRDD[Row], s)
//           case None => throw new Exception(s"TextExtract has produced 0 columns and no schema has been provided to create an empty dataframe.")
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

//     // // set column metadata if exists
//     val enrichedDF = optionSchema match {
//         case Some(schema) => MetadataUtils.setMetadata(sourceEnrichedDF, schema)
//         case None => sourceEnrichedDF   
//     }

//     // repartition to distribute rows evenly
//     val repartitionedDF = extract.numPartitions match {
//       case Some(numPartitions) => enrichedDF.repartition(numPartitions)
//       case None => enrichedDF
//     }   

//     repartitionedDF.createOrReplaceTempView(extract.outputView)

//     if (!repartitionedDF.isStreaming) {
//       stageDetail.put("inputFiles", Integer.valueOf(repartitionedDF.inputFiles.length))
//       stageDetail.put("outputColumns", Integer.valueOf(repartitionedDF.schema.length))
//       stageDetail.put("numPartitions", Integer.valueOf(repartitionedDF.rdd.partitions.length))

//       if (extract.persist) {
//         repartitionedDF.persist(StorageLevel.MEMORY_AND_DISK_SER)
//         stageDetail.put("records", Long.valueOf(repartitionedDF.count)) 
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

