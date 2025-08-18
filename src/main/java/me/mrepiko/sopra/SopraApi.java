package me.mrepiko.sopra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface SopraApi {

    @NotNull
    Connection getConnection(@NotNull String dataSourceId) throws SQLException;

    @NotNull
    Connection getConnection() throws SQLException;

    @Setter
    class Builder {

        private final Logger LOGGER = LoggerFactory.getLogger(Builder.class);
        private final ObjectMapper MAPPER = new ObjectMapper();

        private Class<?> baseClass;
        private Map<String, Config> config = new HashMap<>();
        private Map<String, Object> defaultProperties;

        private Builder(@NotNull Class<?> baseClass) {
            this.baseClass = baseClass;
        }

        @NotNull
        public Builder setDefaultProperties(@NotNull Map<String, Object> defaultProperties) {
            this.defaultProperties = new HashMap<>(defaultProperties);
            return this;
        }

        @NotNull
        public Builder setCredentials(@NotNull List<DatabaseCredentials> credentials) {
            for (DatabaseCredentials credential : credentials) {
                setCredentials(credential);
            }
            return this;
        }

        @NotNull
        public Builder setCredentials(@NotNull DatabaseCredentials credentials) {
            if (credentials.getId() == null || credentials.getId().isEmpty()) {
                throw new IllegalArgumentException("Credentials ID cannot be null or empty");
            }
            Config config = this.config.computeIfAbsent(credentials.getId(), x -> new Config());
            config.syncHikariConfig(credentials);
            return this;
        }

        @NotNull
        public Builder setCredentials(@NotNull ArrayNode arrayNode) {
            for (JsonNode node : arrayNode) {
                if (!node.isObject()) {
                    throw new IllegalArgumentException("Element in provided array node is not an object");
                }
                setCredentials((ObjectNode) node);
            }
            return this;
        }

        @NotNull
        public Builder setCredentials(@NotNull ObjectNode node) {
            String id = (node.has("id")) ? node.get("id").asText() : null;
            if (id == null) {
                throw new IllegalArgumentException("Provided object node has no id provided");
            }
            this.config.computeIfAbsent(id, x -> new Config()).syncHikariConfig(MAPPER.convertValue(node, DatabaseCredentials.class));
            return this;
        }

        @NotNull
        public Builder setServerName(@NotNull String dataSourceId, @NotNull String serverName) {
            this.config.computeIfAbsent(dataSourceId, x -> new Config()).setServerName(serverName);
            return this;
        }

        @NotNull
        public Builder setDatabaseName(@NotNull String dataSourceId, @NotNull String databaseName) {
            this.config.computeIfAbsent(dataSourceId, x -> new Config()).setDatabaseName(databaseName);
            return this;
        }

        @NotNull
        public Builder setUser(@NotNull String dataSourceId, @NotNull String user) {
            this.config.computeIfAbsent(dataSourceId, x -> new Config()).setUser(user);
            return this;
        }

        @NotNull
        public Builder setPassword(@NotNull String dataSourceId, @NotNull String password) {
            this.config.computeIfAbsent(dataSourceId, x -> new Config()).setPassword(password);
            return this;
        }

        @NotNull
        public Builder setPort(@NotNull String dataSourceId, int port) {
            this.config.computeIfAbsent(dataSourceId, x -> new Config()).setPort(port);
            return this;
        }

        @NotNull
        public Builder setDataSourceClassName(@NotNull String className) {
            for (Config config : this.config.values()) {
                config.getHikariConfig().setDataSourceClassName(className);
            }
            return this;
        }

        @NotNull
        public Builder setDataSourceClassName(@NotNull String dataSourceId, @NotNull String className) {
            this.config.computeIfAbsent(dataSourceId, x -> new Config()).getHikariConfig().setDataSourceClassName(className);
            return this;
        }

        @NotNull
        public Builder addDataSourceProperty(@NotNull String propertyName, @NotNull Object value) {
            for (Config config : this.config.values()) {
                config.getHikariConfig().addDataSourceProperty(propertyName, value);
            }
            return this;
        }

        @NotNull
        public Builder addDataSourceProperty(@NotNull String dataSourceId, @NotNull String propertyName, @NotNull Object value) {
            this.config.computeIfAbsent(dataSourceId, x -> new Config()).getHikariConfig().addDataSourceProperty(propertyName, value);
            return this;
        }

        @NotNull
        public static Builder create(@NotNull Class<?> baseClass) {
            return new Builder(baseClass);
        }

        @NotNull
        public SopraApi build() {
            Map<String, HikariDataSource> dataSources = new HashMap<>();
            for (Map.Entry<String, Config> entry : config.entrySet()) {
                String id = entry.getKey();
                Config config = entry.getValue();
                config.setDefaultProperties(defaultProperties);
                dataSources.put(id, new HikariDataSource(config.getHikariConfig()));
                LOGGER.info("Connection established for data source with ID: {}", id);
            }
            return new SopraImpl(dataSources, baseClass);
        }

        @AllArgsConstructor
        @NoArgsConstructor
        @Getter
        @Setter
        private static class Config {
            private String serverName;
            private String databaseName;
            private String user;
            private String password;
            private int port = 3306;
            private HikariConfig hikariConfig;

            public void syncHikariConfig(@NotNull DatabaseCredentials credentials) {
                this.serverName = credentials.getHost();
                this.databaseName = credentials.getDatabaseName();
                this.user = credentials.getUser();
                this.password = credentials.getPassword();
                this.port = credentials.getPort();

                syncHikariConfig();
            }

//            public void syncHikariConfig(@NotNull ObjectNode node) {
//                this.serverName = (node.has("serverName")) ? node.get("serverName").asText() : null;
//                if (this.serverName == null) {
//                    if (node.has("server_name")) {
//                        this.serverName = node.get("server_name").asText();
//                    } else {
//                        throw new IllegalArgumentException("Provided object node has no serverName provided");
//                    }
//                }
//
//                this.databaseName = (node.has("databaseName")) ? node.get("databaseName").asText() : null;
//                if (this.databaseName == null) {
//                    if (node.has("database_name")) {
//                        this.databaseName = node.get("database_name").asText();
//                    } else {
//                        throw new IllegalArgumentException("Provided object node has no databaseName provided");
//                    }
//                }
//
//                this.user = (node.has("user")) ? node.get("user").asText() : null;
//                if (this.user == null) {
//                    if (node.has("user")) {
//                        this.user = node.get("user").asText();
//                    } else {
//                        throw new IllegalArgumentException("Provided object node has no user provided");
//                    }
//                }
//
//                this.password = (node.has("password")) ? node.get("password").asText() : null;
//                if (this.password == null) {
//                    if (node.has("password")) {
//                        this.password = node.get("password").asText();
//                    } else {
//                        throw new IllegalArgumentException("Provided object node has no password provided");
//                    }
//                }
//
//                this.port = (node.has("port")) ? node.get("port").asInt() : 0;
//                if (this.port == 0 && node.has("port")) {
//                    this.port = node.get("port").asInt();
//                } else {
//                    this.port = 3306;
//                }
//
//                syncHikariConfig();
//            }

            private void syncHikariConfig() {
                if (this.hikariConfig == null) {
                    this.hikariConfig = new HikariConfig();
                }
                this.hikariConfig.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
                this.hikariConfig.addDataSourceProperty("serverName", serverName);
                this.hikariConfig.addDataSourceProperty("databaseName", databaseName);
                this.hikariConfig.addDataSourceProperty("user", user);
                this.hikariConfig.addDataSourceProperty("password", password);
                this.hikariConfig.addDataSourceProperty("port", port);
            }

            private void setDefaultProperties(@Nullable Map<String, Object> defaultProperties) {
                if (defaultProperties == null || defaultProperties.isEmpty()) {
                    return;
                }
                if (hikariConfig == null) {
                    hikariConfig = new HikariConfig();
                }
                for (Map.Entry<String, Object> entry : defaultProperties.entrySet()) {
                    hikariConfig.addDataSourceProperty(entry.getKey(), entry.getValue());
                }
            }

        }

    }

}
