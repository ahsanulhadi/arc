package au.com.agl.arc

import java.net.URI
import java.sql.DriverManager

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter

import org.mortbay.jetty.handler.{AbstractHandler, ContextHandler, ContextHandlerCollection}
import org.mortbay.jetty.{Server, Request, HttpConnection}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.apache.spark.sql._
import org.apache.spark.sql.functions._

import au.com.agl.arc.api._
import au.com.agl.arc.api.API._
import au.com.agl.arc.util.log.LoggerFactory 

import au.com.agl.arc.util._

class HTTPExtractSuite extends FunSuite with BeforeAndAfter {

  class DataHandler extends AbstractHandler {
    val payload = """{"booleanDatum":true,"dateDatum":"2016-12-18","decimalDatum":54.321000000000000000,"doubleDatum":42.4242,"integerDatum":17,"longDatum":1520828868,"stringDatum":"test,breakdelimiter","timeDatum":"12:34:56","timestampDatum":"2017-12-20T21:46:54.000Z"}
      |{"booleanDatum":false,"dateDatum":"2016-12-19","decimalDatum":12.345000000000000000,"doubleDatum":21.2121,"integerDatum":34,"longDatum":1520828123,"stringDatum":"breakdelimiter,test","timeDatum":"23:45:16","timestampDatum":"2017-12-29T17:21:49.000Z"}""".stripMargin

    override def handle(target: String, request: HttpServletRequest, response: HttpServletResponse, dispatch: Int) = {
      response.setContentType("text/html")
      response.setStatus(HttpServletResponse.SC_OK)
      response.getWriter().println(payload)
      HttpConnection.getCurrentConnection().getRequest().setHandled(true) 
    }
  } 

  class EmptyHandler extends AbstractHandler {
    override def handle(target: String, request: HttpServletRequest, response: HttpServletResponse, dispatch: Int) = {
      response.setContentType("text/html")
      response.setStatus(HttpServletResponse.SC_OK)
      HttpConnection.getCurrentConnection().getRequest().setHandled(true) 
    }
  }   

  var session: SparkSession = _  
  val port = 1080
  val server = new Server(port)
  val outputView = "dataset"
  val uri = s"http://localhost:${port}"
  val badUri = s"http://localhost:${port+1}"

  before {
    implicit val spark = SparkSession
                  .builder()
                  .master("local[*]")
                  .config("spark.ui.port", "9999")
                  .appName("Spark ETL Test")
                  .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")

    session = spark
    import spark.implicits._

    // set for deterministic timezone
    spark.conf.set("spark.sql.session.timeZone", "UTC")    

    // register handlers
    val dataContext = new ContextHandler("/data");
    dataContext.setHandler(new DataHandler)
    val emptyContext = new ContextHandler("/empty");
    emptyContext.setHandler(new EmptyHandler)
    val contexts = new ContextHandlerCollection()
    contexts.setHandlers(Array(dataContext, emptyContext));
    server.setHandler(contexts)

    // start http server
    server.start    
  }

  after {
    session.stop
    try {
      server.stop
    } catch {
      case e: Exception =>
    }
  }

  test("HTTPExtract: Can read data") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val extractDataset = extract.HTTPExtract.extract(
      HTTPExtract(
        name=outputView,
        uri=new URI(s"${uri}/data"),
        headers=Map.empty,
        validStatusCodes=None,
        outputView=outputView,
        params=Map.empty,
        persist=false
      )
    )

    val actual = spark.read.json(extractDataset.as[String])

    // JSON does not have DecimalType or TimestampType
    // JSON will silently drop NullType on write
    val expected = TestDataUtils.getKnownDataset
      .withColumn("decimalDatum", $"decimalDatum".cast("double"))
      .withColumn("timestampDatum", from_unixtime(unix_timestamp($"timestampDatum"), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
      .drop($"nullDatum")

    val actualExceptExpectedCount = actual.except(expected).count
    val expectedExceptActualCount = expected.except(actual).count
    if (actualExceptExpectedCount != 0 || expectedExceptActualCount != 0) {
      println("actual")
      actual.show(false)
      println("expected")
      expected.show(false)  
    }
    assert(actual.except(expected).count === 0)
    assert(expected.except(actual).count === 0)
  }    

  test("HTTPExtract: Can handle empty response") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val actual = extract.HTTPExtract.extract(
      HTTPExtract(
        name=outputView,
        uri=new URI(s"${uri}/empty"),
        headers=Map.empty,
        validStatusCodes=None,
        outputView=outputView,
        params=Map.empty,
        persist=false
      )
    )

    assert(actual.first.getString(0) == "")
  }      

  test("HTTPExtract: Throws exception with 404") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val thrown = intercept[Exception with DetailException] {
      extract.HTTPExtract.extract(
        HTTPExtract(
          name=outputView,
          uri=new URI(s"${uri}/fail"),
          validStatusCodes=None,
          headers=Map.empty,
          outputView=outputView,
          params=Map.empty,
          persist=false
        )
      )
    }
    assert(thrown.getMessage == "HTTPExtract expects a response StatusCode in [200, 201, 202] but server responded with 404 (Not Found).")    
  }

  test("HTTPExtract: validStatusCodes") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val thrown = intercept[Exception with DetailException] {
      extract.HTTPExtract.extract(
        HTTPExtract(
          name=outputView,
          uri=new URI(s"${uri}/empty"),
          headers=Map.empty,
          validStatusCodes=Option(List(201)),
          outputView=outputView,
          params=Map.empty,
          persist=false
        )
      )
    }
    assert(thrown.getMessage == "HTTPExtract expects a response StatusCode in [201] but server responded with 200 (OK).")    
  }        

  test("HTTPExtract: broken url throws exception") {
    implicit val spark = session
    import spark.implicits._
    implicit val logger = LoggerFactory.getLogger(spark.sparkContext.applicationId)

    val thrown = intercept[Exception with DetailException] {
      extract.HTTPExtract.extract(
        HTTPExtract(
          name=outputView,
          uri=new URI(badUri),
          headers=Map.empty,
          validStatusCodes=None,
          outputView=outputView,
          params=Map.empty,
          persist=false
        )
      )
    }
    assert(thrown.getMessage.contains("Connection refused"))
  }
       
}