// package ai.tripl.arc.transform

// import java.lang._
// import scala.collection.JavaConverters._

// import org.apache.spark.sql._
// import org.apache.spark.sql.types._
// import org.apache.spark.storage.StorageLevel

// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util._

// object JSONTransform {

//   def transform(transform: JSONTransform)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger): Option[DataFrame] = {
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

//     logger.info()
//       .field("event", "enter")
//       .map("stage", stageDetail)      
//       .log()      
    
//     val df = spark.table(transform.inputView)

//     val dropMap = new java.util.HashMap[String, Object]()

//     // JSON does not need to deal with NullType as it is silenty dropped on write but we want logging to be explicit
//     val nulls = df.schema.filter( _.dataType == NullType).map(_.name)
//     if (!nulls.isEmpty) {
//       dropMap.put("NullType", nulls.asJava)
//     }

//     stageDetail.put("drop", dropMap)   

//     val transformedDF = try {
//       df.toJSON.toDF
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
