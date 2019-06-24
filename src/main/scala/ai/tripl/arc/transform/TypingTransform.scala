// package ai.tripl.arc.transform

// import java.lang._
// import scala.collection.JavaConverters._

// import org.apache.spark.sql._
// import org.apache.spark.storage.StorageLevel

// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util._

// object TypingTransform {

//   def transform(transform: TypingTransform)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger): Option[DataFrame] = {
//     val startTime = System.currentTimeMillis() 
//     val stageDetail = new java.util.HashMap[String, Object]()
//     stageDetail.put("type", transform.getType)
//     stageDetail.put("name", transform.name)
//     for (description <- transform.description) {
//       stageDetail.put("description", description)    
//     }    
//     stageDetail.put("inputView", transform.inputView)   
//     stageDetail.put("outputView", transform.outputView)   
//     stageDetail.put("persist", Boolean.valueOf(transform.persist))
//     stageDetail.put("failMode", transform.failMode.sparkString)

//     logger.info()
//       .field("event", "enter")
//       .map("stage", stageDetail)      
//       .log()   
 
//     val cols = transform.cols match {
//       case Right(cols) => {
//         cols match {
//           case Nil => throw new Exception(s"""TypingTransform requires an input schema to define how to transform data but the provided schema has 0 columns.""") with DetailException {
//             override val detail = stageDetail          
//           } 
//           case c => c
//         }
//       }
//       case Left(view) => {
//         val parseResult: ai.tripl.arc.util.MetadataSchema.ParseResult = ai.tripl.arc.util.MetadataSchema.parseDataFrameMetadata(spark.table(view))
//         parseResult match {
//           case Right(cols) => cols
//           case Left(errors) => throw new Exception(s"""Schema view '${view}' to cannot be parsed as it has errors: ${errors.mkString(", ")}.""") with DetailException {
//             override val detail = stageDetail          
//           }  
//         }
//       }
//     }
//     stageDetail.put("columns", cols.map(_.name).asJava)

//     val df = spark.table(transform.inputView)

//     // get schema length filtering out any internal fields
//     val inputColumnCount = df.schema.filter(row => { 
//       !row.metadata.contains("internal") || (row.metadata.contains("internal") && row.metadata.getBoolean("internal") == false) 
//     }).length

//     if (inputColumnCount != cols.length) {
//       stageDetail.put("schemaColumnCount", Integer.valueOf(cols.length))
//       stageDetail.put("inputColumnCount", Integer.valueOf(inputColumnCount))

//       throw new Exception(s"TypingTransform can only be performed on tables with the same number of columns, but the schema has ${cols.length} columns and the data table has ${inputColumnCount} columns.") with DetailException {
//         override val detail = stageDetail          
//       }    
//     }

//     // initialise statistics accumulators or reset if they exist
//     val valueAccumulator = spark.sparkContext.longAccumulator
//     val errorAccumulator = spark.sparkContext.longAccumulator

//     val transformedDF = try {
//       Typing.typeDataFrame(df, cols, transform.failMode, valueAccumulator, errorAccumulator)
//     } catch {
//       case e: Exception => throw new Exception(e) with DetailException {
//         override val detail = stageDetail          
//       }      
//     }  

//     // repartition to distribute rows evenly
//     val repartitionedDF = transform.partitionBy match {
//       case Nil => { 
//         transform.numPartitions match {
//           case Some(numPartitions) => transformedDF.repartition(numPartitions)
//           case None => transformedDF
//         }   
//       }
//       case partitionBy => {
//         // create a column array for repartitioning
//         val partitionCols = partitionBy.map(col => transformedDF(col))
//         transform.numPartitions match {
//           case Some(numPartitions) => transformedDF.repartition(numPartitions, partitionCols:_*)
//           case None => transformedDF.repartition(partitionCols:_*)
//         }
//       }
//     } 

//     repartitionedDF.createOrReplaceTempView(transform.outputView)

//     if (!repartitionedDF.isStreaming) {
//       stageDetail.put("outputColumns", Integer.valueOf(repartitionedDF.schema.length))
//       stageDetail.put("numPartitions", Integer.valueOf(repartitionedDF.rdd.partitions.length))

//       if (transform.persist) {
//         repartitionedDF.persist(StorageLevel.MEMORY_AND_DISK_SER)
//         stageDetail.put("records", Long.valueOf(repartitionedDF.count)) 
//         stageDetail.put("values", Long.valueOf(valueAccumulator.value))
//         stageDetail.put("errors", Long.valueOf(errorAccumulator.value))            
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
