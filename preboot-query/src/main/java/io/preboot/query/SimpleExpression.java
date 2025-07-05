package io.preboot.query;

import io.preboot.query.exception.PropertyNotFoundException;
import io.preboot.query.exception.TypeConversionException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@Slf4j
public class SimpleExpression implements CriteriaExpression {
    private final String field;
    private final String operator;
    private final Object value;
    private final String paramName; // Can be null for IS NULL/IS NOT NULL operations
    private RelationalPersistentProperty property; // Store the property for type conversion

    public SimpleExpression(String field, String operator, Object value, String paramName) {
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.paramName = paramName;
    }

    @Override
    public String toSql(SqlContext context) {
        String columnRef;

        if (context.propertyResolver().isNestedProperty(field)) {
            String[] parts = field.split("\\.");

            // First try to find by reference alias
            RelationalPersistentProperty property =
                    context.propertyResolver().findPropertyByReferenceAlias(context.entity(), parts[0]);

            // If not found by alias, try direct property
            if (property == null) {
                property = context.entity().getPersistentProperty(parts[0]);
            }

            if (property == null) {
                throw new PropertyNotFoundException("Property not found: " + parts[0]);
            }

            String currentAlias;
            RelationalPersistentEntity<?> currentEntity;

            if (property.isCollectionLike()) {
                // Handle collection case
                currentEntity = context.mappingContext().getRequiredPersistentEntity(property.getActualType());
                currentAlias = parts[0] + "_table"; // Match the alias convention used in joins

                // If we have more parts and this is a collection with AggregateReference
                if (parts.length > 2) {
                    // Find the AggregateReference in the collection entity
                    for (RelationalPersistentProperty prop : currentEntity) {
                        AggregateReference ref = prop.findAnnotation(AggregateReference.class);
                        if (ref != null && ref.alias().equals(parts[1])) {
                            currentEntity = context.mappingContext().getRequiredPersistentEntity(ref.target());
                            currentAlias = ref.alias();
                            property = currentEntity.getPersistentProperty(parts[2]);
                            if (property == null) {
                                throw new PropertyNotFoundException("Property not found: " + field);
                            }
                            this.property = property; // Store for type conversion
                            columnRef = String.format(
                                    "\"%s\".\"%s\"",
                                    currentAlias, property.getColumnName().getReference());
                            return buildOperationClause(columnRef, operator, paramName);
                        }
                    }
                }
            } else if (property.findAnnotation(AggregateReference.class) != null) {
                // Handle aggregate reference case
                AggregateReference reference = property.findAnnotation(AggregateReference.class);
                currentEntity = context.mappingContext().getRequiredPersistentEntity(reference.target());
                currentAlias = reference.alias();
            } else {
                // Handle regular nested property case
                currentEntity = context.mappingContext().getRequiredPersistentEntity(property.getType());
                currentAlias = parts[0];
            }

            // Get the final property from the target entity
            RelationalPersistentProperty finalProperty = currentEntity.getPersistentProperty(parts[parts.length - 1]);
            if (finalProperty == null) {
                throw new PropertyNotFoundException("Property not found: " + field);
            }

            this.property = finalProperty; // Store for type conversion
            columnRef = String.format(
                    "\"%s\".\"%s\"", currentAlias, finalProperty.getColumnName().getReference());
        } else {
            RelationalPersistentProperty property =
                    context.propertyResolver().getPropertyByPath(context.entity(), field);
            this.property = property; // Store for type conversion
            columnRef =
                    String.format("\"base\".\"%s\"", property.getColumnName().getReference());
        }

        return buildOperationClause(columnRef, operator, paramName);
    }

