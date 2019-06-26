package ai.tripl.arc

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter

import org.apache.spark.sql._

import ai.tripl.arc.util.log.LoggerFactory
import ai.tripl.arc.util.TestUtils

import ai.tripl.arc.udf.UDF

class UDFSuite extends FunSuite with BeforeAndAfter {

  var session: SparkSession = _
  var logger: ai.tripl.arc.util.log.logger.Logger = _

  before {
    implicit val spark = SparkSession
                  .builder()
                  .master("local[*]")
                  .config("spark.ui.port", "9999")
                  .appName("Spark ETL Test")
                  .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    // set for deterministic timezone
    spark.conf.set("spark.sql.session.timeZone", "UTC")   

    session = spark
    logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)
    val arcContext = TestUtils.getARCContext(isStreaming=false)

    // register udf
    UDF.registerUDFs()(spark, logger, arcContext)
  }

  after {
    session.stop()
  }

  test("jsonPath") {
    implicit val spark = session

    val df = spark.sql("""
    SELECT get_json_long_array('{"nested": {"object": [2147483648, 2147483649]}}', '$.nested.object') AS test
    """)

    assert(df.first.getAs[scala.collection.mutable.WrappedArray[Long]](0)(0) == 2147483648L)
    assert(df.schema.fields(0).dataType.toString == "ArrayType(LongType,false)")
  }       

  test("get_json_double_array") {
    implicit val spark = session

    val df = spark.sql("""
    SELECT get_json_double_array('[0.1, 1.1]', '$') AS test
    """)

    assert(df.first.getAs[scala.collection.mutable.WrappedArray[Double]](0)(0) == 0.1)
    assert(df.schema.fields(0).dataType.toString == "ArrayType(DoubleType,false)")
  }   

  test("get_json_integer_array") {
    implicit val spark = session

    val df = spark.sql("""
    SELECT get_json_integer_array('[1, 2]', '$') AS test
    """)

    assert(df.first.getAs[scala.collection.mutable.WrappedArray[Integer]](0)(0) == 1)
    assert(df.schema.fields(0).dataType.toString == "ArrayType(IntegerType,false)")
  }    

  test("get_json_long_array") {
    implicit val spark = session

    val df = spark.sql("""
    SELECT get_json_long_array('[2147483648, 2147483649]', '$') AS test
    """)

    assert(df.first.getAs[scala.collection.mutable.WrappedArray[Long]](0)(0) == 2147483648L)
    assert(df.schema.fields(0).dataType.toString == "ArrayType(LongType,false)")
  } 

  test("random") {
    implicit val spark = session

    val df = spark.sql("""
    SELECT random() AS test
    """)

    assert(df.first.getDouble(0) > 0.0)
    assert(df.first.getDouble(0) < 1.0)
    assert(df.schema.fields(0).dataType.toString == "DoubleType")
  }       
}