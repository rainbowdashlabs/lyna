package de.chojo.lyna.configuration.elements;


@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class Database {
    private String host = "localhost";
    private String port = "5432";
    private String database = "db";
    private String schema = "lyna";
    private String user = "user";
    private String password = "pw";
    private int poolSize = 5;

    public String host() {
        return host;
    }

    public String port() {
        return port;
    }

    public String database() {
        return database;
    }

    public String schema() {
        return schema;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public int poolSize() {
        return poolSize;
    }
}
