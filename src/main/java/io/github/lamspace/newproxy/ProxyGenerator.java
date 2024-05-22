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

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.*;
import com.sun.org.apache.bcel.internal.generic.*;
import com.sun.xml.internal.ws.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;
import java.util.stream.Collectors;

import static io.github.lamspace.newproxy.Constants.*;

/**
 * {@link ProxyGenerator} is the core of {@link NewProxy}. It manipulates Java Class files in binary with format
 * in an array of bytes. {@link ProxyGenerator} generates dynamic proxy class via
 * <a href="https://commons.apache.org/proper/commons-bcel/">BCEL</a> which aims to analyze, create and manipulate
 * Java Class files. But {@link ProxyGenerator} does not need to add extra dependency about {@code BCEL} since
 * {@code JDK} has the core of {@code BCEL} and that is enough to manipulate Java Class files in binary.<br/><br/>
 *
 * <h3>Dynamic Proxy Class Format</h3>
 * To create a dynamic proxy class for interface {@code Foo}, {@link ProxyGenerator} would generate a proxy class as
 * below:
 * <blockquote><pre>
 * public interface Foo {
 *     void foo();
 * }
 * </pre></blockquote>
 * <blockquote><pre>
 * public final class $NewProxy0 implements Foo, InvocationDispatcher {
 *     private static final MethodDecorator m0;
 *     private static final MethodDecorator m1;
 *     private static final MethodDecorator m2;
 *     private static final MethodDecorator m3;
 *     private final InvocationInterceptor interceptor;
 *     private volatile MethodHandle mhFoo;
 *
 *     static {
 *         try {
 *             m0 = MethodDecorator.of(Class.forName("java.lang.Object").getMethod("equals", Object.class));
 *             m1 = MethodDecorator.of(Class.forName("java.lang.Object").getMethod("hashCode"));
 *             m2 = MethodDecorator.of(Class.forName("java.lang.Object").getMethod("toString"));
 *             m3 = MethodDecorator.of(Class.forName("full-qualified-name-of-Foo").getMethod("foo"));
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public $NewProxy0(InvocationInterceptor interceptor) {
 *         this.interceptor = interceptor;
 *     }
 *
 *     public final boolean equals(Object o) {
 *         try {
 *             return (Boolean) this.interceptor.intercept(this, m0, new Object[]{o});
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public final int hashCode() {
 *         try {
 *             return (Integer) this.interceptor.intercept(this, m1, (Object[]) null);
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public final String toString() {
 *         try {
 *             return (String) this.interceptor.intercept(this, m2, (Object[]) null);
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public final void foo() {
 *         try {
 *             this.interceptor.intercept(this, m3, (Object[]) null);
 *         }  catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public final Object dispatch(Object object, MethodDecorator method, Object[] args) throws Throwable {
 *         int var4 = method.getHashCode();
 *         if (-1918826964 == var4) {
 *             return super.equals(args[0]);
 *         } else if (933549448 == var4) {
 *             return super.hashCode();
 *         } else if (-1451283457 == var4) {
 *             return super.toString();
 *         } else if (-199555930 == var4) {
 *             this.doInvokeFoo(object);
 *             return null;
 *         }
 *         return null;
 *     }
 *
 *     private void doInvokeFoo(Object object) {
 *         if (this.mhFoo == null) {
 *             synchronized(this) {
 *                 if (this.mhFoo == null) {
 *                     this.mhFoo = MethodHandles.lookup().findVirtual(FooService.class, "foo", MethodType.methodType(Void.TYPE)).bindTo(object);
 *                 }
 *             }
 *         }
 *         this.mhFoo.invokeExact();
 *     }
 *
 * }
 * </pre></blockquote><br/>
 * Note that class format can be divided into six parts as below:
 * <ol>
 *     <li>Static variables in a proxy class.</li>
 *     <li>Static variables initialization in a proxy class, using try-catch block.</li>
 *     <li>Default constructor of the proxy class with public modifier which initialize an instance field
 *     with name "interceptor", whose type is {@link InvocationInterceptor}.</li>
 *     <li>Implementation of methods overridden from interfaces (class also) and {@code equals}, {@code hashCode}
 *     and {@code toString} from {@code java.lang.Object}.</li>
 *     <li>Implementation of interface {@link InvocationDispatcher} to
 *     {@link InvocationDispatcher#dispatch(Object, MethodDecorator, Object...) dispatch} method invocation.</li>
 *     <li>Method invocation for method inherits from interfaces, using {@code java.lang.invoke} API.</li>
 * </ol><br/>
 *
 * <h3>Procedure to Generate Dynamic Proxy Class</h3>
 * there are several steps to generate a dynamic proxy class for specified interfaces with given proxy class name
 * and modifiers as following as you can see.
 * <ol>
 *     <li>Creates an instance with type of {@link ClassGen} to represent an object to generate a Java Class.</li>
 *     <li>Sets the major number and the minor number (Optionally).</li>
 *     <li>Generates variables in a proxy class with modifier {@code private static final} and of type
 *     {@code Method}.</li>
 *     <li>Initializes static variables in a proxy class in a static initializer.</li>
 *     <li>Generates the default constructor of this proxy class with modifier {@code public}, which needs a
 *     parameter of type {@link InvocationInterceptor}, and that parameter will be assigned to an instance filed
 *     of type {@link InvocationInterceptor} with name "interceptor", which process methods invocations of a proxy
 *     class.</li>
 *     <li>Implements all methods originate from interfaces (and class) with three methods from
 *     {@code java.lang.Object}: {@code equals}, {@code hashCode} and {@code toString}.</li>
 *     <li>Implements method from interface {@link InvocationDispatcher} to dispatch method invocation to
 *     appropriate execution logic.</li>
 *     <li>Implements method invocation for method inherits from interfaces, using {@code java.lang.invoke} API.</li>
 *     <li>Exports generated proxy class in an array of byte.</li>
 * </ol>
 *
 * @author Lam Tong
 * @version 1.0.0
 * @see NewProxy
 * @since 1.0.0
 */
public final class ProxyGenerator {

    private ProxyGenerator() {
    }

    /**
     * proxy class name hold in ThreadLocal
     */
    private static final ThreadLocal<String> proxyClassName = new ThreadLocal<>();

    /**
     * mapping for method information of type Method with static variables in a proxy class
     */
    private static final ThreadLocal<LinkedHashMap<Method, String>> METHOD_CACHE = new ThreadLocal<>();

    /**
     * flag to indicate whether to generate method invocation for method inherits from interfaces, using
     * {@code Dynamic Language Support} in package {@code java.lang.invoke}
     */
    private static final ThreadLocal<Boolean> GENERATE_DO_INVOKE = new ThreadLocal<>();

