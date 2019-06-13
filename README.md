# multi-query-vertx-jdbc reproducer

Steps to reproduce:
1. Run MS-SQL server in a docker container:
    ```bash
    # docker run -e 'ACCEPT_EULA=Y' -e 'SA_PASSWORD=yourStrong(!)Password' -p 1433:1433 -d mcr.microsoft.com/mssql/server:2017-latest
    ```
2. Launch the app with gradle:
    ```bash
    $ ./gradlew run 
    ```

or by manually running JDBCMultiQueryReproducer from your favorite IDE.