    @Override
    public void addParameters(SqlParameterSource paramSource) {
        if (!isNullOperation(operator) && paramSource instanceof MapSqlParameterSource mapParamSource) {
            if ("BETWEEN".equalsIgnoreCase(operator) || "between".equalsIgnoreCase(operator)) {
                if (value instanceof Object[] values && values.length >= 2) {
                    // Handle BETWEEN with possible type conversion for both values
                    Object fromValue = values[0];
                    Object toValue = values[1];

                    if (property != null) {
                        // Try to convert string values if needed
                        fromValue = convertValueIfNeeded(fromValue, property);
                        toValue = convertValueIfNeeded(toValue, property);
                    }

                    mapParamSource.addValue(paramName + "From", fromValue);
                    mapParamSource.addValue(paramName + "To", toValue);
                } else {
                    throw new IllegalArgumentException("BETWEEN operator requires two values");
                }
            } else if (value instanceof Object[] arr) {
                // Handle arrays (e.g., for IN operator)
                if (property != null) {
                    // Try to convert each array element if needed
                    Object[] convertedArr = new Object[arr.length];
                    for (int i = 0; i < arr.length; i++) {
                        convertedArr[i] = convertValueIfNeeded(arr[i], property);
                    }
                    mapParamSource.addValue(paramName, ArraySqlValue.create(convertedArr));
                } else {
                    mapParamSource.addValue(paramName, ArraySqlValue.create(arr));
                }
            } else {
                // Handle regular single value
                Object paramValue = value;
                if (property != null) {
                    paramValue = convertValueIfNeeded(value, property);
                }
                mapParamSource.addValue(paramName, paramValue);
            }
        }
    }

    /** Attempts to convert a value to the property's type if needed. */
    private Object convertValueIfNeeded(Object value, RelationalPersistentProperty property) {
        // Handle enum values by converting them to their string representation
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }

        // Handle Instant values by converting them to java.sql.Timestamp
        // This ensures proper parameter binding with JDBC drivers and maintains precision
        if (value instanceof Instant) {
            return java.sql.Timestamp.from((Instant) value);
        }