    /**
     * Generates a dynamic proxy class for specified classes with given proxy class name and modifiers.
     *
     * @param proxyClass the proxy class name
     * @param accessFlag the proxy class modifier
     * @param classes    the classes to be implemented or extends for the proxy class
     * @return a proxy class in an array of byte
     * @throws IllegalArgumentException if the specified classes contain a base class to be extended and that class
     *                                  is {@code private}, {@code abstract} or {@code final}.
     * @throws RuntimeException         if generation exception occurs, or exception occurs when dump generated
     *                                  byte array.
     */
    public static byte[] generate(String proxyClass, int accessFlag, Class<?>[] classes) {
        // Checks if the specified classes contain a base class to be extended or not.
        // If so, the proxy class will extend the base class.
        Class<?> parentClass = findClass(classes);
        ClassGen classGen;
        if (parentClass != null) {
            int modifiers = parentClass.getModifiers();
            // fixme: what kind of class can be extended? Should static class be extended or not?
            if (Modifier.isPrivate(modifiers)) {
                throw new IllegalArgumentException("Class [" + parentClass.getName() + "] is private");
            }
            if (Modifier.isAbstract(modifiers)) {
                throw new IllegalArgumentException("Class [" + parentClass.getName() + "] is abstract");
            }
            if (Modifier.isFinal(modifiers)) {
                throw new IllegalArgumentException("Class [" + parentClass.getName() + "] is final");
            }
            classGen = new ClassGen(proxyClass, parentClass.getName(), "<generated>", accessFlag, extractNamesFromInterfaces(filterClass(classes)));
        } else {
            classGen = new ClassGen(proxyClass, Object.class.getName(), "<generated>", accessFlag, extractNamesFromInterfaces(classes));
        }
        GENERATE_DO_INVOKE.set(System.getProperty(STRING_GENERATE_DO_INVOKE_METHOD, "true").equalsIgnoreCase("true"));
        proxyClassName.set(proxyClass);
        METHOD_CACHE.set(new LinkedHashMap<>());
        try {
            ConstantPoolGen constantPool = classGen.getConstantPool();
            classGen.setMinor(Const.MINOR_1_8);
            classGen.setMajor(Const.MAJOR_1_8);

            // Add @Proxied to proxy class
            classGen.addAnnotationEntry(new AnnotationEntryGen(new ObjectType(Proxied.class.getName()), Collections.emptyList(), true, constantPool));

            // generates static variables for proxy class
            generateStaticVariables(classGen, constantPool, classes);

            // initialize static variable for proxy class in static initializer
            generateStaticVariableInitializer(classGen, constantPool);

            // generate default constructor for proxy class
            generateDefaultConstructor(classGen, constantPool, parentClass);

            // generate methods to invoke
            generateMethods(classGen, constantPool);

            // generate implementation of interface InvocationDispatcher to encode and dispatch method invocation
            generateDispatchMethod(classGen, constantPool);
        } catch (Exception e) {
            throw new RuntimeException("exception with message: " + e.getMessage() + "for proxy class" + proxyClass, e);
        } finally {
            GENERATE_DO_INVOKE.remove();
            proxyClassName.remove();
            METHOD_CACHE.remove();
        }
        JavaClass javaClass = classGen.getJavaClass();
        // can debug javaClass object here
        dumpJavaClass(javaClass, proxyClass);
        return javaClass.getBytes();
    }

    /**
     * Dumps generated java class into {@code .class} file in specified directory.
     *
     * @param javaClass  {@code JavaClass} instance to be dumped
     * @param proxyClass the proxy class name
     */
    private static void dumpJavaClass(JavaClass javaClass, String proxyClass) {
        if (System.getProperty(STRING_DUMP_FLAG, "false").equalsIgnoreCase("true")) {
            String dir = System.getProperty(STRING_DUMP_DIR, STRING_DUMP_DIR_DEFAULT);
            try {
                int index = proxyClass.lastIndexOf('.');
                String pkg = proxyClass.substring(0, index + 1).replace(".", File.separator);
                File file = new File(dir + File.separator + pkg);
                if (!file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.mkdirs();
                }
                javaClass.dump(dir + File.separator + pkg + proxyClass.substring(index + 1) + ".class");
            } catch (IOException e) {
                throw new RuntimeException("exception when dumping class: " + proxyClass + ", message: " + e.getMessage());
            }
        }
    }

    /**
     * Finds the first non-interface class in the specified classes.
     *
     * @param classes classes to be checked
     * @return the first non-interface class in the specified classes, otherwise null.
     */
    private static Class<?> findClass(Class<?>[] classes) {
        return Arrays.stream(classes)
                .filter(c -> !c.isInterface())
                .findFirst()
                .orElse(null);
    }

    /**
     * Filters out a class object, retaining only interfaces.
     *
     * @param classes classes to be filtered
     * @return an array of interfaces.
     */
    private static Class<?>[] filterClass(Class<?>[] classes) {
        return Arrays.stream(classes)
                .filter(Class::isInterface)
                .toArray(Class<?>[]::new);
    }

    /**
     * Extracts the names of interfaces from the specified classes.
     *
     * @param interfaces the list of interfaces to be implemented by proxy class
     * @return an array of interface names.
     */
    private static String[] extractNamesFromInterfaces(Class<?>[] interfaces) {
        String[] res = new String[interfaces.length + 1];
        for (int i = 0; i < interfaces.length; i++) {
            res[i] = interfaces[i].getName();
        }
        res[res.length - 1] = CLASS_INVOCATION_DISPATCHER;
        return res;
    }

    /**
     * Generates default {@code private} {@code static} {@code final} variables of type {@link MethodDecorator} for proxy class.
     * Mapping relationship can be listed as below:
     * <ul>
     *     <li>m0 -> MethodDecorator.of(Class.forName("java.lang.Object").getMethod("equals", Object.class))</li>
     *     <li>m1 -> MethodDecorator.of(Class.forName("java.lang.Object").getMethod("hashCode"))</li>
     *     <li>m2 -> MethodDecorator.of(Class.forName("java.lang.Object").getMethod("toString"))</li>
     * </ul>
     *
     * @param classGen     {@link ClassGen} instance
     * @param constantPool {@link ConstantPoolGen} instance
     */
    private static void generateDefaultStaticVariables(ClassGen classGen, ConstantPoolGen constantPool) {
        int modifiers = Const.ACC_PRIVATE | Const.ACC_STATIC | Const.ACC_FINAL;
        ObjectType objectType = new ObjectType(MethodDecorator.class.getName());
        Field m0 = new FieldGen(modifiers, objectType, "m0", constantPool).getField(),
                m1 = new FieldGen(modifiers, objectType, "m1", constantPool).getField(),
                m2 = new FieldGen(modifiers, objectType, "m2", constantPool).getField();
        classGen.addField(m0);
        classGen.addField(m1);
        classGen.addField(m2);
    }

