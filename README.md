# experiment-event-outbox
An experiment with [Debezium](https://debezium.io/) change data capture from
Postgres for an event
[outbox](https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/).

Run everything
```sh
# Bring the system up
# Until other components are actually dockerized, this is just Postgres
docker-compose up --build

# TODO: dockerize the service. Until then, build like this
./gradlew build
java -jar build/libs/experiment-event-outbox-0.0.1-SNAPSHOT.jar
```

Start a psql cli session
```sh
docker exec -it experiment-event-outbox_db_1 psql experimenteventoutbox experimenteventoutbox
```

Insert some data into the database
```sql
# Insert some data
INSERT INTO outbox (payload) VALUES('hello world');
```

Take a look at the output from experiment-event-outbox to see what the cdc data
looks like.

## Change event order and commit visibility
With CDC, the order of change events should reflect the order of when a change
becomes _visible/committed_, instead of when a change was
[inserted but not yet committed](https://www.postgresql.org/docs/9.5/mvcc-intro.html).

Start a second psql session
```sh
$ docker exec -it experiment-event-outbox_db_1 psql experimenteventoutbox experimenteventoutbox
```

In session-1
```sql
BEGIN;
INSERT INTO outbox (payload) VALUES('hello-1');
-- DO NOT COMMIT YET
```

Note that experiment-event-outbox does not print the change event yet.

In session-2
```sql
BEGIN;
INSERT INTO outbox (payload) VALUES('hello-2');
COMMIT;
```

Note that experiment-event-outbox does print this change event.

Now finally commit the transaction from session-1
```sql
COMMIT;
```

Now, the change event is printed. Also note that sequences and timestamps do
_not_ align with order of visibility/commit, but rather the insert order.

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

(For this example repo, the below is optional. Not necessary locally nor with
super user) Configure the PostgreSQL server to allow replication to take place
between the server machine and the host on which the Debezium PostgreSQL
connector is running.

`/var/lib/postgresql/data/pg_hba.conf`
```
local   replication     experimenteventoutbox                          trust
host    replication     experimenteventoutbox  127.0.0.1/32            trust
host    replication     experimenteventoutbox  ::1/128                 trust
```

## Questions
1. If we _just_ want an event outbox, can we get by with a service that polls
   the outbox table (vs wiring up everything necessary for cdc)? *Answer: No.
   Sequence and timestamp ordering may not align with visibility ordering. See
   "Total Order Broadcast" example above.*
1. Is Postgres LISTEN / NOTIFY an option too?
1. Secondary fail-over scenarios - what if messages published to message bus are
   ahead of secondary. *Note: PG 10+ allows setting up replication slots on secondaries.*
1. Slot maintenance - need to remove unused slots so Postgres can clean up old
   WAL
1. Initial outbox service startup with events already written to outbox table.
   *TODO: look into snapshots*
