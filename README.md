# experiment-event-outbox
An experiment with [Debezium](https://debezium.io/) change data capture from Postgres for an event [outbox](https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/).

Run everything
```
# TODO: dockerize the service. Until then, build like this
$ ./gradlew build
$ java -jar build/libs/experiment-event-outbox-0.0.1-SNAPSHOT.jar

# Bring the system up
# Until other components are actually dockerized, this is just Postgres
$ docker-compose up --build
```

Insert some data into the database
```
# Start a psql cli session
$ docker exec -it experiment-event-outbox_db_1 psql experimenteventoutbox experimenteventoutbox

# Insert some data
> insert into outbox (payload) values('hello world');
```

Take a look at the output from experiment-event-outbox to see what the cdc data looks like.

## Notes
### Adapted from
- https://debezium.io/documentation/reference/1.0/operations/embedded.html
- https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/
- https://github.com/debezium/debezium-examples/blob/master/apache-pulsar/src/main/java/io/debezium/examples/apache/pulsar/PulsarProducer.java

### Configuring Postgres
https://debezium.io/documentation/reference/1.0/connectors/postgresql.html

Configure the replication slot

`postgresql.conf`
```
# REPLICATION
wal_level = logical
max_wal_senders = 1       
max_replication_slots = 1
```

(The below is optional. Not necessary locally nor with super user) Configure the PostgreSQL server to allow replication 
to take place between the server machine and the host on which the Debezium PostgreSQL connector is running.

`pg_hba.conf`
```
local   replication     experimenteventoutbox                          trust
host    replication     experimenteventoutbox  127.0.0.1/32            trust
host    replication     experimenteventoutbox  ::1/128                 trust
```

## Questions
1. If we _just_ want an event outbox, can we get by with a service that polls the outbox table (vs wiring up everything
   necessary for cdc)? Postgres LISTEN / NOTIFY can also be considered.
1. Secondary fail-over scenarios - what if messages published to message bus are ahead of secondary
1. Slot maintenance - need to remove unused slots so Postgres can clean up old WAL
1. Initial outbox service startup with events already written to outbox table