    /**
     * Generates static variables of type {@link MethodDecorator} for proxy class.
     *
     * @param classGen     {@link ClassGen} instance
     * @param constantPool {@link ConstantPoolGen} instance
     * @param classes      the list of classes to extract methods to enhance
     */
    private static void generateStaticVariables(ClassGen classGen, ConstantPoolGen constantPool, Class<?>[] classes) {
        generateDefaultStaticVariables(classGen, constantPool);
        try {
            Class<?> clazz = Class.forName(Object.class.getName());
            METHOD_CACHE.get().put(clazz.getMethod(METHOD_EQUALS, Object.class), "m0");
            METHOD_CACHE.get().put(clazz.getMethod(METHOD_HASH_CODE), "m1");
            METHOD_CACHE.get().put(clazz.getMethod(METHOD_TO_STRING), "m2");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        List<Method> methods = new ArrayList<>();
        for (Class<?> clazz : classes) {
            if (clazz.isInterface()) {
                // Class object represents an interface
                methods.addAll(Arrays.asList(clazz.getMethods()));
            } else {
                // Class object represents a class.
                // Methods inherited from Class Object should be excluded.
                methods.addAll(Arrays.stream(clazz.getMethods())
                        .filter(m -> !m.getDeclaringClass().equals(Object.class))
                        .filter(m -> !Modifier.isFinal(m.getModifiers()))
                        .filter(m -> !Modifier.isStatic(m.getModifiers()))
                        .collect(Collectors.toList()));
            }
        }
        if (methods.isEmpty()) {
            return;
        }
        int modifiers = Const.ACC_PRIVATE | Const.ACC_STATIC | Const.ACC_FINAL;
        ObjectType type = new ObjectType(MethodDecorator.class.getName());
        Set<String> set = new HashSet<>();
        for (int i = 0; i < methods.size(); i++) {
            Method method = methods.get(i);
            String methodSignature = getMethodSignature(method);
            if (set.add(methodSignature)) {
                String fieldName = "m" + (3 + i);
                METHOD_CACHE.get().put(method, fieldName);
                FieldGen fieldGen = new FieldGen(modifiers, type, fieldName, constantPool);
                classGen.addField(fieldGen.getField());
            }
        }
    }

    /**
     * Gets method signature, including three parts: method name, return type, parameter types.
     *
     * @param method the method to get signature
     * @return method signature in {@code String}
     */
    public static String getMethodSignature(Method method) {
        String name = method.getName();
        Class<?> returnType = method.getReturnType();
        Parameter[] parameters = method.getParameters();
        return name + ";" + returnType + ";" + Arrays.toString(parameters);
    }

    /**
     * Generates static variable initializer for proxy class using {@code try-catch} block.
     *
     * @param classGen     {@link ClassGen} instance
     * @param constantPool {@link ConstantPoolGen} instance
     */
    private static void generateStaticVariableInitializer(ClassGen classGen, ConstantPoolGen constantPool) {
        InstructionList list = new InstructionList();
        InstructionFactory factory = new InstructionFactory(constantPool);
        MethodGen methodGen = new MethodGen(Const.ACC_STATIC, Type.VOID, Type.NO_ARGS, null, METHOD_CL_INIT, proxyClassName.get(), list, constantPool);

        InstructionHandle try_start = null, try_end = null;
        // generate class variables in a for loop
        for (Method method : METHOD_CACHE.get().keySet()) {
            String methodName = method.getName(), className = method.getDeclaringClass().getName();
            Parameter[] parameters = method.getParameters();

            InstructionHandle handle = list.append(new LDC(constantPool.addString(className)));
            if (try_start == null) {
                try_start = handle;
            }
            list.append(factory.createInvoke(CLASS_CLASS, METHOD_FOR_NAME, Type.CLASS, new Type[]{Type.STRING}, Const.INVOKESTATIC));
            list.append(new LDC(constantPool.addString(methodName)));
            list.append(new ICONST(parameters.length));
            list.append(new ANEWARRAY(constantPool.addClass(CLASS_CLASS)));
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Class<?> type = parameter.getType();
                String parameterTypeName = type.getName();
                list.append(new DUP());
                list.append(new ICONST(i));
                if (type.isPrimitive()) {
                    Class<?> wrapperClass = transformPrimitiveTypesToWrapperTypes(type);
                    list.append(new GETSTATIC(constantPool.addFieldref(wrapperClass.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                } else {
                    list.append(new LDC(constantPool.addClass(parameterTypeName)));
                }
                list.append(new AASTORE());
            }
            list.append(factory.createInvoke(CLASS_CLASS, METHOD_GET_METHOD, new ObjectType(Method.class.getName()), new Type[]{Type.STRING, new ArrayType(Type.CLASS, 1)}, Const.INVOKEVIRTUAL));
            list.append(factory.createInvoke(CLASS_METHOD_DECORATOR, METHOD_OF, new ObjectType(MethodDecorator.class.getName()), new Type[]{new ObjectType(Method.class.getName())}, Const.INVOKESTATIC));
            try_end = list.append(new PUTSTATIC(constantPool.addFieldref(proxyClassName.get(), METHOD_CACHE.get().get(method), SIGNATURE_METHOD_DECORATOR)));
        }

        // catch (NoSuchMethodException e) {
        //   throw new NoSuchMethodError(e.getMessage());
        // }
        GOTO g = new GOTO(null);
        list.append(g);
        InstructionHandle handle108 = list.append(new ASTORE(0));
        list.append(new NEW(constantPool.addClass(CLASS_NO_SUCH_METHOD_ERROR)));
        list.append(new DUP());
        list.append(new ALOAD(0));
        list.append(factory.createInvoke(NoSuchMethodException.class.getName(), METHOD_GET_MESSAGE, Type.STRING, Type.NO_ARGS, Const.INVOKEVIRTUAL));
        list.append(factory.createInvoke(NoSuchMethodError.class.getName(), METHOD_INIT, Type.VOID, new Type[]{Type.STRING}, Const.INVOKESPECIAL));
        list.append(InstructionConst.ATHROW);
        methodGen.addExceptionHandler(try_start, try_end, handle108, new ObjectType(NoSuchMethodException.class.getName()));

        // catch (ClassNotFoundException e) {
        //   throw new NoClassDefFoundError(e.getMessage());
        // }
        InstructionHandle handle121 = list.append(new ASTORE(0));
        list.append(new NEW(constantPool.addClass(CLASS_NO_CLASS_DEF_FOUND_ERROR)));
        list.append(new DUP());
        list.append(new ALOAD(0));
        list.append(factory.createInvoke(ClassNotFoundException.class.getName(), METHOD_GET_MESSAGE, Type.STRING, Type.NO_ARGS, Const.INVOKEVIRTUAL));
        list.append(factory.createInvoke(NoClassDefFoundError.class.getName(), METHOD_INIT, Type.VOID, new Type[]{Type.STRING}, Const.INVOKESPECIAL));
        list.append(InstructionConst.ATHROW);
        methodGen.addExceptionHandler(try_start, try_end, handle121, new ObjectType(ClassNotFoundException.class.getName()));
        g.setTarget(list.append(InstructionConst.RETURN));

        list.setPositions();
        StackMapEntry[] entries = new StackMapEntry[]{
                new StackMapEntry(Const.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED, handle108.getPosition(), new StackMapType[0], new StackMapType[]{new StackMapType(((byte) 7), constantPool.addClass(CLASS_NO_SUCH_METHOD_EXCEPTION), constantPool.getConstantPool())}, constantPool.getConstantPool()),
                new StackMapEntry(handle121.getPosition() - handle108.getPosition() - 1 + 64, handle121.getPosition() - handle108.getPosition() - 1, new StackMapType[0], new StackMapType[]{new StackMapType(((byte) 7), constantPool.addClass(Class_CLASS_NOT_FOUND_EXCEPTION), constantPool.getConstantPool())}, constantPool.getConstantPool()),
                new StackMapEntry(handle121.getPosition() - handle108.getPosition() - 1, handle121.getPosition() - handle108.getPosition() - 1, new StackMapType[0], new StackMapType[0], constantPool.getConstantPool())
        };
        // fixme: how to figure out the second parameter below, passed like "STACK_MAP_TABLE.length()"
        methodGen.addCodeAttribute(new StackMap(constantPool.addUtf8(STACK_MAP_TABLE), STACK_MAP_TABLE.length(), entries, constantPool.getConstantPool()));

        methodGen.setMaxLocals();
        methodGen.setMaxStack();
        classGen.addMethod(methodGen.getMethod());
        list.dispose();
    }

    /**
     * Transforms primitive types to wrapper types.
     *
     * @param type the primitive type to transform
     * @return the wrapper type
     */
    private static Class<?> transformPrimitiveTypesToWrapperTypes(Class<?> type) {
        if (byte.class == type) {
            return Byte.class;
        } else if (short.class == type) {
            return Short.class;
        } else if (int.class == type) {
            return Integer.class;
        } else if (long.class == type) {
            return Long.class;
        } else if (float.class == type) {
            return Float.class;
        } else if (double.class == type) {
            return Double.class;
        } else if (boolean.class == type) {
            return Boolean.class;
        } else if (char.class == type) {
            return Character.class;
        } else {
            throw new RuntimeException("Class [" + type + "] is not primitive");
        }
    }

    /**
     * Generates the default constructor for the proxy class.
     *
     * @param classGen     {@link ClassGen} instance
     * @param constantPool {@link ConstantPoolGen} instance
     * @param parentClass  the parent class to extend for the proxy class
     */
    private static void generateDefaultConstructor(ClassGen classGen, ConstantPoolGen constantPool, Class<?> parentClass) {
        FieldGen handlerFieldGen = new FieldGen(Const.ACC_PRIVATE | Const.ACC_FINAL, new ObjectType(InvocationInterceptor.class.getName()), FIELD_INTERCEPTOR, constantPool);
        classGen.addField(handlerFieldGen.getField());

        InstructionList list = new InstructionList();
        InstructionFactory factory = new InstructionFactory(constantPool);

        // If the parent class has a constructor with parameters, then "super" should be called in constructor first.
        Class<?>[] parameterTypes = NewProxy.ARG_TYPES.get();
        Type[] parameterTypesArray = new Type[parameterTypes.length + 1];
        String[] parameterNames = new String[parameterTypes.length + 1];
        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypesArray[i + 1] = getTypeFromClass(parameterTypes[i]);
            parameterNames[i + 1] = "arg" + i;
        }
        parameterTypesArray[0] = new ObjectType(InvocationInterceptor.class.getName());
        parameterNames[0] = FIELD_INTERCEPTOR;

        MethodGen methodGen = new MethodGen(Const.ACC_PUBLIC, Type.VOID, parameterTypesArray, parameterNames, METHOD_INIT, proxyClassName.get(), list, constantPool);
        list.append(new ALOAD(0));
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType.equals(boolean.class)) {
                list.append(new ILOAD(i + 2));
            } else if (parameterType.equals(byte.class)) {
                list.append(new ILOAD(i + 2));
            } else if (parameterType.equals(char.class)) {
                list.append(new ILOAD(i + 2));
            } else if (parameterType.equals(short.class)) {
                list.append(new ILOAD(i + 2));
            } else if (parameterType.equals(int.class)) {
                list.append(new ILOAD(i + 2));
            } else if (parameterType.equals(long.class)) {
                list.append(new LLOAD(i + 2));
            } else if (parameterType.equals(float.class)) {
                list.append(new FLOAD(i + 2));
            } else if (parameterType.equals(double.class)) {
                list.append(new DLOAD(i + 2));
            } else {
                list.append(new ALOAD(i + 2));
            }
        }
        if (parentClass != null) {
            Type[] args = new Type[parameterTypes.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = getTypeFromClass(parameterTypes[i]);
            }
            list.append(factory.createInvoke(parentClass.getName(), METHOD_INIT, Type.VOID, args, Const.INVOKESPECIAL));
        } else {
            list.append(factory.createInvoke(Object.class.getName(), METHOD_INIT, Type.VOID, Type.NO_ARGS, Const.INVOKESPECIAL));
        }
        list.append(new ALOAD(0));
        list.append(new ALOAD(1));
        list.append(new PUTFIELD(constantPool.addFieldref(proxyClassName.get(), FIELD_INTERCEPTOR, SIGNATURE_INVOCATION_INTERCEPTOR)));
        list.append(InstructionConst.RETURN);

        methodGen.setMaxLocals();
        methodGen.setMaxStack();
        classGen.addMethod(methodGen.getMethod());
        list.dispose();
    }

