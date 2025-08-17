package me.mrepiko.sopra;

import com.zaxxer.hikari.HikariDataSource;
import me.mrepiko.sopra.annotations.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class SopraImpl implements SopraApi {

    private final Logger logger = LoggerFactory.getLogger(SopraImpl.class);
    private final Map<String, HikariDataSource> dataSources;
    private final Reflections reflections = new Reflections(SopraApi.class.getPackageName());

    protected SopraImpl(@NotNull Map<String, HikariDataSource> dataSources) {
        this.dataSources = new HashMap<>(dataSources);
        setupTables();
    }

    @Override
    public @NotNull Connection getConnection(@NotNull String dataSourceId) throws SQLException {
        if (dataSources.containsKey(dataSourceId)) {
            return dataSources.get(dataSourceId).getConnection();
        }
        throw new IllegalArgumentException("Data source with ID '" + dataSourceId + "' does not exist.");
    }

    @Override
    public @NotNull Connection getConnection() throws SQLException {
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            return entry.getValue().getConnection();
        }
        throw new IllegalStateException("No data sources available");
    }

    private void setupTables() {
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(SopraTable.class);
        Set<Class<?>> handledClasses = new HashSet<>();

        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            Set<Class<?>> dataSourceClasses = classes.stream()
                    .filter(clazz -> {
                        SopraTable annotation = clazz.getAnnotation(SopraTable.class);
                        if (annotation == null) {
                            return false;
                        }
                        return annotation.dataSourceId().equalsIgnoreCase(entry.getKey());
                    })
                    .collect(Collectors.toSet());
            if (dataSourceClasses.isEmpty()) {
                continue;
            }
            setupTable(entry.getValue(), dataSourceClasses);
            handledClasses.addAll(dataSourceClasses);
        }
        warnOfUnhandledClasses(classes, handledClasses);
    }

    private void warnOfUnhandledClasses(@NotNull Set<Class<?>> allClasses, @NotNull Set<Class<?>> handledClasses) {
        Set<Class<?>> unhandledClasses = allClasses.stream()
                .filter(clazz -> !handledClasses.contains(clazz))
                .collect(Collectors.toSet());

        if (!unhandledClasses.isEmpty()) {
            String classNames = unhandledClasses.stream()
                    .map(Class::getName)
                    .collect(Collectors.joining(", "));
            logger.warn("The following classes were not handled by any data source: {}", classNames);
        }
    }

    private void setupTable(@NotNull HikariDataSource source, @NotNull Set<Class<?>> classes) {
        for (Class<?> clazz : classes) {
            SopraTable annotation = clazz.getAnnotation(SopraTable.class);
            if (annotation == null) {
                continue;
            }
            String tableName = annotation.name();
            if (tableName == null || tableName.isEmpty()) {
                tableName = clazz.getSimpleName().toLowerCase();
            }

            StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (\n");
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Transient.class)) {
                    continue;
                }

                Column column = field.getAnnotation(Column.class);
                String columnName = getColumnName(field, annotation, column);

                Default defaultAnnotation = field.getAnnotation(Default.class);
                OnUpdate onUpdate = field.getAnnotation(OnUpdate.class);
                String sqlType = getSqlType(field.getType(), column);

                query.append("  ").append(columnName).append(" ").append(sqlType);
                if (field.isAnnotationPresent(Id.class)) {
                    query.append(" PRIMARY KEY");
                    if (field.isAnnotationPresent(AutoIncrement.class)) {
                        query.append(" AUTO_INCREMENT");
                    }
                }

                if (column != null && !column.nullable()) {
                    query.append(" NOT NULL");
                } else {
                    query.append(" NULL");
                }

                if (field.isAnnotationPresent(Unique.class)) {
                    query.append(" UNIQUE");
                }
                if (defaultAnnotation != null) {
                    query.append(" DEFAULT ").append(defaultAnnotation.value());
                }
                if (onUpdate != null) {
                    query.append(" ON UPDATE ").append(onUpdate.value());
                }

                query.append(",\n");
            }

            PrimaryKey primaryKey = clazz.getAnnotation(PrimaryKey.class);
            if (primaryKey != null) {
                query.append("  PRIMARY KEY (").append(String.join(", ", primaryKey.columns())).append("),\n");
            }

            UniqueConstraint uniqueConstraint = clazz.getAnnotation(UniqueConstraint.class);
            if (uniqueConstraint != null) {
                query.append("  UNIQUE (").append(String.join(", ", uniqueConstraint.columns())).append("),\n");
            }

            query.setLength(query.length() - 2);
            query.append("\n);");
            executeQuery(source, query.toString());
        }
    }

    private static @NotNull String getColumnName(Field field, SopraTable annotation, Column column) {
        boolean snakeCase = annotation.snakeCase();
        String columnName;
        if (column != null) {
            if (column.name().isEmpty()) {
                columnName = field.getName();
            } else {
                columnName = column.name();
            }
        } else {
            columnName = snakeCase ?
                    field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase() :
                    field.getName();
        }
        return columnName;
    }

    private void executeQuery(@NotNull HikariDataSource dataSource, @NotNull String query) {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(query);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query: " + query, e);
        }
    }

    private String getSqlType(@NotNull Class<?> type, @Nullable Column column) {
        if (type == int.class || type == Integer.class) {
            return "INT";
        }
        if (type == long.class || type == Long.class) {
            return "BIGINT";
        }
        if (type == short.class || type == Short.class) {
            return "SMALLINT";
        }
        if (type == byte.class || type == Byte.class) {
            return "TINYINT";
        }
        if (type == double.class || type == Double.class) {
            return "DOUBLE";
        }
        if (type == float.class || type == Float.class) {
            return "FLOAT";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "BOOLEAN";
        }
        if (type == Timestamp.class) {
            return "TIMESTAMP";
        }
        if (type == LocalDateTime.class) {
            return "DATETIME";
        }
        if (type == LocalDate.class) {
            return "DATE";
        }
        if (type == LocalTime.class) {
            return "TIME";
        }
        if (type == UUID.class) {
            return "CHAR(36)";
        }
        if (type.isEnum()) {
            return "VARCHAR(50)";
        }
        if (type == byte[].class) {
            return "BLOB";
        }
        if (type == String.class) {
            return "VARCHAR(" + (column != null ? column.length() : 255) + ")";
        }
        return "LONGTEXT";
    }


}
