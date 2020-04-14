package ai.tripl.arc.util

import scala.util.{Try, Success, Failure}

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.node._

import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

object MetadataUtils {

  // convertes the schema of an input dataframe into a dataframe [name, nullable, type, metadata]
  def createMetadataDataframe(input: DataFrame)(implicit spark: SparkSession, logger: ai.tripl.arc.util.log.logger.Logger): DataFrame = {
    import spark.implicits._

    // this is a hack but having to create a StructType using union of all metadata maps is not trivial
    val schemaDataframe = spark.sparkContext.parallelize(Seq(input.schema.json)).toDF.as[String]
    val parsedSchema = spark.read.json(schemaDataframe)

    // create schema dataframe
    val schema = parsedSchema.select(explode(col("fields"))).select("col.*")

    // add metadata column if missing
    val output = if (schema.columns.contains("metadata")) {
      schema
    } else {
      schema.withColumn("metadata", typedLit(Map[String, String]()))
    }

    output.cache.count
    output
  }

  // attach metadata by column name name to input dataframe
  // only attach metadata if the column with same name exists
  def setMetadata(input: DataFrame, schema: StructType): DataFrame = {
    // needs to be var not val as we are mutating by overriding columns with metadata attached
    var output = input

    schema.foreach(field => {
      if (output.columns.contains(field.name)) {
        output = output.withColumn(field.name, col(field.name).as(field.name, field.metadata))
      }
    })

    output
  }

  // a helper function to speed up the creation of a metadata formatted file
  def makeMetadataFromDataframe(input: DataFrame): String = {
    val fields = input.schema.map(field => {
      val objectMapper = new ObjectMapper()
      val jsonNodeFactory = new JsonNodeFactory(true)
      val node = jsonNodeFactory.objectNode

      field.dataType match {
        case _: BooleanType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          node.set("type", jsonNodeFactory.textNode("boolean"))

          val trueValuesArray = node.putArray("trueValues")
          trueValuesArray.add("true")

          val falseValuesArray = node.putArray("falseValues")
          falseValuesArray.add("false")

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: DateType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          node.set("type", jsonNodeFactory.textNode("date"))

          val formattersArray = node.putArray("formatters")
          formattersArray.add("uuuu-MM-dd")

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: DecimalType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          val decimalField = field.dataType.asInstanceOf[DecimalType]

          node.set("type", jsonNodeFactory.textNode("decimal"))
          node.set("precision", jsonNodeFactory.numberNode(decimalField.precision))
          node.set("scale", jsonNodeFactory.numberNode(decimalField.scale))

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: DoubleType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          node.set("type", jsonNodeFactory.textNode("double"))

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: IntegerType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          node.set("type", jsonNodeFactory.textNode("integer"))

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: LongType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          node.set("type", jsonNodeFactory.textNode("long"))

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: StringType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          node.set("type", jsonNodeFactory.textNode("string"))

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: TimestampType => {
          node.set("id", jsonNodeFactory.textNode(""))
          node.set("name", jsonNodeFactory.textNode(field.name))
          node.set("description", jsonNodeFactory.textNode(""))

          node.set("type", jsonNodeFactory.textNode("timestamp"))

          val formattersArray = node.putArray("formatters")
          formattersArray.add("uuuu-MM-dd HH:mm:ss")

          node.set("timezoneId", jsonNodeFactory.textNode("UTC"))

          node.set("nullable", jsonNodeFactory.booleanNode(field.nullable))
          node.set("trim", jsonNodeFactory.booleanNode(true))

          val nullableValuesArray = node.putArray("nullableValues")
          nullableValuesArray.add("")
          nullableValuesArray.add("null")

          node.set("metadata", jsonNodeFactory.objectNode())

          Option(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node))
        }
        case _: ArrayType => None
        case _: NullType => None
      }
    })

    s"""[${fields.flatten.mkString(",")}]"""
  }
}

