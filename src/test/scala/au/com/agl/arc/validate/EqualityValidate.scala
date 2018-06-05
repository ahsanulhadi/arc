package au.com.agl.arc

import java.net.URI

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter

import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.spark.sql._
import org.apache.spark.sql.functions._

import au.com.agl.arc.api._
import au.com.agl.arc.api.API._
import au.com.agl.arc.util.log.LoggerFactory 
import au.com.agl.arc.util._

import com.fasterxml.jackson.databind._

import au.com.agl.arc.util.TestDataUtils

class EqualityValidateSuite extends FunSuite with BeforeAndAfter {

  var session: SparkSession = _  
  var testName = "EqualityValidate"
  val leftView = "leftViewName"
  val rightView = "rightViewName"
  val objectMapper = new ObjectMapper()

  before {
    implicit val spark = SparkSession
                  .builder()
                  .master("local[*]")
                  .config("spark.ui.port", "9999")
                  .appName("Spark ETL Test")
                  .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    session = spark
  }

  after {
    session.stop()
  }

  test("EqualityValidate") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val dataset = TestDataUtils.getKnownDataset.drop($"nullDatum")
    dataset.createOrReplaceTempView(leftView)
    dataset.createOrReplaceTempView(rightView)

    validate.EqualityValidate.validate(
      EqualityValidate(
        name=testName, 
        leftView=leftView,
        rightView=rightView,
        params=Map.empty
      )
    )


  }  

  test("EqualityValidate: schema") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val dataset = TestDataUtils.getKnownDataset.drop($"nullDatum")
    dataset.createOrReplaceTempView(leftView)
    dataset.drop($"booleanDatum").createOrReplaceTempView(rightView)

    val thrown = intercept[Exception with DetailException] {
    validate.EqualityValidate.validate(
      EqualityValidate(
        name=testName, 
        leftView=leftView,
        rightView=rightView,
        params=Map.empty
      )
    )
    }

    assert(thrown.getMessage === """EqualityValidate ensures the two input datasets are the same, but 'leftViewName' (9 columns) contains columns: ['booleanDatum'] that are not in 'rightViewName' and 'rightViewName' (8 columns) contains columns: [] that are not in 'leftViewName'. Columns are not equal so cannot the data be compared.""")
  }    

  test("EqualityValidate: value") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val dataset = TestDataUtils.getKnownDataset.drop($"nullDatum")
    dataset.createOrReplaceTempView(leftView)
    dataset.withColumn("booleanDatum", lit(true)).createOrReplaceTempView(rightView)

    val thrown = intercept[Exception with DetailException] {
    validate.EqualityValidate.validate(
      EqualityValidate(
        name=testName, 
        leftView=leftView,
        rightView=rightView,
        params=Map.empty
      )
    )
    }

    assert(thrown.getMessage === s"""EqualityValidate ensures the two input datasets are the same, but 'leftViewName' (2 rows) contains 1 rows that are not in 'rightViewName' and 'rightViewName' (2 rows) contains 1 rows which are not in 'leftViewName'.""")
  }   

}