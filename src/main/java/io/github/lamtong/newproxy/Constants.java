package io.github.lamtong.newproxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Constants used when create dynamic class and instances in {@link ProxyGenerator}.
 *
 * @author Lam Tong
 * @version 0.0.1
 * @see ProxyGenerator
 * @since 0.0.1
 */
public final class Constants {

    /**
     * signature for {@link Method} type instance variable in String format
     */
    public static final String SIGNATURE_METHOD = "Ljava/lang/reflect/Method;";

    /**
     * signature for {@link Class} type instance variable in String format
     */
    public static final String SIGNATURE_CLASS = "Ljava/lang/Class;";

    /**
     * signature for {@link InvocationHandler} type instance variable in String format
     */
    public static final String SIGNATURE_INVOCATION_HANDLER = "Ljava/lang/reflect/InvocationHandler;";

    /**
     * full-qualified class name for class {@link Object}
     */
    public static final String CLASS_OBJECT = Object.class.getName();

    /**
     * full-qualified class name for class {@link Class}
     */
    public static final String CLASS_CLASS = Class.class.getName();

    /**
     * full-qualified class name for class {@link NoSuchMethodError}
     */
    public static final String CLASS_NO_SUCH_METHOD_ERROR = NoSuchMethodError.class.getName();

    /**
     * full-qualified class name for class {@link NoSuchMethodException}
     */
    public static final String CLASS_NO_SUCH_METHOD_EXCEPTION = NoSuchMethodException.class.getName();

    /**
     * full-qualified class name for class {@link NoClassDefFoundError}
     */
    public static final String CLASS_NO_CLASS_DEF_FOUND_ERROR = NoClassDefFoundError.class.getName();

    /**
     * full-qualified class name for class {@link ClassNotFoundException}
     */
    public static final String Class_CLASS_NOT_FOUND_EXCEPTION = ClassNotFoundException.class.getName();

    /**
     * full-qualified class name for class {@link UndeclaredThrowableException}
     */
    public static final String CLASS_UNDECLARED_THROWABLE_EXCEPTION = UndeclaredThrowableException.class.getName();

    /**
     * full-qualified class name for class {@link Throwable}
     */
    public static final String CLASS_THROWABLE = Throwable.class.getName();

    /**
     * method name for {@link Object#equals(Object)} in string format

     /**
     * method name for {@link Exception#getMessage()} in string format
     */
    public static final String METHOD_GET_MESSAGE = "getMessage";

    /**
     * method name for constructing an instance in string format
     */
    public static final String METHOD_INIT = "<init>";

    /**
     * method name for initialization of class-level when a class is loaded in string format
     */
    public static final String METHOD_CL_INIT = "<clinit>";

    /**
     * method name for {@link InvocationHandler#invoke(Object, Method, Object[])} in string format
     */
    public static final String METHOD_INVOKE = "invoke";

    /**
     * method name for {@link Class#forName(String)} in string format
     */
    public static final String METHOD_FOR_NAME = "forName";

    /**
     * method name for {@link Class#getMethod(String, Class[])} in string format
     */
    public static final String METHOD_GET_METHOD = "getMethod";

    /**
     * field name in generated dynamic proxy class which represents an {@link InvocationHandler} instance
     */
    public static final String FIELD_HANDLER = "handler";

    /**
     * {@code StackMapTable} when create dynamic class proxy which needs to process exception
     */
    public static final String STACK_MAP_TABLE = "StackMapTable";

    /**
     * name of static method for all wrapper class for primitive types
     */
    public static final String METHOD_VALUE_OF = "valueOf";

}
