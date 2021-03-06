package com.github.therapi.core;

import static com.github.therapi.core.internal.JacksonHelper.getTypeReference;

import java.lang.annotation.Annotation;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.therapi.core.annotation.Default;
import com.github.therapi.core.internal.TypesHelper;

public class StandardParameterIntrospector implements ParameterIntrospector {
    private final ObjectMapper objectMapper;

    public StandardParameterIntrospector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ParameterDefinition> findParameters(Method method, Object owner) {
        List<ParameterDefinition> params = new ArrayList<>();
        for (Parameter p : method.getParameters()) {
            TypeReference typeReference = getTypeReference(p, owner.getClass());
            boolean nullable = isNullable(p, method);
            Supplier<?> defaultValueSupplier = getDefaultValueSupplier(p, typeReference, nullable);
            params.add(new ParameterDefinition(getName(p), nullable, defaultValueSupplier, typeReference));
        }
        return params;
    }

    @Nullable
    protected Supplier<?> getDefaultValueSupplier(Parameter p, TypeReference typeReference, boolean nullable) {
        Default defaultAnnotation = p.getAnnotation(Default.class);
        if (defaultAnnotation == null) {
            return nullable ? () -> null : null;
        }

        if (Default.NULL.equals(defaultAnnotation.value())) {
            return TypesHelper.getDefaultValueSupplier(p.getType());
        }


        String defValue = defaultAnnotation.value();
        // allow enums to be unquoted (quotes added here)
        if (p.getType().isEnum()) {
            if (!defValue.startsWith("\"") && !defValue.startsWith("'")) {
                defValue = "\"" + defValue + "\"";
            }
        }

        String defaultValueStr = defValue;

        if (p.getType().equals(String.class)) {
            return () -> defaultValueStr;
        }

        Supplier<?> result = () -> {
            try {
                return objectMapper.readValue(defaultValueStr, typeReference);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to deserialize default value for parameter "
                        + "'" + p.getName() + "' of " + p.getDeclaringExecutable(), e);
            }
        };

        // fail fast; better to find out right away that the default value can't be deserialized
        result.get();

        return result;
    }

    protected String getName(Parameter p) {
        return p.getName();
    }

    protected boolean isNullable(Parameter p, Method method) {
        if (hasNullableAnnotation(p)) {
            if (p.getType().isPrimitive()) {
                throw new InvalidAnnotationException("Annotation " + Nullable.class.getName()
                        + " may not be applied to primitive " + p.getType()
                        + " parameter '" + p.getName() + "' of method: " + method);
            }

            return true;
        }

        Default defaultAnnontation = p.getAnnotation(Default.class);
        return defaultAnnontation != null && Default.NULL.equals(defaultAnnontation.value());
    }

    private boolean hasNullableAnnotation(Parameter p) {
        for (Annotation a : p.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals("Nullable")) {
                return true;
            }
        }
        return false;
    }
}
