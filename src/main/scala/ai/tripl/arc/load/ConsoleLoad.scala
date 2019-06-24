// package ai.tripl.arc.load

// import java.net.URI
// import scala.collection.JavaConverters._

// import org.apache.spark.sql._
// import org.apache.spark.sql.types._

// import ai.tripl.arc.api.API._
// import ai.tripl.arc.util._

// object ConsoleLoad {

//   def load(load: ConsoleLoad)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger): Option[DataFrame] = {
//     val startTime = System.currentTimeMillis() 
//     val stageDetail = new java.util.HashMap[String, Object]()
//     stageDetail.put("type", load.getType)
//     stageDetail.put("name", load.name)
//     for (description <- load.description) {
//       stageDetail.put("description", description)    
//     }    
//     stageDetail.put("inputView", load.inputView)  
//     stageDetail.put("outputMode", load.outputMode.sparkString)  

//     logger.info()
//       .field("event", "enter")
//       .map("stage", stageDetail)      
//       .log()    

//     val df = spark.table(load.inputView)   

//     if (!df.isStreaming) {
//       throw new Exception("ConsoleLoad can only be executed in streaming mode.") with DetailException {
//         override val detail = stageDetail          
//       }
//     }      

//     df.writeStream
//         .format("console")
//         .outputMode(load.outputMode.sparkString)
//         .start

//     logger.info()
//       .field("event", "exit")
//       .field("duration", System.currentTimeMillis() - startTime)
//       .map("stage", stageDetail)      
//       .log()

//     Option(df)
//   }
// }