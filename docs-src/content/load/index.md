---
title: Load
weight: 40
type: blog
---

`*Load` stages write out Spark `datasets` to a database or file system.

`*Load` staages should meet this criteria:

- Take in a single `dataset`.
- Perform target specific validation that the dataset has been written correctly.

## AvroLoad

The `AvroLoad` writes an input `DataFrame` to a target [Apache Avro](https://avro.apache.org/) file. 

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|outputURI|URI|true|URI of the Parquet file to write to.|
|numPartitions|Integer|false|{{< readfile file="/content/partials/fields/numPartitions.md" markdown="true" >}}|
|partitionBy|Array[String]|false|{{< readfile file="/content/partials/fields/partitionBy.md" markdown="true" >}}|
|authentication|Map[String, String]|false|{{< readfile file="/content/partials/fields/authentication.md" markdown="true" >}}|
|saveMode|String|false|{{< readfile file="/content/partials/fields/saveMode.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "AvroLoad",
    "name": "write customer records to avro",
    "environments": ["production", "test"],
    "inputView": "customer",
    "outputURI": "hdfs://datalake/raw/customer.avro",
    "numPartitions": 100,
    "partitionBy": [
        "customer_segment",
        "customer_type"
    ],
    "authentication": {
        ...
    },    
    "saveMode": "Append",
    "params": {}
}
```

## AzureEventHubsLoad

The `AzureEventHubsLoad` writes an input `DataFrame` to a target [Azure Event Hubs](https://azure.microsoft.com/en-gb/services/event-hubs/) stream. The input to this stage needs to be a single column dataset of signature `value: string` and is intended to be used after a [JSONTransform](/load/#jsontransform) stage which would prepare the data for sending to the external server.

In the future additional Transform stages (like `ProtoBufTransform`) could be added to prepare binary payloads instead of just `json` `string`.

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|namespaceName|String|true|{{< readfile file="/content/partials/fields/namespaceName.md" markdown="true" >}}|
|eventHubName|String|true|{{< readfile file="/content/partials/fields/eventHubName.md" markdown="true" >}}|
|sharedAccessSignatureKeyName|String|true|{{< readfile file="/content/partials/fields/sharedAccessSignatureKeyName.md" markdown="true" >}}|
|sharedAccessSignatureKey|String|true|{{< readfile file="/content/partials/fields/sharedAccessSignatureKey.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "AzureEventHubsLoad",
    "name": "write customer records to eventhub",
    "environments": ["production", "test"],
    "inputView": "customer",
    "namespaceName": "mynamespace", 
    "eventHubName": "myeventhub", 
    "sharedAccessSignatureKeyName": "mysignaturename", 
    "sharedAccessSignatureKey": "ctzMq410TV3wS7upTBcunJTDLEJwMAZuFPfr0mrrA08=",
    "params": {}
}
```

## DelimitedLoad

The `DelimitedLoad` writes an input `DataFrame` to a target delimited file. 

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|outputURI|URI|true|URI of the Delimited file to write to.|
|authentication|Map[String, String]|false|{{< readfile file="/content/partials/fields/authentication.md" markdown="true" >}}|
|partitionBy|Array[String]|false|{{< readfile file="/content/partials/fields/partitionBy.md" markdown="true" >}}|
|numPartitions|Integer|false|{{< readfile file="/content/partials/fields/numPartitions.md" markdown="true" >}}|
|delimiter|String|true|The type of delimiter in the file. Supported values: `Comma`, `Pipe`, `DefaultHive`. `DefaultHive` is  ASCII character 1, the default delimiter for Apache Hive extracts.|
|quote|String|true|The type of quoting in the file. Supported values: `None`, `SingleQuote`, `DoubleQuote`.|
|header|Boolean|true|Whether or not the dataset contains a header row. If available the output dataset will have named columns otherwise columns will be named `_col1`, `_col2` ... `_colN`.|
|saveMode|String|false|{{< readfile file="/content/partials/fields/saveMode.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "DelimitedLoad",
    "name": "write out customer csvs",
    "environments": ["production", "test"],
    "inputView": "customer",            
    "outputURI": "hdfs://input_data/customer/customer.csv",
    "delimiter": "Comma",
    "quote" : "DoubleQuote",
    "header": true,
    "partitionBy": ["active"],
    "numPartitions": 10,
    "authentication": {
        ...
    },
    "saveMode": "Append",
    "params": {
    }
}
```

## HTTPLoad

The `HTTPLoad` takes an input `DataFrame` and executes a series of `POST` requests against a remote HTTP service. The input to this stage needs to be a single column dataset of signature `value: string` and is intended to be used after a [JSONTransform](/load/#jsontransform) stage which would prepare the data for sending to the external server.

In the future additional Transform stages (like `ProtoBufTransform`) could be added to prepare binary payloads instead of just `json` `string`.

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|outputURI|URI|true|URI of the HTTP server.|
|headers|Map[String, String]|false|{{< readfile file="/content/partials/fields/headers.md" markdown="true" >}}|
|validStatusCodes|Array[Integer]|false|{{< readfile file="/content/partials/fields/validStatusCodes.md" markdown="true" >}} Note: all request response codes must be contained in this list for the stage to be successful.|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "HTTPLoad",
    "name": "load customers to the customer api",
    "environments": ["production", "test"],
    "inputView": "customer",            
    "outputURI": "http://internalserver/api/customer",
    "headers": {
        "Authorization": "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
        "custom-header": "payload",
    },
    "validStatusCodes": [200],
    "params": {
    }
}
```

## JDBCLoad

The `JDBCLoad` writes an input `DataFrame` to a target JDBC Database. See [Spark JDBC documentation](https://spark.apache.org/docs/latest/sql-programming-guide.html#jdbc-to-other-databases).

Whilst it is possible to use `JDBCLoad` to create tables directly in the target database Spark only has a limited knowledge of the schema required in the destination database and so will translate things like `StringType` internally to a `TEXT` type in the target database (because internally Spark does not have limited length strings). The recommendation is to use a preceding [JDBCExecute](../execute/#jdbcexecute) to execute a `CREATE TABLE` statement which creates the intended schema then inserting into that table with `saveMode` set to `Append`.

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|jdbcURL|String|true|{{< readfile file="/content/partials/fields/jdbcURL.md" markdown="true" >}}|
|tableName|String|true|{{< readfile file="/content/partials/fields/tableName.md" markdown="true" >}}|
|numPartitions|Integer|false|{{< readfile file="/content/partials/fields/numPartitions.md" markdown="true" >}} This also determines the maximum number of concurrent JDBC connections.|
|isolationLevel|String|false|{{< readfile file="/content/partials/fields/isolationLevel.md" markdown="true" >}}|
|batchsize|Integer|false|{{< readfile file="/content/partials/fields/batchsize.md" markdown="true" >}}|
|truncate|Boolean|false|{{< readfile file="/content/partials/fields/truncate.md" markdown="true" >}}|
|createTableOptions|String|false|{{< readfile file="/content/partials/fields/createTableOptions.md" markdown="true" >}}|
|createTableColumnTypes|String|false|{{< readfile file="/content/partials/fields/createTableColumnTypes.md" markdown="true" >}}|
|saveMode|String|false|{{< readfile file="/content/partials/fields/saveMode.md" markdown="true" >}}|
|bulkload|Boolean|false|{{< readfile file="/content/partials/fields/bulkload.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}}. Currently requires `user` and `password` to be set here - see example below.|

### Examples

```json
{
    "type": "JDBCLoad",
    "name": "load active customers to web server database",
    "environments": ["production", "test"],
    "inputView": "ative_customers",            
    "jdbcURL": "jdbc:mysql://localhost/mydb",
    "tableName": "mydatabase.myschema.customers",
    "numPartitions": 10,
    "isolationLevel": "READ_COMMITTED",
    "batchsize": 10000,
    "truncate": false,
    "saveMode": "Append",
    "bulkload": false,
    "params": {
        "user": "mydbuser",
        "password": "mydbpassword",
    }
}
```

## JSONLoad

The `JSONLoad` writes an input `DataFrame` to a target JSON file. 

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|outputURI|URI|true|URI of the Delimited file to write to.|
|authentication|Map[String, String]|false|{{< readfile file="/content/partials/fields/authentication.md" markdown="true" >}}|
|saveMode|String|false|{{< readfile file="/content/partials/fields/saveMode.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "JSONLoad",
    "name": "load customer json extract",
    "environments": ["production", "test"],
    "outputView": "customer",            
    "outputURI": "hdfs://input_data/customer/customer.json",
    "authentication": {
        ...
    },
    "saveMode": "Append",
    "params": {
    }
}
```

## ORCLoad

The `ORCLoad` writes an input `DataFrame` to a target [Apache ORC](https://orc.apache.org/) file. 

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|outputURI|URI|true|URI of the ORC file to write to.|
|numPartitions|Integer|false|{{< readfile file="/content/partials/fields/numPartitions.md" markdown="true" >}}|
|partitionBy|Array[String]|false|{{< readfile file="/content/partials/fields/partitionBy.md" markdown="true" >}}|
|authentication|Map[String, String]|false|{{< readfile file="/content/partials/fields/authentication.md" markdown="true" >}}|
|saveMode|String|false|{{< readfile file="/content/partials/fields/saveMode.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "ORCLoad",
    "name": "write customer records to orc",
    "environments": ["production", "test"],
    "inputView": "customer",
    "outputURI": "hdfs://datalake/raw/customer.orc",
    "numPartitions": 100,
    "partitionBy": [
        "customer_segment",
        "customer_type"
    ],
    "authentication": {
        ...
    },    
    "saveMode": "Append",
    "params": {}
}
```

## ParquetLoad

The `ParquetLoad` writes an input `DataFrame` to a target [Apache Parquet](https://parquet.apache.org/) file. 

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|outputURI|URI|true|URI of the Parquet file to write to.|
|numPartitions|Integer|false|{{< readfile file="/content/partials/fields/numPartitions.md" markdown="true" >}}|
|partitionBy|Array[String]|false|{{< readfile file="/content/partials/fields/partitionBy.md" markdown="true" >}}|
|authentication|Map[String, String]|false|{{< readfile file="/content/partials/fields/authentication.md" markdown="true" >}}|
|saveMode|String|false|{{< readfile file="/content/partials/fields/saveMode.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "ParquetLoad",
    "name": "write customer records to parquet",
    "environments": ["production", "test"],
    "inputView": "customer",
    "outputURI": "hdfs://datalake/raw/customer.parquet",
    "numPartitions": 100,
    "partitionBy": [
        "customer_segment",
        "customer_type"
    ],
    "authentication": {
        ...
    },    
    "saveMode": "Append",
    "params": {}
}
```

## XMLLoad

The `XMLLoad` writes an input `DataFrame` to a target XML file. 

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|name|String|true|{{< readfile file="/content/partials/fields/stageName.md" markdown="true" >}}|
|environments|Array[String]|true|{{< readfile file="/content/partials/fields/environments.md" markdown="true" >}}|
|inputView|String|true|{{< readfile file="/content/partials/fields/inputView.md" markdown="true" >}}|
|outputURI|URI|true|URI of the Parquet file to write to.|
|numPartitions|Integer|false|{{< readfile file="/content/partials/fields/numPartitions.md" markdown="true" >}}|
|partitionBy|Array[String]|false|{{< readfile file="/content/partials/fields/partitionBy.md" markdown="true" >}}|
|authentication|Map[String, String]|false|{{< readfile file="/content/partials/fields/authentication.md" markdown="true" >}}|
|params|Map[String, String]|true|{{< readfile file="/content/partials/fields/params.md" markdown="true" >}} Currently unused.|

### Examples

```json
{
    "type": "XMLLoad",
    "name": "write customer records to xml",
    "environments": ["production", "test"],
    "inputView": "customer",
    "outputURI": "hdfs://datalake/raw/customer.xml",
    "numPartitions": 100,
    "partitionBy": [
        "customer_segment",
        "customer_type"
    ],
    "authentication": {
        ...
    },    
    "saveMode": "Append",
    "params": {}
}
```