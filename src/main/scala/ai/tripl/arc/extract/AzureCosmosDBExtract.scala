// package ai.tripl.arc.extract

// import java.lang._
// import java.net.URI
// import scala.collection.JavaConverters._

// import org.apache.spark.sql._
// import org.apache.spark.sql.functions._
// import org.apache.spark.sql.types._
// import org.apache.spark.storage.StorageLevel

// import ai.tripl.arc.api._
// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util._

// import com.microsoft.azure.cosmosdb.spark.schema._
// import com.microsoft.azure.cosmosdb.spark._
// import com.microsoft.azure.cosmosdb.spark.config.Config
// import com.microsoft.azure.cosmosdb.spark.streaming._

// object AzureCosmosDBExtract {

//   def extract(extract: AzureCosmosDBExtract)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger, arcContext: ARCContext): Option[DataFrame] = {
//     import spark.implicits._
//     val startTime = System.currentTimeMillis() 
//     val stageDetail = new java.util.HashMap[String, Object]()
//     stageDetail.put("type", extract.getType)
//     stageDetail.put("name", extract.name)
//     for (description <- extract.description) {
//       stageDetail.put("description", description)    
//     }     
//     stageDetail.put("outputView", extract.outputView)  
//     stageDetail.put("persist", Boolean.valueOf(extract.persist))

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

//     // if incoming dataset is empty create empty dataset with a known schema
//     val df = try {
//       if (arcContext.isStreaming) {
//         spark.readStream.format(classOf[CosmosDBSourceProvider].getName).options(extract.config).load()
//       } else {    
//         spark.read.cosmosDB(com.microsoft.azure.cosmosdb.spark.config.Config(extract.config))
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
//           case None => throw new Exception(s"ParquetExtract has produced 0 columns and no schema has been provided to create an empty dataframe.")
//         }
//       } else {
//         df
//       }
//     } catch {
//       case e: Exception => throw new Exception(e.getMessage) with DetailException {
//         override val detail = stageDetail          
//       }      
//     }    

//     // set column metadata if exists
//     val enrichedDF = optionSchema match {
//         case Some(schema) => MetadataUtils.setMetadata(df, schema)
//         case None => df   
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