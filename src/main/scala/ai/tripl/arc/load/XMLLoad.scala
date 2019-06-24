// package ai.tripl.arc.load

// import java.net.URI
// import scala.collection.JavaConverters._

// import org.apache.spark.sql._
// import org.apache.spark.sql.types._

// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util._

// object XMLLoad {

//   def load(load: XMLLoad)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger): Option[DataFrame] = {
//     // force com.sun.xml.* implementation for writing xml to be compatible with spark-xml library
//     System.setProperty("javax.xml.stream.XMLOutputFactory", "com.sun.xml.internal.stream.XMLOutputFactoryImpl")

//     val startTime = System.currentTimeMillis() 
//     val stageDetail = new java.util.HashMap[String, Object]()
//     stageDetail.put("type", load.getType)
//     stageDetail.put("name", load.name)
//     for (description <- load.description) {
//       stageDetail.put("description", description)    
//     }    
//     stageDetail.put("inputView", load.inputView)  
//     stageDetail.put("outputURI", load.outputURI.toString)  
//     stageDetail.put("partitionBy", load.partitionBy.asJava)
//     stageDetail.put("saveMode", load.saveMode.toString.toLowerCase)

//     val df = spark.table(load.inputView) 

//     load.numPartitions match {
//       case Some(partitions) => stageDetail.put("numPartitions", Integer.valueOf(partitions))
//       case None => stageDetail.put("numPartitions", Integer.valueOf(df.rdd.getNumPartitions))
//     }

//     logger.info()
//       .field("event", "enter")
//       .map("stage", stageDetail)      
//       .log()


//     // set write permissions
//     CloudUtils.setHadoopConfiguration(load.authentication)
     
//     val dropMap = new java.util.HashMap[String, Object]()

//     // XML does not need to deal with NullType as it is silenty dropped on write but we want logging to be explicit
//     val nulls = df.schema.filter( _.dataType == NullType).map(_.name)
//     if (!nulls.isEmpty) {
//       dropMap.put("NullType", nulls.asJava)
//     }

//     stageDetail.put("drop", dropMap) 

//     val listener = ListenerUtils.addStageCompletedListener(stageDetail)

//     try {
//       load.partitionBy match {
//         case Nil => { 
//           load.numPartitions match {
//             case Some(n) => df.repartition(n).write.format("com.databricks.spark.xml").mode(load.saveMode).save(load.outputURI.toString)
//             case None => df.write.format("com.databricks.spark.xml").mode(load.saveMode).save(load.outputURI.toString)  
//           }   
//         }
//         case partitionBy => {
//           // create a column array for repartitioning
//           val partitionCols = partitionBy.map(col => df(col))
//           load.numPartitions match {
//             case Some(n) => df.repartition(n, partitionCols:_*).write.format("com.databricks.spark.xml").partitionBy(partitionBy:_*).mode(load.saveMode).save(load.outputURI.toString)
//             case None => df.repartition(partitionCols:_*).write.format("com.databricks.spark.xml").partitionBy(partitionBy:_*).mode(load.saveMode).save(load.outputURI.toString)
//           }   
//         }
//       }    
//     } catch {
//       case e: Exception => throw new Exception(e) with DetailException {
//         override val detail = stageDetail          
//       }      
//     }        

//     spark.sparkContext.removeSparkListener(listener)           

//     logger.info()
//       .field("event", "exit")
//       .field("duration", System.currentTimeMillis() - startTime)
//       .map("stage", stageDetail)      
//       .log()

//     Option(df)
//   }
// }