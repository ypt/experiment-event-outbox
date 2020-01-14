package com.example.experimenteventoutbox

import io.debezium.config.CommonConnectorConfig
import io.debezium.config.Configuration
import io.debezium.embedded.EmbeddedEngine
import io.debezium.util.Clock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.apache.kafka.connect.data.Struct
import org.apache.kafka.connect.json.JsonConverter
import org.apache.kafka.connect.source.SourceRecord
import org.slf4j.LoggerFactory

class Application

// TODO: clean up config - key constants, external config values
var config: Configuration = Configuration.create()
    // engine properties
    // -----------------

    // Other logic decoding output plugins are available, too
    // decoderbufs and wal2json will need to be installed
    // pgoutput is built into Postgres 10 and above
    // See: https://debezium.io/documentation/reference/1.0/connectors/postgresql.html#overview
    .with("plugin.name", "pgoutput")
    .with(
        EmbeddedEngine.CONNECTOR_CLASS,
        "io.debezium.connector.postgresql.PostgresConnector"
    )

    // Offset storage:
    // FileOffsetBackingStore should be replaced with a remote store for prod usage
    .with(
        EmbeddedEngine.OFFSET_STORAGE,
        "org.apache.kafka.connect.storage.FileOffsetBackingStore"
    )
    .with(
        EmbeddedEngine.OFFSET_STORAGE_FILE_FILENAME,
        "/path/to/storage/offset.dat"
    )
    .with("offset.flush.interval.ms", 60000)

    // connector properties
    // --------------------
    .with("name", "db-connector")
    .with("database.hostname", "0.0.0.0")
    .with("database.port", 5432)
    .with("database.user", "experimenteventoutbox")
    .with("database.password", "experimenteventoutbox")
    .with("database.dbname", "experimenteventoutbox")
    .with("database.server.name", "experimenteventoutbox")

    // TODO: take a closer look at database.history
    .with(
        "database.history",
        "io.debezium.relational.history.FileDatabaseHistory"
    )
    .with(
        "database.history.file.filename",
        "/path/to/storage/dbhistory.dat"
    )
    .with(CommonConnectorConfig.TOMBSTONES_ON_DELETE, false)
    .with("table.whitelist", "public.outbox")
    .build()

fun main(args: Array<String>) {
    ChangeConsumer().run()
}

class ChangeConsumer {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val keyConverter = JsonConverter()
    private val valueConverter = JsonConverter()

    init {
        keyConverter.configure(config.asMap(), true)
        valueConverter.configure(config.asMap(), false)
    }

    fun run() {
        val engine: EmbeddedEngine = EmbeddedEngine.create()
            .using(config)
            .using(this.javaClass.classLoader)
            .using(Clock.SYSTEM)
            .notifying(::handleEvent) // here's where you wire up your own change event handler
            .build()
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        executor.execute(engine)

        Runtime.getRuntime().addShutdownHook(Thread(Runnable {
            logger.info("Requesting embedded engine to shut down")
            engine.stop()
        }))

        // the submitted task keeps running, only no more new ones can be added
        executor.shutdown()

        awaitTermination(executor)

        cleanUp()
    }

