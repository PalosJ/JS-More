package com.palos.jsmore.system.observation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

final class ReflectionAccessCache {
    private static final ConcurrentHashMap<MethodKey, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<FieldKey, Optional<Field>> FIELDS = new ConcurrentHashMap<>();

    private ReflectionAccessCache() {
    }

    static OptionalDouble invokePercentage(Object target, String methodName) {
        Object value = invoke(target, methodName);
        if (!(value instanceof Number number)) {
            return OptionalDouble.empty();
        }
        double percentage = number.doubleValue();
        if (!Double.isFinite(percentage)) {
            return OptionalDouble.empty();
        }
        if (percentage <= 1.0D) {
            percentage *= 100.0D;
        }
        return OptionalDouble.of(Math.max(0.0D, Math.min(100.0D, percentage)));
    }

    static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    static int cachedMethodCount() {
        return METHODS.size();
    }

    private static Method findMethod(Class<?> owner, String name) {
        return METHODS.computeIfAbsent(
                new MethodKey(owner, name),
                key -> Optional.ofNullable(findMethodUncached(key.owner(), key.name()))
        ).orElse(null);
    }

    private static Field findField(Class<?> owner, String name) {
        return FIELDS.computeIfAbsent(
                new FieldKey(owner, name),
                key -> Optional.ofNullable(findFieldUncached(key.owner(), key.name()))
        ).orElse(null);
    }

    private static Method findMethodUncached(Class<?> owner, String name) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Field findFieldUncached(Class<?> owner, String name) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private record MethodKey(Class<?> owner, String name) {
    }

    private record FieldKey(Class<?> owner, String name) {
    }
}
