package io.github.Sammers.vertx.reporoducer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class JDBCMultiQueryReproducer {

    private static final Logger log = LoggerFactory.getLogger(JDBCMultiQueryReproducer.class);

    public static void main(String[] args) throws InterruptedException {
        log.info("Welcome to the reproducer");
        JDBCClient client = initJdbcClient();
        initDb(client);
        reproduce(client);
    }

    private static void reproduce(JDBCClient client) {
        String sql = "\tDECLARE @INSERT_RES table(id int);\n" +
                "\tinsert into dbo.Person(name)  OUTPUT inserted.id INTO @INSERT_RES values (?);\n" +
                "\tselect id from @INSERT_RES";
        JsonArray sqlArgs = new JsonArray().add("Pavel");

        Future<SQLConnection> connectionFuture = Future.future();
        client.getConnection(connectionFuture);
        connectionFuture.compose(sqlConnection -> {
            Future<ResultSet> resultSetFuture = Future.future();
            log.info("Executing:\n\n{}\n\nwith args:{}", sql, sqlArgs.encodePrettily());
            sqlConnection.queryWithParams(sql, sqlArgs, resultSetFuture);
            return resultSetFuture;
        }).setHandler(event -> {
            if (event.succeeded()) {
                log.info("The query has been executed.");
                ResultSet result = event.result();
                if (result == null) {
                    log.error("REPRODUCED: Unable to obtain select results");
                    Objects.requireNonNull(result);
                } else {
                    Integer id = result.getRows().get(0).getInteger("id");
                    log.info("New person id:{}", id);
                }
            } else {
                log.error("Sql execution error", event.cause());
            }
        });
    }

    private static JDBCClient initJdbcClient() {
        JsonObject config = new JsonObject();
        config.put("driver_class", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .put("url", "jdbc:sqlserver://localhost:1433;database=master")
                .put("user", "sa")
                .put("password", "yourStrong(!)Password");
        return JDBCClient.createNonShared(Vertx.vertx(), config);
    }

    private static void initDb(JDBCClient client) throws InterruptedException {
        CountDownLatch cdl = new CountDownLatch(1);
        client.getConnection(ar -> {
            if (ar.succeeded()) {
                SQLConnection result = ar.result();
                String dbCreationSQL = "if not exists (select * from sysobjects where name='Person' and xtype='U')\n" +
                        "CREATE TABLE dbo.Person\n" +
                        "(\n" +
                        "    id   INT PRIMARY KEY,\n" +
                        "    name text\n" +
                        ")";
                result.query(dbCreationSQL, event -> {
                    if (event.succeeded()) {
                        result.commit(commit -> {
                            if (commit.succeeded()) {
                                log.info("DB has been initialized");
                                cdl.countDown();
                            } else {
                                log.error("Commit error", commit.cause());
                            }
                        });
                    } else {
                        log.error("DB creation error", event.cause());
                    }
                });
            } else {
                log.error("DB init error:", ar.cause());
            }
        });
        cdl.await();
    }
}
