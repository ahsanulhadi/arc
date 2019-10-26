---
date: 2016-03-09T00:11:02+01:00
title: Partials
weight: 90
type: blog
---

## Authentication

The `Authentication` map defines the authentication parameters for connecting to a remote service (e.g. HDFS, Blob Storage, etc.).

### Parameters

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
|method|String|true|A value of `AzureSharedKey`, `AzureSharedAccessSignature`, `AzureDataLakeStorageToken`, `AzureDataLakeStorageGen2AccountKey`, `AzureDataLakeStorageGen2OAuth`, `AmazonAccessKey`, `AmazonAnonymous`, `AmazonIAM`, `GoogleCloudStorageKeyFile` which defines which method should be used to authenticate with the remote service.|
|accountName|String|false*|Required for `AzureSharedKey` and `AzureSharedAccessSignature`.|
|signature|String|false*|Required for `AzureSharedKey`.|
|container|String|false*|Required for `AzureSharedAccessSignature`.|
|token|String|false*|Required for `AzureSharedAccessSignature`.|
|clientID|String|false*|Required for `AzureDataLakeStorageToken`.|
|refreshToken|String|false*|Required for `AzureDataLakeStorageToken`.|
|accountName|String|false*|Required for `AzureDataLakeStorageGen2AccountKey`.|
|accessKey|String|false*|Required for `AzureDataLakeStorageGen2AccountKey`.|
|clientID|String|false*|Required for `AzureDataLakeStorageGen2OAuth`.|
|secret|String|false*|Required for `AzureDataLakeStorageGen2OAuth`.|
|directoryID|String|false*|Required for `AzureDataLakeStorageGen2OAuth`.|
|accessKeyID|String|false*|Required for `AmazonAccessKey`.|
|secretAccessKey|String|false*|Required for `AmazonAccessKey`.|
|accessKeyID|String|false*|Required for `AmazonIAM`.|
|secretAccessKey|String|false*|Required for `AmazonAccessKey`.|
|encryptionAlgorithm|String|false*|The bucket encrpytion algorithm: `SSE-S3`, `SSE-KMS`, `SSE-C`. Optional for `AmazonIAM`.|
|kmsArn|String|false*|The Key Management Service Amazon Resource Name when using `SSE-KMS` encryptionAlgorithm e.g. `arn:aws:kms:us-west-2:111122223333:key/1234abcd-12ab-34cd-56ef-1234567890ab`. Optional for `AmazonIAM`.|
|customKey|String|false*|The key to use when using Customer-Provided Encryption Keys (`SSE-C`). Optional for `AmazonIAM`.|
|endpoint|String|false|Used for setting S3 endpoint for services like `Ceph Object Store` or `Minio`. Optional for `AmazonAccessKey`.|
|sslEnabled|Boolean|false|Used to set whether to use SSL. Optional for `AmazonAccessKey`.|
|projectID|String|false*|Required for `GoogleCloudStorageKeyFile`.|
|keyFilePath|String|false*|Required for `GoogleCloudStorageKeyFile`.|

### Examples

```json
{
    "type": "DelimitedExtract",
    ...
    "authentication": {
        "method": "AzureSharedKey",
        "accountName": "myaccount",
        "signature": "ctzMq410TV3wS7upTBcunJTDLEJwMAZuFPfr0mrrA08=",
    }
    ...
}
```

```json
{
    "type": "DelimitedExtract",
    ...
    "authentication": {
        "method": "AzureSharedAccessSignature",
        "accountName": "myaccount",
        "container": "mycontainer",
        "token": "sv=2015-04-05&st=2015-04-29T22%3A18%3A26Z&se=2015-04-30T02%3A23%3A26Z&sr=b&sp=rw&sip=168.1.5.60-168.1.5.70&spr=https&sig=Z%2FRHIX5Xcg0Mq2rqI3OlWTjEg2tYkboXr1P9ZUXDtkk%3D",
    }
    ...
}
```

```json
{
    "type": "DelimitedExtract",
    ...
    "authentication": {
        "method": "AmazonAccessKey",
        "accessKeyID": "AKIAIOSFODNN7EXAMPLE",
        "secretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        "endpoint": "http://minio:9000"
    }
    ...
}
```

## Environments

The `Environments` list specifies a list of environments under which the stage will be executed. The environments list must contain the value in the `ETL_CONF_ENV` environment variable or `etl.config.environment` `spark-submit` argument for the stage to be executed.

### Examples

If a stage is to be executed in both production and testing and the `ETL_CONF_ENV` environment variable is set to `production` or `test` then the `DelimitedExtract` stage defined here will be executed. If the `ETL_CONF_ENV` environment variable was set to something else like `user_acceptance_testing` then this stage will not be executed and a warning message will be logged.

```json
{
    "type": "DelimitedExtract",
    ...
    "environments": ["production", "test"],
    ...
}
```

A practical use case of this is to execute additional stages in testing which would prevent the job from being automatically deployed to production via [Continuous Delivery](https://en.wikipedia.org/wiki/Continuous_delivery) if it fails:

```json
{
    "type": "ParquetExtract",
    "name": "load the manually verified known good set of data from testing", 
    "environments": ["test"],
    "outputView": "known_correct_dataset",
    ...
},
{
    "type": "EqualityValidate",
    "name": "ensure the business logic produces the same result as the known good set of data from testing", 
    "environments": ["test"],
    "leftView": "newly_caluclated_dataset",
    "rightView": "known_correct_dataset",
    ...
}
```


## User Defined Functions

To help with common data tasks several additional functions have been added to Arc in addition to the inbuilt [Spark SQL Functions](https://spark.apache.org/docs/latest/api/sql/index.html).

### get_json_double_array
##### Since: 1.0.9

Similar to [get_json_object](https://spark.apache.org/docs/latest/api/sql/index.html#get_json_object) - but extracts a json `double` `array` from path.

```sql
SELECT get_json_double_array('[0.1, 1.1]', '$')
```

### get_json_integer_array
##### Since: 1.0.9

Similar to [get_json_object](https://spark.apache.org/docs/latest/api/sql/index.html#get_json_object) - but extracts a json `integer` `array` from path.

```sql
SELECT get_json_integer_array('[1, 2]', '$')
```

### get_json_long_array
##### Since: 1.0.9

Similar to [get_json_object](https://spark.apache.org/docs/latest/api/sql/index.html#get_json_object) - but extracts a json `long` `array` from path.

```sql
SELECT get_json_long_array('[2147483648, 2147483649]', '$')
```