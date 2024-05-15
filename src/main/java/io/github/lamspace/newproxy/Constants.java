/*
 * Copyright 2024 the original author, Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace.newproxy;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Constants used when create dynamic class and instances in {@link ProxyGenerator}.
 *
 * @author Lam Tong
 * @version 1.0.0
 * @since 1.0.0
 */
public final class Constants {

    private Constants() {
    }

    /**
     * signature for {@link MethodDecorator} type instance variable in String format
     */
    public static final String SIGNATURE_METHOD_DECORATOR = "Lio/github/lamspace/newproxy/MethodDecorator;";

    /**
     * signature for {@link Class} type instance variable in String format
     */
    public static final String SIGNATURE_CLASS = "Ljava/lang/Class;";

    /**
     * signature for {@link InvocationInterceptor} type instance variable in String format
     */
    public static final String SIGNATURE_INVOCATION_INTERCEPTOR = "Lio/github/lamspace/newproxy/InvocationInterceptor;";

    /**
     * full-qualified class name for class {@link Object}
     */
    public static final String CLASS_OBJECT = Object.class.getName();

    /**
     * full-qualified class name for class {@link Class}
     */
    public static final String CLASS_CLASS = Class.class.getName();

    /**
     * full-qualified class name for class {@link MethodDecorator}
     */
    public static final String CLASS_METHOD_DECORATOR = MethodDecorator.class.getName();

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
     * full-qualified class name for class {@link InvocationDispatcher}
     */
    public static final String CLASS_INVOCATION_DISPATCHER = InvocationDispatcher.class.getName();

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
     * method name for {@link java.lang.invoke.MethodHandle#invokeExact(Object...)} in string format
     */
    public static final String METHOD_INVOKE_EXACT = "invokeExact";

    /**
     * method name for {@link InvocationInterceptor#intercept(Object, MethodDecorator, Object[])} in string format
     */
    public static final String METHOD_INTERCEPT = "intercept";

    /**
     * method name for {@link Class#forName(String)} in string format
     */
    public static final String METHOD_FOR_NAME = "forName";

    /**
     * method name for {@link Class#getMethod(String, Class[])} in string format
     */
    public static final String METHOD_GET_METHOD = "getMethod";

    /**
     * method name for {@link MethodDecorator#of(Method)} in string format
     */
    public static final String METHOD_OF = "of";

    /**
     * name of static method for all wrapper class for primitive types
     */
    public static final String METHOD_VALUE_OF = "valueOf";

    /**
     * name of {@link Object#equals(Object)} method
     */
    public static final String METHOD_EQUALS = "equals";

    /**
     * name of {@link Object#hashCode()} method
     */
    public static final String METHOD_HASH_CODE = "hashCode";

    /**
     * name of {@link Object#toString()} method
     */
    public static final String METHOD_TO_STRING = "toString";

    /**
     * name of {@link java.lang.invoke.MethodType#methodType(Class) methodType} method
     */
    public static final String METHOD_METHOD_TYPE = "methodType";

    /**
     * name of {@link java.lang.invoke.MethodHandles.Lookup#findVirtual(Class, String, MethodType)} method
     */
    public static final String METHOD_FIND_VIRTUAL = "findVirtual";

    /**
     * name of {@link java.lang.invoke.MethodHandle#bindTo(Object)} method
     */
    public static final String METHOD_BIND_TO = "bindTo";

    /**
     * name of {@link Boolean#booleanValue()} method
     */
    public static final String METHOD_BOOLEAN_VALUE = "booleanValue";

    /**
     * name of {@link Byte#byteValue()} method
     */
    public static final String METHOD_BYTE_VALUE = "byteValue";

    /**
     * name of {@link Character#charValue()} method
     */
    public static final String METHOD_CHAR_VALUE = "charValue";

    /**
     * name of {@link Double#doubleValue()} method
     */
    public static final String METHOD_DOUBLE_VALUE = "doubleValue";

    /**
     * name of {@link Float#floatValue()} method
     */
    public static final String METHOD_FLOAT_VALUE = "floatValue";

    /**
     * name of {@link Integer#intValue()} method
     */
    public static final String METHOD_INT_VALUE = "intValue";

    /**
     * name of {@link Long#longValue()} method
     */
    public static final String METHOD_LONG_VALUE = "longValue";

    /**
     * name of {@link Short#shortValue()} method
     */
    public static final String METHOD_SHORT_VALUE = "shortValue";

    /**
     * name of methods implemented from interface.
     */
    public static final String METHOD_DO_INVOKE = "doInvoke";

    /**
     * field name in generated dynamic proxy class which represents an {@link InvocationInterceptor} instance
     */
    public static final String FIELD_INTERCEPTOR = "interceptor";

    /**
     * static field name for all wrapper class for primitive types
     */
    public static final String FIELD_TYPE = "TYPE";

    /**
     * {@code StackMapTable} when create dynamic class proxy which needs to process exception
     */
    public static final String STACK_MAP_TABLE = "StackMapTable";

    /**
     * flag to indicate whether dump class file or not
     */
    public static final String STRING_DUMP_FLAG = "io.github.lamspace.newproxy.dump.flag";

    /**
     * directory to dump class file
     */
    public static final String STRING_DUMP_DIR = "io.github.lamspace.newproxy.dump.dir";

    /**
     * default directory to dump class file
     */
    public static final String STRING_DUMP_DIR_DEFAULT = "newproxy";

}