        if (value instanceof String) {
            String stringValue = (String) value;
            Class<?> targetType = property.getType();

            if (isTemporalType(targetType)) {
                try {
                    if (LocalDateTime.class.isAssignableFrom(targetType)) {
                        try {
                            return LocalDateTime.parse(stringValue);
                        } catch (Exception e) {
                            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(stringValue);
                            return zonedDateTime
                                    .withZoneSameLocal(ZoneId.systemDefault())
                                    .toLocalDateTime();
                        }
                    } else if (LocalDate.class.isAssignableFrom(targetType)) {
                        return LocalDate.parse(stringValue);
                    } else if (Instant.class.isAssignableFrom(targetType)) {
                        // Convert string to Instant and then to Timestamp for PostgreSQL compatibility
                        return java.sql.Timestamp.from(Instant.parse(stringValue));
                    }
                } catch (DateTimeParseException e) {
                    log.warn("Failed to parse '{}' as {}: {}", stringValue, targetType.getSimpleName(), e.getMessage());
                    throw new TypeConversionException(
                            String.class,
                            targetType,
                            "Cannot parse '" + stringValue + "' as " + targetType.getSimpleName());
                }
            } else if (Integer.class.isAssignableFrom(targetType) || int.class.equals(targetType)) {
                try {
                    return Integer.valueOf(stringValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse '{}' as Integer: {}", stringValue, e.getMessage());
                    throw new TypeConversionException(
                            String.class, targetType, "Cannot parse '" + stringValue + "' as Integer");
                }
            } else if (Long.class.isAssignableFrom(targetType) || long.class.equals(targetType)) {
                try {
                    return Long.valueOf(stringValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse '{}' as Long: {}", stringValue, e.getMessage());
                    throw new TypeConversionException(
                            String.class, targetType, "Cannot parse '" + stringValue + "' as Long");
                }
            } else if (Short.class.isAssignableFrom(targetType) || short.class.equals(targetType)) {
                try {
                    return Short.valueOf(stringValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse '{}' as Short: {}", stringValue, e.getMessage());
                    throw new TypeConversionException(
                            String.class, targetType, "Cannot parse '" + stringValue + "' as Short");
                }
            } else if (Double.class.isAssignableFrom(targetType) || double.class.equals(targetType)) {
                try {
                    return Double.valueOf(stringValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse '{}' as Double: {}", stringValue, e.getMessage());
                    throw new TypeConversionException(
                            String.class, targetType, "Cannot parse '" + stringValue + "' as Double");
                }
            } else if (Float.class.isAssignableFrom(targetType) || float.class.equals(targetType)) {
                try {
                    return Float.valueOf(stringValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse '{}' as Float: {}", stringValue, e.getMessage());
                    throw new TypeConversionException(
                            String.class, targetType, "Cannot parse '" + stringValue + "' as Float");
                }
            } else if (BigDecimal.class.isAssignableFrom(targetType)) {
                try {
                    return new BigDecimal(stringValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse '{}' as BigDecimal: {}", stringValue, e.getMessage());
                    throw new TypeConversionException(
                            String.class, targetType, "Cannot parse '" + stringValue + "' as BigDecimal");
                }
            } else if (Boolean.class.isAssignableFrom(targetType) || boolean.class.equals(targetType)) {
                if (stringValue.equalsIgnoreCase("true")
                        || stringValue.equals("1")
                        || stringValue.equalsIgnoreCase("yes")
                        || stringValue.equalsIgnoreCase("tak")) {
                    return Boolean.TRUE;
                } else if (stringValue.equalsIgnoreCase("false")
                        || stringValue.equals("0")
                        || stringValue.equalsIgnoreCase("no")
                        || stringValue.equalsIgnoreCase("nie")) {
                    return Boolean.FALSE;
                } else {
                    log.warn("Failed to parse '{}' as Boolean", stringValue);
                    throw new TypeConversionException(
                            String.class, targetType, "Cannot parse '" + stringValue + "' as Boolean");
                }
            }
        }
        return value;
    }

    /** Checks if a class represents a temporal type. */
    private boolean isTemporalType(Class<?> clazz) {
        return LocalDateTime.class.isAssignableFrom(clazz)
                || LocalDate.class.isAssignableFrom(clazz)
                || Instant.class.isAssignableFrom(clazz)
                || Date.class.isAssignableFrom(clazz)
                || java.sql.Timestamp.class.isAssignableFrom(clazz)
                || java.sql.Date.class.isAssignableFrom(clazz);
    }

    private boolean isNullOperation(String operator) {
        return "IS NULL".equals(operator)
                || "IS NOT NULL".equals(operator)
                || "isnull".equals(operator)
                || "isnotnull".equals(operator);
    }

    private String buildOperationClause(String columnRef, String operator, String paramName) {
        if (isNullOperation(operator)) {
            switch (operator) {
                case "IS NULL":
                case "isnull":
                    return columnRef + " IS NULL";
                case "IS NOT NULL":
                case "isnotnull":
                    return columnRef + " IS NOT NULL";
                default:
                    throw new IllegalArgumentException("Unsupported operation for isNullOperation: " + operator);
            }
        }

        String paramPlaceholder = ":" + paramName;

        switch (operator) {
            case "=":
            case "eq":
                return columnRef + " = " + paramPlaceholder;
            case "!=":
            case "neq":
                return columnRef + " != " + paramPlaceholder;
            case "ILIKE":
            case "like":
                return columnRef + " ILIKE :" + paramName;
            case ">":
            case "gt":
                return columnRef + " > " + paramPlaceholder;
            case "<":
            case "lt":
                return columnRef + " < " + paramPlaceholder;
            case ">=":
            case "gte":
                return columnRef + " >= " + paramPlaceholder;
            case "<=":
            case "lte":
                return columnRef + " <= " + paramPlaceholder;
            case "BETWEEN":
            case "between":
                return columnRef + " BETWEEN :" + paramName + "From AND :" + paramName + "To";
            case "IN":
            case "in":
                return columnRef + " = ANY(:" + paramName + ")";
            case "eqic":
                return "LOWER(" + columnRef + ") = LOWER(:" + paramName + ")";
            case "&& ARRAY":
            case "ao":
                return columnRef + " && ARRAY[:" + paramName + "]::text[]";
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operator);
        }
    }
}
