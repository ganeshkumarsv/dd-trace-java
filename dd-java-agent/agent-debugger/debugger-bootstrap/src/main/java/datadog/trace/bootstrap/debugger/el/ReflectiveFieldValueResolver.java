package datadog.trace.bootstrap.debugger.el;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** A helper class to resolve a reference path using reflection. */
public class ReflectiveFieldValueResolver {
  public static Object resolve(Object target, Class<?> targetType, String fldName) {
    Field fld = getField(targetType, fldName);
    if (fld == null) {
      return Values.UNDEFINED_OBJECT;
    }
    try {
      return Modifier.isStatic(fld.getModifiers()) ? fld.get(null) : fld.get(target);
    } catch (IllegalAccessException | IllegalArgumentException ignored) {
      return Values.UNDEFINED_OBJECT;
    }
  }

  public static Object getFieldValue(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).get(target);
  }

  public static long getFieldValueAsLong(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getLong(target);
  }

  public static int getFieldValueAsInt(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getInt(target);
  }

  public static double getFieldValueAsDouble(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getDouble(target);
  }

  public static float getFieldValueAsFloat(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getFloat(target);
  }

  public static float getFieldValueAsShort(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getShort(target);
  }

  public static char getFieldValueAsChar(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getChar(target);
  }

  public static byte getFieldValueAsByte(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getByte(target);
  }

  public static boolean getFieldValueAsBoolean(Object target, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    return getField(target, fieldName).getBoolean(target);
  }

  private static Field getField(Object target, String name) throws NoSuchFieldException {
    if (target == null) {
      throw new NullPointerException();
    }
    Field field = getField(target.getClass(), name);
    if (field == null) {
      throw new NoSuchFieldException(name);
    }
    return field;
  }

  private static Field getField(Class<?> container, String name) {
    while (container != null) {
      try {
        Field fld = container.getDeclaredField(name);
        fld.setAccessible(true);
        return fld;
      } catch (NoSuchFieldException ignored) {
        container = container.getSuperclass();
      } catch (SecurityException ignored) {
        return null;
      } catch (Exception ignored) {
        // The only other exception allowed here is InaccessibleObjectException but since we compile
        // against JDK 8 we can not use that type in the exception handler
        return null;
      }
    }
    return null;
  }
}