    private fun awaitTermination(executor: ExecutorService) {
        try {
            while (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.info("Waiting another 10 seconds for the embedded engine to complete")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun cleanUp() {
        // TODO: gracefully shut down other resources - i.e. message bus connection, etc.
    }

    // Here's where you can implement your own change event handler logic. You'll need to wired it up to EmbeddedEngine.
    private fun handleEvent(record: SourceRecord) {
        // Here's the original SourceRecord
        // --------------------------------

        println("RECORD")
        println(record)
        // SourceRecord{
        //   sourcePartition={server=experimenteventoutbox},
        //   sourceOffset={
        //     lsn_proc=23520760,
        //     lsn=23520760,
        //     txId=591,
        //     ts_usec=1579017839105425
        //   }
        // }
        // ConnectRecord{
        //   topic='experimenteventoutbox.public.outbox',
        //   kafkaPartition=null,
        //   key=Struct{
        //     id=3f9c547c-4b47-4ce3-8fc7-1f734f4301ed
        //   },
        //   keySchema=Schema{experimenteventoutbox.public.outbox.Key:STRUCT},
        //   value=Struct{
        //     after=Struct{
        //       id=3f9c547c-4b47-4ce3-8fc7-1f734f4301ed,
        //       payload=hello world 4
        //     },
        //     source=Struct{
        //       version=1.0.0.Final,
        //       connector=postgresql,
        //       name=experimenteventoutbox,
        //       ts_ms=1579017839105,
        //       db=experimenteventoutbox,
        //       schema=public,
        //       table=outbox,
        //       txId=591,
        //       lsn=23520760
        //     },
        //     op=c,
        //     ts_ms=1579017839155
        //   },
        //   valueSchema=Schema{experimenteventoutbox.public.outbox.Envelope:STRUCT},
        //   timestamp=null,
        //   headers=ConnectHeaders(headers=)
        // }

        // The SourceRecord's key and value data, de-serialized into JSON
        // --------------------------------------------------------------

        val recordKey: ByteArray = keyConverter.fromConnectData("dummy", record.keySchema(), record.key())
        val recordValue: ByteArray = valueConverter.fromConnectData("dummy", record.valueSchema(), record.value())

        // The "key" can be used the record's identifier
        println("RECORD KEY")
        println(String(recordKey))
        // {
        //     "schema": {
        //         "type": "struct",
        //         "fields": [
        //             {
        //                 "type": "string",
        //                 "optional": false,
        //                 "name": "io.debezium.data.Uuid",
        //                 "version": 1,
        //                 "field": "id"
        //             }
        //         ],
        //         "optional": false,
        //         "name": "experimenteventoutbox.public.outbox.Key"
        //     },
        //     "payload": {
        //         "id": "3f9c547c-4b47-4ce3-8fc7-1f734f4301ed"
        //     }
        // }

        // The "value" describes the change event
        println("RECORD VALUE")
        println(String(recordValue))
        // {
        //     "schema": {
        //         "type": "struct",
        //         "fields": [
        //             {
        //                 "type": "struct",
        //                 "fields": [
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "name": "io.debezium.data.Uuid",
        //                         "version": 1,
        //                         "field": "id"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "payload"
        //                     }
        //                 ],
        //                 "optional": true,
        //                 "name": "experimenteventoutbox.public.outbox.Value",
        //                 "field": "before"
        //             },
        //             {
        //                 "type": "struct",
        //                 "fields": [
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "name": "io.debezium.data.Uuid",
        //                         "version": 1,
        //                         "field": "id"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "payload"
        //                     }
        //                 ],
        //                 "optional": true,
        //                 "name": "experimenteventoutbox.public.outbox.Value",
        //                 "field": "after"
        //             },
        //             {
        //                 "type": "struct",
        //                 "fields": [
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "version"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "connector"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "name"
        //                     },
        //                     {
        //                         "type": "int64",
        //                         "optional": false,
        //                         "field": "ts_ms"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": true,
        //                         "name": "io.debezium.data.Enum",
        //                         "version": 1,
        //                         "parameters": {
        //                             "allowed": "true,last,false"
        //                         },
        //                         "default": "false",
        //                         "field": "snapshot"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "db"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "schema"
        //                     },
        //                     {
        //                         "type": "string",
        //                         "optional": false,
        //                         "field": "table"
        //                     },
        //                     {
        //                         "type": "int64",
        //                         "optional": true,
        //                         "field": "txId"
        //                     },
        //                     {
        //                         "type": "int64",
        //                         "optional": true,
        //                         "field": "lsn"
        //                     },
        //                     {
        //                         "type": "int64",
        //                         "optional": true,
        //                         "field": "xmin"
        //                     }
        //                 ],
        //                 "optional": false,
        //                 "name": "io.debezium.connector.postgresql.Source",
        //                 "field": "source"
        //             },
        //             {
        //                 "type": "string",
        //                 "optional": false,
        //                 "field": "op"
        //             },
        //             {
        //                 "type": "int64",
        //                 "optional": true,
        //                 "field": "ts_ms"
        //             }
        //         ],
        //         "optional": false,
        //         "name": "experimenteventoutbox.public.outbox.Envelope"
        //     },
        //     "payload": {
        //         "before": null,
        //         "after": {
        //             "id": "3f9c547c-4b47-4ce3-8fc7-1f734f4301ed",
        //             "payload": "hello world 4"
        //         },
        //         "source": {
        //             "version": "1.0.0.Final",
        //             "connector": "postgresql",
        //             "name": "experimenteventoutbox",
        //             "ts_ms": 1579017839105,
        //             "snapshot": "false",
        //             "db": "experimenteventoutbox",
        //             "schema": "public",
        //             "table": "outbox",
        //             "txId": 591,
        //             "lsn": 23520760,
        //             "xmin": null
        //         },
        //         "op": "c",
        //         "ts_ms": 1579017839155
        //     }
        // }

        // Customizing logic
        // -----------------

        // To further customize logic, you can examine the SourceRecord struct and implement your own data
        // transformations and routing logic. For example, the outbox pattern:
        // https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/
        val value = record.value()
        if (value is Struct) {
            println("OP")
            val op = value.getString("op")
            println(op)
            if (op == "c") { // We only care about the "create" operation
                println("AFTER")
                // NOTE: if field is not present or the wrong type, an exception will be thrown
                val after = value.getStruct("after")
                println(after.getString("id"))

                // TODO: payload column as bytes in db?
                println("PAYLOAD COLUMN")
                val payload = after.getString("payload")
                println(payload)

                // TODO: add other metadata fields to table - namespace, topic, ?
                val topic = "TODO"

                send(topic, payload)
            }
        }

        return
    }

    private fun send(topic: String, payload: String) {
        // TODO: send to message bus
    }
}