    /**
     * Generates the enhanced methods for the proxy class.
     *
     * @param classGen     {@link ClassGen} instance
     * @param constantPool {@link ConstantPoolGen} instance
     */
    private static void generateMethods(ClassGen classGen, ConstantPoolGen constantPool) {
        InstructionList list = new InstructionList();
        for (Method method : METHOD_CACHE.get().keySet()) {
            Class<?> returnType = method.getReturnType();
            Parameter[] parameters = method.getParameters();
            int value = parameters.length + 1;
            Type[] types = new Type[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Class<?> type = parameters[i].getType();
                types[i] = getTypeFromClass(type);
            }
            InstructionFactory factory = new InstructionFactory(constantPool);

            Type return_type = getTypeFromClass(returnType);
            MethodGen methodGen = new MethodGen(Const.ACC_PUBLIC | Const.ACC_FINAL, return_type, types, null, method.getName(), proxyClassName.get(), list, constantPool);
            int additional = 0;
            InstructionHandle try_start = list.append(new ALOAD(0));
            list.append(new GETFIELD(constantPool.addFieldref(proxyClassName.get(), FIELD_INTERCEPTOR, SIGNATURE_INVOCATION_INTERCEPTOR)));
            list.append(new ALOAD(0));
            list.append(new GETSTATIC(constantPool.addFieldref(proxyClassName.get(), METHOD_CACHE.get().get(method), SIGNATURE_METHOD_DECORATOR)));
            if (parameters.length == 0) {
                list.append(new ACONST_NULL());
                list.append(new CHECKCAST(constantPool.addClass(Object[].class.getName())));
            } else {
                list.append(new ICONST(parameters.length));
                list.append(new ANEWARRAY(constantPool.addClass(CLASS_OBJECT)));
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    Class<?> type = parameter.getType();
                    list.append(new DUP());
                    list.append(new ICONST(i));
                    // if parameter is primitive, then a conversion is needed
                    if (type.isPrimitive()) {
                        if (type == boolean.class) {
                            list.append(new ILOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Boolean.class.getName(), METHOD_VALUE_OF, new ObjectType(Boolean.class.getName()), new Type[]{Type.BOOLEAN}, Const.INVOKESTATIC));
                        } else if (type == byte.class) {
                            list.append(new ILOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Byte.class.getName(), METHOD_VALUE_OF, new ObjectType(Byte.class.getName()), new Type[]{Type.BYTE}, Const.INVOKESTATIC));
                        } else if (type == short.class) {
                            list.append(new ILOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Short.class.getName(), METHOD_VALUE_OF, new ObjectType(Short.class.getName()), new Type[]{Type.SHORT}, Const.INVOKESTATIC));
                        } else if (type == char.class) {
                            list.append(new ILOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Character.class.getName(), METHOD_VALUE_OF, new ObjectType(Character.class.getName()), new Type[]{Type.CHAR}, Const.INVOKESTATIC));
                        } else if (type == int.class) {
                            list.append(new ILOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Integer.class.getName(), METHOD_VALUE_OF, new ObjectType(Integer.class.getName()), new Type[]{Type.INT}, Const.INVOKESTATIC));
                        } else if (type == long.class) {
                            list.append(new LLOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Long.class.getName(), METHOD_VALUE_OF, new ObjectType(Long.class.getName()), new Type[]{Type.LONG}, Const.INVOKESTATIC));
                            additional++;
                        } else if (type == float.class) {
                            list.append(new FLOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Float.class.getName(), METHOD_VALUE_OF, new ObjectType(Float.class.getName()), new Type[]{Type.FLOAT}, Const.INVOKESTATIC));
                        } else if (type == double.class) {
                            list.append(new DLOAD(i + 1 + additional));
                            list.append(factory.createInvoke(Double.class.getName(), METHOD_VALUE_OF, new ObjectType(Double.class.getName()), new Type[]{Type.DOUBLE}, Const.INVOKESTATIC));
                            additional++;
                        }
                    } else {
                        list.append(new ALOAD(i + 1));
                    }
                    list.append(new AASTORE());
                }
            }
            list.append(factory.createInvoke(InvocationInterceptor.class.getName(), METHOD_INTERCEPT, Type.OBJECT, new Type[]{Type.OBJECT, new ObjectType(MethodDecorator.class.getName()), new ArrayType(Type.OBJECT, 1)}, Const.INVOKEINTERFACE));
            InstructionHandle try_end;
            if (returnType.isPrimitive()) {
                if (returnType == boolean.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Boolean.class.getName())));
                    list.append(factory.createInvoke(Boolean.class.getName(), METHOD_BOOLEAN_VALUE, Type.BOOLEAN, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == byte.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Byte.class.getName())));
                    list.append(factory.createInvoke(Byte.class.getName(), METHOD_BYTE_VALUE, Type.BYTE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == short.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Short.class.getName())));
                    list.append(factory.createInvoke(Short.class.getName(), METHOD_SHORT_VALUE, Type.SHORT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == char.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Character.class.getName())));
                    list.append(factory.createInvoke(Character.class.getName(), METHOD_CHAR_VALUE, Type.CHAR, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == int.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Integer.class.getName())));
                    list.append(factory.createInvoke(Integer.class.getName(), METHOD_INT_VALUE, Type.INT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == long.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Long.class.getName())));
                    list.append(factory.createInvoke(Long.class.getName(), METHOD_LONG_VALUE, Type.LONG, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.LRETURN);
                } else if (returnType == float.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Float.class.getName())));
                    list.append(factory.createInvoke(Float.class.getName(), METHOD_FLOAT_VALUE, Type.FLOAT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.FRETURN);
                } else if (returnType == double.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Double.class.getName())));
                    list.append(factory.createInvoke(Double.class.getName(), METHOD_DOUBLE_VALUE, Type.DOUBLE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.DRETURN);
                } else {
                    // void return type
                    list.append(new POP());
                    try_end = list.append(InstructionConst.RETURN);
                }
            } else {
                list.append(new CHECKCAST(constantPool.addClass(returnType.getName())));
                try_end = list.append(InstructionConst.ARETURN);
            }

            //  catch (RuntimeException | Error e) {
            //    throw e;
            //  }
            InstructionHandle handle_1 = list.append(new ASTORE(value + additional));
            list.append(new ALOAD(value + additional));
            list.append(InstructionConst.ATHROW);
            methodGen.addExceptionHandler(try_start, try_end, handle_1, new ObjectType(Error.class.getName()));
            methodGen.addExceptionHandler(try_start, try_end, handle_1, new ObjectType(RuntimeException.class.getName()));

            //  catch (Throwable e) {
            //    throw new UndeclaredThrowableException(e);
            //  }
            InstructionHandle handle_2 = list.append(new ASTORE(value + additional));
            list.append(new NEW(constantPool.addClass(CLASS_UNDECLARED_THROWABLE_EXCEPTION)));
            list.append(new DUP());
            list.append(new ALOAD(value + additional));
            list.append(factory.createInvoke(UndeclaredThrowableException.class.getName(), METHOD_INIT, Type.VOID, new Type[]{new ObjectType(Throwable.class.getName())}, Const.INVOKESPECIAL));
            list.append(InstructionConst.ATHROW);
            methodGen.addExceptionHandler(try_start, try_end, handle_2, new ObjectType(Throwable.class.getName()));

            list.setPositions();
            StackMapEntry[] entries = new StackMapEntry[]{
                    new StackMapEntry(handle_1.getPosition() + 64, handle_1.getPosition(), new StackMapType[0], new StackMapType[]{new StackMapType((byte) 7, constantPool.addClass(CLASS_THROWABLE), constantPool.getConstantPool())}, constantPool.getConstantPool()),
                    new StackMapEntry(handle_2.getPosition() - handle_1.getPosition() - 1 + 64, handle_2.getPosition() - handle_1.getPosition() - 1, new StackMapType[0], new StackMapType[]{new StackMapType((byte) 7, constantPool.addClass(CLASS_THROWABLE), constantPool.getConstantPool())}, constantPool.getConstantPool())
            };
            // fixme: the magic number 10 is a magic number, and I don't know how to calculate it
            methodGen.addCodeAttribute(new StackMap(constantPool.addUtf8(STACK_MAP_TABLE), 10, entries, constantPool.getConstantPool()));

            methodGen.setMaxLocals();
            methodGen.setMaxStack();
            classGen.addMethod(methodGen.getMethod());
            list.dispose();
        }
    }

    /**
     * Get the {@link Type} from the class.
     *
     * @param clazz the class to get the {@link Type} from
     * @return the {@link Type}
     */
    private static Type getTypeFromClass(Class<?> clazz) {
        Type return_type;
        // Check if the return type is primitive
        if (clazz.isPrimitive()) {
            // Return the matching Type enum constant based on the primitive type
            if (clazz == boolean.class) {
                return_type = Type.BOOLEAN;
            } else if (clazz == byte.class) {
                return_type = Type.BYTE;
            } else if (clazz == short.class) {
                return_type = Type.SHORT;
            } else if (clazz == char.class) {
                return_type = Type.CHAR;
            } else if (clazz == int.class) {
                return_type = Type.INT;
            } else if (clazz == long.class) {
                return_type = Type.LONG;
            } else if (clazz == float.class) {
                return_type = Type.FLOAT;
            } else if (clazz == double.class) {
                return_type = Type.DOUBLE;
            } else {
                // Default to VOID if not a common primitive type
                return_type = Type.VOID;
            }
        } else {
            // For reference types, create an ObjectType with the fully qualified name
            return_type = new ObjectType(clazz.getName());
        }
        return return_type;
    }

    /**
     * Generate the dispatch method inherited from interface {@link InvocationDispatcher}.
     *
     * @param classGen     the {@link ClassGen} instance
     * @param constantPool the {@link ConstantPoolGen} instance
     * @throws NoSuchMethodException if the method is not found when acquiring {@link Method} instances.
     */
    @SuppressWarnings(value = {"DuplicatedCode"})
    private static void generateDispatchMethod(ClassGen classGen, ConstantPoolGen constantPool) throws NoSuchMethodException {
        Set<String> set = new HashSet<String>() {{
            add(METHOD_EQUALS);
            add(METHOD_HASH_CODE);
            add(METHOD_TO_STRING);
        }};
        List<Method> methods = METHOD_CACHE.get()
                .keySet()
                .stream()
                .filter(m -> !set.contains(m.getName()))
                .collect(Collectors.toList());

        InstructionList list = new InstructionList();
        InstructionFactory factory = new InstructionFactory(constantPool);
        MethodGen methodGen = new MethodGen(Const.ACC_PUBLIC | Const.ACC_FINAL, Type.OBJECT, new Type[]{Type.OBJECT, new ObjectType(MethodDecorator.class.getName()), new ArrayType(Type.OBJECT, 1)}, new String[]{"object", "method", "args"}, "dispatch", CLASS_INVOCATION_DISPATCHER, list, constantPool);

        list.append(new ALOAD(2));
        list.append(factory.createInvoke(MethodDecorator.class.getName(), "getHashCode", Type.INT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
        list.append(new ISTORE(4));

        StackMapEntry[] entries = new StackMapEntry[methods.size() + 3];
        InstructionHandle pre, cur;

        // Object.equals(Object o)
        list.append(new LDC(constantPool.addInteger(Objects.hashCode(Object.class.getMethod(METHOD_EQUALS, Object.class)))));
        list.append(new ILOAD(4));
        IF_ICMPNE if_icmpne = new IF_ICMPNE(null);
        list.append(if_icmpne);
        list.append(new ALOAD(0));
        list.append(new ALOAD(3));
        list.append(new ICONST(0));
        list.append(new AALOAD());
        list.append(factory.createInvoke(Object.class.getName(), METHOD_EQUALS, Type.BOOLEAN, new Type[]{Type.OBJECT}, Const.INVOKESPECIAL));
        list.append(factory.createInvoke(Boolean.class.getName(), METHOD_VALUE_OF, new ObjectType(Boolean.class.getName()), new Type[]{Type.BOOLEAN}, Const.INVOKESTATIC));
        list.append(new ARETURN());

        // Object.hashCode()
        cur = list.append(new LDC(constantPool.addInteger(Objects.hashCode(Object.class.getMethod(METHOD_HASH_CODE)))));
        if_icmpne.setTarget(cur);
        list.append(new ILOAD(4));
        if_icmpne = new IF_ICMPNE(null);
        list.append(if_icmpne);
        list.append(new ALOAD(0));
        list.append(factory.createInvoke(Object.class.getName(), METHOD_HASH_CODE, Type.INT, Type.NO_ARGS, Const.INVOKESPECIAL));
        list.append(factory.createInvoke(Integer.class.getName(), METHOD_VALUE_OF, new ObjectType(Integer.class.getName()), new Type[]{Type.INT}, Const.INVOKESTATIC));
        list.append(new ARETURN());

        list.setPositions();
        entries[0] = new StackMapEntry(Const.APPEND_FRAME, cur.getPosition(), new StackMapType[]{new StackMapType(Const.ITEM_Integer, -1, constantPool.getConstantPool())}, new StackMapType[0], constantPool.getConstantPool());
        pre = cur;

        // Object.toString()
        cur = list.append(new LDC(constantPool.addInteger(Objects.hashCode(Object.class.getMethod(METHOD_TO_STRING)))));
        if_icmpne.setTarget(cur);
        list.append(new ILOAD(4));
        if_icmpne = new IF_ICMPNE(null);
        list.append(if_icmpne);
        list.append(new ALOAD(0));
        list.append(factory.createInvoke(Object.class.getName(), METHOD_TO_STRING, Type.STRING, Type.NO_ARGS, Const.INVOKESPECIAL));
        list.append(new ARETURN());

        list.setPositions();
        entries[1] = new StackMapEntry(cur.getPosition() - pre.getPosition() - 1, cur.getPosition() - pre.getPosition() - 1, new StackMapType[0], new StackMapType[0], constantPool.getConstantPool());
        pre = cur;

        for (int i = 0; i < methods.size(); i++) {
            Method method = methods.get(i);
            if (method.getDeclaringClass().isInterface() && GENERATE_DO_INVOKE.get()) {
                // method from interface, which should be invoked via MethodHandle along with another "doInvoke..." method
                String methodName = method.getName(), doMethodName = METHOD_DO_INVOKE + StringUtils.capitalize(methodName);
                Parameter[] parameters = method.getParameters();
                Type returnType = getTypeFromClass(method.getReturnType());

                Type[] parameterTypes;
                if (parameters.length > 0) {
                    parameterTypes = new Type[]{Type.OBJECT, new ArrayType(Type.OBJECT, 1)};
                } else {
                    parameterTypes = new Type[]{Type.OBJECT};
                }

                cur = list.append(new LDC(constantPool.addInteger(Objects.hashCode(method))));
                if_icmpne.setTarget(cur);
                list.append(new ILOAD(4));
                if_icmpne = new IF_ICMPNE(null);
                list.append(if_icmpne);
                list.append(new ALOAD(0));
                list.append(new ALOAD(1));
                if (parameters.length > 0) {
                    list.append(new ALOAD(3));
                }
                list.append(factory.createInvoke(proxyClassName.get(), doMethodName, returnType, parameterTypes, Const.INVOKEVIRTUAL));

                if (returnType.equals(Type.VOID)) {
                    list.append(new ACONST_NULL());
                } else if (returnType.equals(Type.BOOLEAN)) {
                    list.append(factory.createInvoke(Boolean.class.getName(), METHOD_VALUE_OF, new ObjectType(Boolean.class.getName()), new Type[]{Type.BOOLEAN}, Const.INVOKESTATIC));
                } else if (returnType.equals(Type.BYTE)) {
                    list.append(factory.createInvoke(Byte.class.getName(), METHOD_VALUE_OF, new ObjectType(Byte.class.getName()), new Type[]{Type.BYTE}, Const.INVOKESTATIC));
                } else if (returnType.equals(Type.SHORT)) {
                    list.append(factory.createInvoke(Short.class.getName(), METHOD_VALUE_OF, new ObjectType(Short.class.getName()), new Type[]{Type.SHORT}, Const.INVOKESTATIC));
                } else if (returnType.equals(Type.CHAR)) {
                    list.append(factory.createInvoke(Character.class.getName(), METHOD_VALUE_OF, new ObjectType(Character.class.getName()), new Type[]{Type.CHAR}, Const.INVOKESTATIC));
                } else if (returnType.equals(Type.INT)) {
                    list.append(factory.createInvoke(Integer.class.getName(), METHOD_VALUE_OF, new ObjectType(Integer.class.getName()), new Type[]{Type.INT}, Const.INVOKESTATIC));
                } else if (returnType.equals(Type.LONG)) {
                    list.append(factory.createInvoke(Long.class.getName(), METHOD_VALUE_OF, new ObjectType(Long.class.getName()), new Type[]{Type.LONG}, Const.INVOKESTATIC));
                } else if (returnType.equals(Type.FLOAT)) {
                    list.append(factory.createInvoke(Float.class.getName(), METHOD_VALUE_OF, new ObjectType(Float.class.getName()), new Type[]{Type.FLOAT}, Const.INVOKESTATIC));
                } else if (returnType.equals(Type.DOUBLE)) {
                    list.append(factory.createInvoke(Double.class.getName(), METHOD_VALUE_OF, new ObjectType(Double.class.getName()), new Type[]{Type.DOUBLE}, Const.INVOKESTATIC));
                } else {
                    list.append(new CHECKCAST(constantPool.addClass(method.getReturnType().getName())));
                }
                list.append(new ARETURN());

                list.setPositions();
                entries[i + 2] = new StackMapEntry(cur.getPosition() - pre.getPosition() - 1, cur.getPosition() - pre.getPosition() - 1, new StackMapType[0], new StackMapType[0], constantPool.getConstantPool());
                pre = cur;

                generateDoInvokeMethod(classGen, constantPool, method);
            } else {
                // method from class, which should be invoked by "super" directly
                Parameter[] parameters = method.getParameters();
                Class<?> returnClass = method.getReturnType();
                Type returnType = getTypeFromClass(returnClass);
                Type[] argsType = new Type[parameters.length];
                for (int j = 0; j < argsType.length; j++) {
                    argsType[j] = getTypeFromClass(parameters[j].getType());
                }

                cur = list.append(new LDC(constantPool.addInteger(Objects.hashCode(method))));
                if_icmpne.setTarget(cur);
                list.append(new ILOAD(4));
                if_icmpne = new IF_ICMPNE(null);
                list.append(if_icmpne);
                list.append(new ALOAD(0));

                for (int j = 0; j < parameters.length; j++) {
                    Parameter parameter = parameters[j];

                    list.append(new ALOAD(3));
                    list.append(new ICONST(j));
                    list.append(new AALOAD());
                    //noinspection StatementWithEmptyBody
                    if (parameter.getType().equals(Object.class)) {
                        // parameter type is Object.class, ignore
                    } else if (parameter.getType().isPrimitive()) {
                        // parameter type is primitive type
                        if (parameter.getType().equals(boolean.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Boolean.class.getName())));
                            list.append(factory.createInvoke(Boolean.class.getName(), METHOD_BOOLEAN_VALUE, Type.BOOLEAN, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        } else if (parameter.getType().equals(byte.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Byte.class.getName())));
                            list.append(factory.createInvoke(Byte.class.getName(), METHOD_BYTE_VALUE, Type.BYTE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        } else if (parameter.getType().equals(short.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Short.class.getName())));
                            list.append(factory.createInvoke(Short.class.getName(), METHOD_SHORT_VALUE, Type.SHORT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        } else if (parameter.getType().equals(char.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Character.class.getName())));
                            list.append(factory.createInvoke(Character.class.getName(), METHOD_CHAR_VALUE, Type.CHAR, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        } else if (parameter.getType().equals(int.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Integer.class.getName())));
                            list.append(factory.createInvoke(Integer.class.getName(), METHOD_INT_VALUE, Type.INT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        } else if (parameter.getType().equals(long.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Long.class.getName())));
                            list.append(factory.createInvoke(Long.class.getName(), METHOD_LONG_VALUE, Type.LONG, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        } else if (parameter.getType().equals(float.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Float.class.getName())));
                            list.append(factory.createInvoke(Float.class.getName(), METHOD_FLOAT_VALUE, Type.FLOAT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        } else if (parameter.getType().equals(double.class)) {
                            list.append(new CHECKCAST(constantPool.addClass(Double.class.getName())));
                            list.append(factory.createInvoke(Double.class.getName(), METHOD_DOUBLE_VALUE, Type.DOUBLE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                        }
                    } else {
                        // parameter type is wrapper type
                        list.append(new CHECKCAST(constantPool.addClass(parameter.getType().getName())));
                    }
                }

                if (parameters.length > 0) {
                    list.append(factory.createInvoke(method.getDeclaringClass().getName(), method.getName(), returnType, argsType, Const.INVOKESPECIAL));
                } else {
                    list.append(factory.createInvoke(method.getDeclaringClass().getName(), method.getName(), returnType, Type.NO_ARGS, Const.INVOKESPECIAL));
                }

                // return value
                if (returnClass.equals(void.class)) {
                    list.append(new ACONST_NULL());
                } else if (returnClass.equals(boolean.class)) {
                    list.append(factory.createInvoke(Boolean.class.getName(), METHOD_VALUE_OF, new ObjectType(Boolean.class.getName()), new Type[]{Type.BOOLEAN}, Const.INVOKESTATIC));
                } else if (returnClass.equals(byte.class)) {
                    list.append(factory.createInvoke(Byte.class.getName(), METHOD_VALUE_OF, new ObjectType(Byte.class.getName()), new Type[]{Type.BYTE}, Const.INVOKESTATIC));
                } else if (returnClass.equals(short.class)) {
                    list.append(factory.createInvoke(Short.class.getName(), METHOD_VALUE_OF, new ObjectType(Short.class.getName()), new Type[]{Type.SHORT}, Const.INVOKESTATIC));
                } else if (returnClass.equals(char.class)) {
                    list.append(factory.createInvoke(Character.class.getName(), METHOD_VALUE_OF, new ObjectType(Character.class.getName()), new Type[]{Type.CHAR}, Const.INVOKESTATIC));
                } else if (returnClass.equals(int.class)) {
                    list.append(factory.createInvoke(Integer.class.getName(), METHOD_VALUE_OF, new ObjectType(Integer.class.getName()), new Type[]{Type.INT}, Const.INVOKESTATIC));
                } else if (returnClass.equals(long.class)) {
                    list.append(factory.createInvoke(Long.class.getName(), METHOD_VALUE_OF, new ObjectType(Long.class.getName()), new Type[]{Type.LONG}, Const.INVOKESTATIC));
                } else if (returnClass.equals(float.class)) {
                    list.append(factory.createInvoke(Float.class.getName(), METHOD_VALUE_OF, new ObjectType(Float.class.getName()), new Type[]{Type.FLOAT}, Const.INVOKESTATIC));
                } else if (returnClass.equals(double.class)) {
                    list.append(factory.createInvoke(Double.class.getName(), METHOD_VALUE_OF, new ObjectType(Double.class.getName()), new Type[]{Type.DOUBLE}, Const.INVOKESTATIC));
                } else {
                    list.append(new CHECKCAST(constantPool.addClass(returnClass.getName())));
                }
                list.append(new ARETURN());

                list.setPositions();
                entries[i + 2] = new StackMapEntry(cur.getPosition() - pre.getPosition() - 1, cur.getPosition() - pre.getPosition() - 1, new StackMapType[0], new StackMapType[0], constantPool.getConstantPool());
                pre = cur;
            }
        }
        cur = list.append(new ACONST_NULL());
        if_icmpne.setTarget(cur);
        list.append(new ARETURN());

        list.setPositions();
        entries[entries.length - 1] = new StackMapEntry(cur.getPosition() - pre.getPosition() - 1, cur.getPosition() - pre.getPosition() - 1, new StackMapType[0], new StackMapType[0], constantPool.getConstantPool());
        // fixme: How to figure out the length of this StackMapTable attribute?
        methodGen.addCodeAttribute(new StackMap(constantPool.addUtf8(STACK_MAP_TABLE), 8 + methods.size(), entries, constantPool.getConstantPool()));
        methodGen.addException(CLASS_THROWABLE);

        methodGen.setMaxLocals();
        methodGen.setMaxStack();

        com.sun.org.apache.bcel.internal.classfile.Method method = methodGen.getMethod();
        classGen.addMethod(method);
        list.dispose();
    }

    /**
     * generate doInvoke method for which inherit from interfaces.
     *
     * @param classGen     {@link ClassGen} instance
     * @param constantPool {@link ConstantPoolGen} instance
     * @param method       {@link Method} instance, which inherit from interfaces
     */
    private static void generateDoInvokeMethod(ClassGen classGen, ConstantPoolGen constantPool, Method method) {
        String methodName = method.getName(), doMethodName = METHOD_DO_INVOKE + StringUtils.capitalize(methodName);
        Class<?> returnType = method.getReturnType();
        Parameter[] parameters = method.getParameters();

        String methodHandleFieldName = "mh" + StringUtils.capitalize(methodName);
        FieldGen fieldGen = new FieldGen(Const.ACC_PRIVATE | Const.ACC_VOLATILE, new ObjectType(MethodHandle.class.getName()), methodHandleFieldName, constantPool);
        classGen.addField(fieldGen.getField());

        InstructionList list = new InstructionList();
        InstructionFactory factory = new InstructionFactory(constantPool);

        Type[] argsType;
        String[] argsName;
        if (parameters.length == 0) {
            argsType = new Type[]{Type.OBJECT};
            argsName = new String[]{"object"};
        } else {
            argsType = new Type[]{Type.OBJECT, new ArrayType(Type.OBJECT, 1)};
            argsName = new String[]{"object", "args"};
        }
        MethodGen methodGen = new MethodGen(Const.ACC_PRIVATE, getTypeFromClass(returnType), argsType, argsName, doMethodName, proxyClassName.get(), list, constantPool);

        list.append(new ALOAD(0));
        list.append(new GETFIELD(constantPool.addFieldref(proxyClassName.get(), methodHandleFieldName, SIGNATURE_METHOD_HANDLE)));
        IFNONNULL firstIfnonnull = new IFNONNULL(null);
        list.append(firstIfnonnull);
        list.append(new ALOAD(0));
        list.append(new DUP());
        list.append(new ASTORE(parameters.length == 0 ? 2 : 3));
        list.append(new MONITORENTER());
        InstructionHandle firstHandle = list.append(new ALOAD(0));
        list.append(new GETFIELD(constantPool.addFieldref(proxyClassName.get(), methodHandleFieldName, SIGNATURE_METHOD_HANDLE)));
        IFNONNULL secondIfnonnull = new IFNONNULL(null);
        list.append(secondIfnonnull);
        list.append(new ALOAD(0));
        list.append(factory.createInvoke(MethodHandles.class.getName(), "lookup", new ObjectType(MethodHandles.Lookup.class.getName()), Type.NO_ARGS, Const.INVOKESTATIC));
        list.append(new LDC(constantPool.addClass(method.getDeclaringClass().getName())));
        list.append(new LDC(constantPool.addString(methodName)));

        if (returnType.isPrimitive()) {
            if (returnType.equals(boolean.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Boolean.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else if (returnType.equals(byte.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Byte.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else if (returnType.equals(char.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Character.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else if (returnType.equals(short.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Short.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else if (returnType.equals(int.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Integer.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else if (returnType.equals(long.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Long.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else if (returnType.equals(float.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Float.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else if (returnType.equals(double.class)) {
                list.append(new GETSTATIC(constantPool.addFieldref(Double.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            } else {
                list.append(new GETSTATIC(constantPool.addFieldref(Void.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
            }
        } else {
            list.append(new LDC(constantPool.addClass(returnType.getName())));
        }

        if (parameters.length == 0) {
            list.append(factory.createInvoke(MethodType.class.getName(), METHOD_METHOD_TYPE, new ObjectType(MethodType.class.getName()), new Type[]{Type.CLASS}, Const.INVOKESTATIC));
        } else {
            list.append(new ICONST(parameters.length));
            list.append(new ANEWARRAY(constantPool.addClass(Class.class.getName())));

            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];

                list.append(new DUP());
                list.append(new ICONST(i));
                if (parameter.getType().isPrimitive()) {
                    if (parameter.getType().equals(boolean.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Boolean.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else if (parameter.getType().equals(byte.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Byte.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else if (parameter.getType().equals(char.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Character.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else if (parameter.getType().equals(short.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Short.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else if (parameter.getType().equals(int.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Integer.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else if (parameter.getType().equals(long.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Long.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else if (parameter.getType().equals(float.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Float.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else if (parameter.getType().equals(double.class)) {
                        list.append(new GETSTATIC(constantPool.addFieldref(Double.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    } else {
                        list.append(new GETSTATIC(constantPool.addFieldref(Void.class.getName(), FIELD_TYPE, SIGNATURE_CLASS)));
                    }
                } else {
                    list.append(new LDC(constantPool.addClass(parameter.getType().getName())));
                }
                list.append(new AASTORE());
            }
            list.append(factory.createInvoke(MethodType.class.getName(), METHOD_METHOD_TYPE, new ObjectType(MethodType.class.getName()), new Type[]{Type.CLASS, new ArrayType(Type.CLASS, 1)}, Const.INVOKESTATIC));
        }

        list.append(factory.createInvoke(MethodHandles.Lookup.class.getName(), METHOD_FIND_VIRTUAL, new ObjectType(MethodHandle.class.getName()), new Type[]{Type.CLASS, Type.STRING, new ObjectType(MethodType.class.getName())}, Const.INVOKEVIRTUAL));
        list.append(new ALOAD(1));
        list.append(factory.createInvoke(MethodHandle.class.getName(), METHOD_BIND_TO, new ObjectType(MethodHandle.class.getName()), new Type[]{Type.OBJECT}, Const.INVOKEVIRTUAL));

        list.append(new PUTFIELD(constantPool.addFieldref(proxyClassName.get(), methodHandleFieldName, SIGNATURE_METHOD_HANDLE)));
        InstructionHandle instructionHandle = list.append(new ALOAD(parameters.length == 0 ? 2 : 3));
        secondIfnonnull.setTarget(instructionHandle);
        list.append(new MONITOREXIT());
        GOTO g = new GOTO(null);
        BranchHandle secondHandle = list.append(g);
        InstructionHandle cur = list.append(new ASTORE(parameters.length == 0 ? 3 : 4));
        list.append(new ALOAD(parameters.length == 0 ? 2 : 3));
        list.append(new MONITOREXIT());
        InstructionHandle thirdHandle = list.append(new ALOAD(parameters.length == 0 ? 3 : 4));
        list.append(new ATHROW());
        InstructionHandle handle = list.append(new ALOAD(0));
        g.setTarget(handle);
        firstIfnonnull.setTarget(handle);

        list.setPositions();
        StackMapEntry[] entries = new StackMapEntry[]{
                new StackMapEntry(Const.APPEND_FRAME, instructionHandle.getPosition(), new StackMapType[]{new StackMapType((byte) 7, constantPool.addClass(CLASS_OBJECT), constantPool.getConstantPool())}, new StackMapType[0], constantPool.getConstantPool()),
                new StackMapEntry(Const.SAME_LOCALS_1_STACK_ITEM_FRAME + cur.getPosition() - instructionHandle.getPosition() - 1, cur.getPosition() - instructionHandle.getPosition() - 1, new StackMapType[0], new StackMapType[]{new StackMapType((byte) 7, constantPool.addClass(CLASS_THROWABLE), constantPool.getConstantPool())}, constantPool.getConstantPool()),
                new StackMapEntry(Const.CHOP_FRAME_MAX, handle.getPosition() - cur.getPosition() - 1, new StackMapType[0], new StackMapType[0], constantPool.getConstantPool())
        };

        list.append(new GETFIELD(constantPool.addFieldref(proxyClassName.get(), methodHandleFieldName, SIGNATURE_METHOD_HANDLE)));

        if (parameters.length > 0) {
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                Class<?> type = parameter.getType();

                list.append(new ALOAD(2));
                list.append(new ICONST(i));
                list.append(new AALOAD());
                if (type.isPrimitive()) {
                    if (type.equals(boolean.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Boolean.class.getName())));
                        list.append(factory.createInvoke(Boolean.class.getName(), METHOD_BOOLEAN_VALUE, Type.BOOLEAN, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else if (type.equals(byte.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Byte.class.getName())));
                        list.append(factory.createInvoke(Byte.class.getName(), METHOD_BYTE_VALUE, Type.BYTE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else if (type.equals(char.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Character.class.getName())));
                        list.append(factory.createInvoke(Character.class.getName(), METHOD_CHAR_VALUE, Type.CHAR, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else if (type.equals(short.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Short.class.getName())));
                        list.append(factory.createInvoke(Short.class.getName(), METHOD_SHORT_VALUE, Type.SHORT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else if (type.equals(int.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Integer.class.getName())));
                        list.append(factory.createInvoke(Integer.class.getName(), METHOD_INT_VALUE, Type.INT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else if (type.equals(long.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Long.class.getName())));
                        list.append(factory.createInvoke(Long.class.getName(), METHOD_LONG_VALUE, Type.LONG, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else if (type.equals(float.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Float.class.getName())));
                        list.append(factory.createInvoke(Float.class.getName(), METHOD_FLOAT_VALUE, Type.FLOAT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else if (type.equals(double.class)) {
                        list.append(new CHECKCAST(constantPool.addClass(Double.class.getName())));
                        list.append(factory.createInvoke(Double.class.getName(), METHOD_DOUBLE_VALUE, Type.DOUBLE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    } else {
                        // ignore since the parameter type should not be void
                        throw new RuntimeException("wrong parameter type [" + type.getName() + "]");
                    }
                } else {
                    list.append(new CHECKCAST(constantPool.addClass(type.getName())));
                }
            }
            Type[] types = new Type[parameters.length];
            for (int i = 0; i < types.length; i++) {
                types[i] = getTypeFromClass(parameters[i].getType());
            }
            list.append(factory.createInvoke(MethodHandle.class.getName(), METHOD_INVOKE_EXACT, getTypeFromClass(returnType), types, Const.INVOKEVIRTUAL));
        } else {
            list.append(factory.createInvoke(MethodHandle.class.getName(), METHOD_INVOKE_EXACT, getTypeFromClass(returnType), Type.NO_ARGS, Const.INVOKEVIRTUAL));
        }

        if (returnType.isPrimitive()) {
            if (returnType.equals(boolean.class)) {
                list.append(new IRETURN());
            } else if (returnType.equals(byte.class)) {
                list.append(new IRETURN());
            } else if (returnType.equals(char.class)) {
                list.append(new IRETURN());
            } else if (returnType.equals(short.class)) {
                list.append(new IRETURN());
            } else if (returnType.equals(int.class)) {
                list.append(new IRETURN());
            } else if (returnType.equals(long.class)) {
                list.append(new LRETURN());
            } else if (returnType.equals(float.class)) {
                list.append(new FRETURN());
            } else if (returnType.equals(double.class)) {
                list.append(new DRETURN());
            } else {
                list.append(new RETURN());
            }
        } else {
            list.append(new ARETURN());
        }

        // fixme: how to calculate the length of StackMap?
        methodGen.addCodeAttribute(new StackMap(constantPool.addUtf8(STACK_MAP_TABLE), 15, entries, constantPool.getConstantPool()));
        methodGen.addException(CLASS_THROWABLE);
        methodGen.addExceptionHandler(firstHandle, secondHandle, cur, null);
        methodGen.addExceptionHandler(cur, thirdHandle, cur, null);

        methodGen.setMaxStack();
        methodGen.setMaxLocals();

        classGen.addMethod(methodGen.getMethod());
        list.dispose();
    }

}
