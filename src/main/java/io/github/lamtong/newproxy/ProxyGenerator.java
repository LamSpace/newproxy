/**
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

package io.github.lamtong.newproxy;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.classfile.*;
import com.sun.org.apache.bcel.internal.generic.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;

import static io.github.lamtong.newproxy.Constants.*;


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
 * public final class $NewProxy0 implements Foo {
 *     private static final Method m0;
 *     private static final Method m1;
 *     private static final Method m2;
 *     // other Method type fields
 *     private final InvocationHandler handler;
 *
 *     static {
 *         try {
 *             m0 = Class.forName("java.lang.Object").getMethod("equals", Object.class);
 *             m1 = Class.forName("java.lang.Object").getMethod("hashCode");
 *             m2 = Class.forName("java.lang.Object").getMethod("toString");
 *             // other static fields initialization
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public $NewProxy0(InvocationHandler handler) {
 *         this.handler = handler;
 *     }
 *
 *     public final boolean equals(Object o) {
 *         try {
 *             return (Boolean) this.handler.invoke(this, m0, new Object[]{o});
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public final int hashCode() {
 *         try {
 *             return (Integer) this.handler.invoke(this, m1, (Object[]) null);
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public final String toString() {
 *         try {
 *             return (String) this.handler.invoke(this, m2, (Object[]) null);
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     // other methods implementation from interface Foo here
 *
 * }
 * </pre></blockquote>
 * Note that class format can be divided into four parts as below:
 * <ol>
 *     <li>Static variables in a proxy class.</li>
 *     <li>Static variables initialization in a proxy class, using try-catch block.</li>
 *     <li>Default constructor of the proxy class with public modifier which initialize an instance field
 *     with name "handler", whose type is {@link InvocationHandler}.</li>
 *     <li>Implementation of methods overridden from interfaces and {@code equals}, {@code hashCode}
 *     and {@code toString} from {@code java.lang.Object}.</li>
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
 *     parameter of type {@link InvocationHandler}, and that parameter will be assigned to an instance filed
 *     of type {@link InvocationHandler} with name "handler", which process methods invocations of a proxy class.</li>
 *     <li>Implements all methods originate from interfaces with three methods from {@code java.lang.Object}:
 *     {@code equals}, {@code hashCode} and {@code toString}.</li>
 *     <li>Exports generated proxy class in an array of byte.</li>
 * </ol>
 *
 * @author Lam Tong
 * @version 0.0.1
 * @see NewProxy
 * @since 0.0.1
 */
public final class ProxyGenerator {

    private static boolean DUMP = false;

    private static String DEFAULT_DIR = "newproxy";

    static {
        String dump = System.getProperty("io.github.lamtong.newproxy.dump");
        if (dump != null && dump.equalsIgnoreCase("true")) {
            DUMP = true;
        }
        String dir = System.getProperty("io.github.lamtong.newproxy.dir");
        if (dir != null && !dir.isEmpty()) {
            DEFAULT_DIR = dir;
        }
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
     * Generates a dynamic proxy class with specified proxy class name, access flags, and interfaces passed in.
     * This method works as mentioned above.
     *
     * @param proxyClass the name of the proxy class should be
     * @param accessFlag access flags of the proxy class
     * @param interfaces a list of interfaces to be implemented
     * @return an array of byte, which represents a dynamic proxy class.
     * @throws RuntimeException if an exception occurs when creating a dynamic proxy class.
     */
    public static byte[] generate(String proxyClass, int accessFlag, Class<?>[] interfaces) {
        System.out.println("Generating proxy class: " + proxyClass);
        proxyClassName.set(proxyClass);
        METHOD_CACHE.set(new LinkedHashMap<>());
        ClassGen classGen = new ClassGen(proxyClassName.get(), Object.class.getName(), "<generated>", accessFlag, extractNamesFromInterfaces(interfaces));
        try {
            ConstantPoolGen constantPool = classGen.getConstantPool();
            classGen.setMinor(Const.MINOR_1_8);
            classGen.setMajor(Const.MAJOR_1_8);

            // generates static variables for proxy class
            generateStaticVariables(classGen, constantPool, interfaces);

            // initialize static variable for proxy class in static initializer
            initializeStaticVariables(classGen, constantPool);

            // generate default constructor for proxy class
            generateDefaultConstructor(classGen, constantPool);

            // generate methods to invoke
            generateMethods(classGen, constantPool);
        } catch (Exception e) {
            throw new RuntimeException("exception with message: " + e.getMessage());
        } finally {
            proxyClassName.remove();
            METHOD_CACHE.remove();
        }
        JavaClass javaClass = classGen.getJavaClass();
        // can debug javaClass object here
        if (DUMP) {
            try {
                javaClass.dump(DEFAULT_DIR + File.separator + proxyClass.substring(proxyClass.lastIndexOf('.') + 1) + ".class");
            } catch (IOException e) {
                throw new RuntimeException("exception when dumping class: " + proxyClass + ", message: " + e.getMessage());
            }
        }
        return javaClass.getBytes();
    }

    /**
     * Extracts the name of interfaces and returns those names in an array of {@code String}.
     *
     * @param interfaces a list of interface which needs to extract their name
     * @return an array of String, representing name of interfaces.
     */
    private static String[] extractNamesFromInterfaces(Class<?>[] interfaces) {
        String[] res = new String[interfaces.length];
        for (int i = 0; i < interfaces.length; i++) {
            res[i] = interfaces[i].getName();
        }
        return res;
    }

    /**
     * Generates default class variables for all dynamic proxy class since these three variables
     * are always required to be present in a proxy class, which can be list as below.
     * <ul>
     *     <li>m0 -> Class.forName("java.lang.Object").getMethod("equals", Object.class)</li>
     *     <li>m1 -> Class.forName("java.lang.Object").getMethod("hashCOde")</li>
     *     <li>m2 -> Class.forName("java.lang.Object").getMethod("toString")</li>
     * </ul>
     *
     * @param classGen     An instance of ClassGen representing the class for which the methods are to be generated.
     * @param constantPool An instance of ConstantPoolGen representing the constant pool for the class.
     */
    private static void generateDefaultStaticVariables(ClassGen classGen, ConstantPoolGen constantPool) {
        int modifiers = Const.ACC_PRIVATE | Const.ACC_STATIC | Const.ACC_FINAL;
        ObjectType objectType = new ObjectType(Method.class.getName());
        Field m0 = new FieldGen(modifiers, objectType, "m0", constantPool).getField(),
                m1 = new FieldGen(modifiers, objectType, "m1", constantPool).getField(),
                m2 = new FieldGen(modifiers, objectType, "m2", constantPool).getField();
        classGen.addField(m0);
        classGen.addField(m1);
        classGen.addField(m2);
    }

    /**
     * Generates static class variables of this proxy class with type {@link Method}, annotated as {@code public, static, final}.
     * This method adds method handles from the specified interfaces into the proxy class, except where there are two methods
     * with the same method name, identical parameters (considering number, type, and order), and the same return type.
     * In such cases, the method from the first interface is included, and the second is ignored.
     *
     * @param classGen     A ClassGen instance representing the class for which methods are to be generated.
     * @param constantPool A ConstantPoolGen instance representing the constant pool for the class.
     * @param interfaces   A list of interfaces to be implemented.
     */
    private static void generateStaticVariables(ClassGen classGen, ConstantPoolGen constantPool, Class<?>[] interfaces) {
        generateDefaultStaticVariables(classGen, constantPool);
        try {
            Class<?> clazz = Class.forName(Object.class.getName());
            METHOD_CACHE.get().put(clazz.getMethod("equals", Object.class), "m0");
            METHOD_CACHE.get().put(clazz.getMethod("hashCode"), "m1");
            METHOD_CACHE.get().put(clazz.getMethod("toString"), "m2");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        List<Method> methods = new ArrayList<>();
        for (Class<?> clazz : interfaces) {
            methods.addAll(Arrays.asList(clazz.getMethods()));
        }
        if (methods.isEmpty()) {
            return;
        }
        int modifiers = Const.ACC_PRIVATE | Const.ACC_STATIC | Const.ACC_FINAL;
        ObjectType type = new ObjectType(Method.class.getName());
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
     * Acquires signature of the specified method object according to its method name, parameter and return type,
     * combine in a String.
     *
     * @param method specified method to acquire signature
     * @return method signature in String
     */
    private static String getMethodSignature(Method method) {
        String name = method.getName();
        Class<?> returnType = method.getReturnType();
        Parameter[] parameters = method.getParameters();
        return name + ";" + returnType + ";" + Arrays.toString(parameters);
    }

    /**
     * Initializes class variables in a proxy class, using {@code try-catch} block in a static initializer.
     *
     * @param classGen     An instance of ClassGen representing the class for which the methods are to be generated.
     * @param constantPool An instance of ConstantPoolGen representing the constant pool for the class.
     */
    private static void initializeStaticVariables(ClassGen classGen, ConstantPoolGen constantPool) {
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
                    Class<?> wrapperClass = transformPrimitiveToWrapper(type);
                    list.append(new GETSTATIC(constantPool.addFieldref(wrapperClass.getName(), "TYPE", SIGNATURE_CLASS)));
                } else {
                    list.append(new LDC(constantPool.addClass(parameterTypeName)));
                }
                list.append(new AASTORE());
            }
            list.append(factory.createInvoke(CLASS_CLASS, METHOD_GET_METHOD, new ObjectType(Method.class.getName()), new Type[]{Type.STRING, new ArrayType(Type.CLASS, 1)}, Const.INVOKEVIRTUAL));
            try_end = list.append(new PUTSTATIC(constantPool.addFieldref(proxyClassName.get(), METHOD_CACHE.get().get(method), SIGNATURE_METHOD)));
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
     * Transforms a primitive type into a wrapper type and returns a Class object of that wrapper type.
     *
     * @param type primitive type to be transformed
     * @return Class object of the wrapper type
     */
    private static Class<?> transformPrimitiveToWrapper(Class<?> type) {
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
     * Generates default constructor of a proxy class with a parameter of type {@link InvocationHandler}. Before
     * create the constructor, an instance field of type {@link InvocationHandler} with name {@code "handler"} should
     * be added into this proxy class first.
     *
     * @param classGen     An instance of ClassGen representing the class for which the methods are to be generated.
     * @param constantPool An instance of ConstantPoolGen representing the constant pool for the class.
     */
    private static void generateDefaultConstructor(ClassGen classGen, ConstantPoolGen constantPool) {
        FieldGen handlerFieldGen = new FieldGen(Const.ACC_PRIVATE | Const.ACC_FINAL, new ObjectType(InvocationHandler.class.getName()), FIELD_HANDLER, constantPool);
        classGen.addField(handlerFieldGen.getField());

        InstructionList list = new InstructionList();
        InstructionFactory factory = new InstructionFactory(constantPool);
        MethodGen methodGen = new MethodGen(Const.ACC_PUBLIC, Type.VOID, new Type[]{new ObjectType(InvocationHandler.class.getName())}, new String[]{FIELD_HANDLER}, METHOD_INIT, proxyClassName.get(), list, constantPool);
        list.append(new ALOAD(0));
        list.append(factory.createInvoke(Object.class.getName(), METHOD_INIT, Type.VOID, Type.NO_ARGS, Const.INVOKESPECIAL));
        list.append(new ALOAD(0));
        list.append(new ALOAD(1));
        list.append(new PUTFIELD(constantPool.addFieldref(proxyClassName.get(), FIELD_HANDLER, SIGNATURE_INVOCATION_HANDLER)));
        list.append(InstructionConst.RETURN);

        methodGen.setMaxLocals();
        methodGen.setMaxStack();
        classGen.addMethod(methodGen.getMethod());
        list.dispose();
    }

    /**
     * Generates the implementation for all methods defined in the specified interfaces,
     * including the additional methods: equals, hashCode, and toString inherited from java.lang.Object.
     *
     * @param classGen     An instance of ClassGen representing the class for which the methods are to be generated.
     * @param constantPool An instance of ConstantPoolGen representing the constant pool for the class.
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
                if (type.isPrimitive()) {
                    if (type == boolean.class) {
                        types[i] = Type.BOOLEAN;
                    } else if (type == byte.class) {
                        types[i] = Type.BYTE;
                    } else if (type == short.class) {
                        types[i] = Type.SHORT;
                    } else if (type == char.class) {
                        types[i] = Type.CHAR;
                    } else if (type == int.class) {
                        types[i] = Type.INT;
                    } else if (type == long.class) {
                        types[i] = Type.LONG;
                    } else if (type == float.class) {
                        types[i] = Type.FLOAT;
                    } else if (type == double.class) {
                        types[i] = Type.DOUBLE;
                    }
                } else {
                    types[i] = new ObjectType(type.getName());
                }
            }
            InstructionFactory factory = new InstructionFactory(constantPool);

            Type return_type = getReturnType(returnType);
            MethodGen methodGen = new MethodGen(Const.ACC_PUBLIC | Const.ACC_FINAL, return_type, types, null, method.getName(), proxyClassName.get(), list, constantPool);
            int additional = 0;
            InstructionHandle try_start = list.append(new ALOAD(0));
            list.append(new GETFIELD(constantPool.addFieldref(proxyClassName.get(), FIELD_HANDLER, SIGNATURE_INVOCATION_HANDLER)));
            list.append(new ALOAD(0));
            list.append(new GETSTATIC(constantPool.addFieldref(proxyClassName.get(), METHOD_CACHE.get().get(method), SIGNATURE_METHOD)));
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
            list.append(factory.createInvoke(InvocationHandler.class.getName(), METHOD_INVOKE, Type.OBJECT, new Type[]{Type.OBJECT, new ObjectType(Method.class.getName()), new ArrayType(Type.OBJECT, 1)}, Const.INVOKEINTERFACE));
            InstructionHandle try_end;
            if (returnType.isPrimitive()) {
                if (returnType == boolean.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Boolean.class.getName())));
                    list.append(factory.createInvoke(Boolean.class.getName(), "booleanValue", Type.BOOLEAN, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == byte.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Byte.class.getName())));
                    list.append(factory.createInvoke(Byte.class.getName(), "byteValue", Type.BYTE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == short.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Short.class.getName())));
                    list.append(factory.createInvoke(Short.class.getName(), "shortValue", Type.SHORT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == char.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Character.class.getName())));
                    list.append(factory.createInvoke(Character.class.getName(), "charValue", Type.CHAR, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == int.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Integer.class.getName())));
                    list.append(factory.createInvoke(Integer.class.getName(), "intValue", Type.INT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.IRETURN);
                } else if (returnType == long.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Long.class.getName())));
                    list.append(factory.createInvoke(Long.class.getName(), "longValue", Type.LONG, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.LRETURN);
                } else if (returnType == float.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Float.class.getName())));
                    list.append(factory.createInvoke(Float.class.getName(), "floatValue", Type.FLOAT, Type.NO_ARGS, Const.INVOKEVIRTUAL));
                    try_end = list.append(InstructionConst.FRETURN);
                } else if (returnType == double.class) {
                    list.append(new CHECKCAST(constantPool.addClass(Double.class.getName())));
                    list.append(factory.createInvoke(Double.class.getName(), "doubleValue", Type.DOUBLE, Type.NO_ARGS, Const.INVOKEVIRTUAL));
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
     * Retrieves the corresponding Type object based on the given return type Class.
     * If the type is primitive, returns the matching Type enum constant;
     * if it is a reference type, returns an ObjectType representing the fully qualified name.
     *
     * @param returnType The return type as a Class object, which can be either a primitive or a reference type.
     * @return The corresponding Type object - a Type enum constant for primitives, an ObjectType for reference types.
     */
    private static Type getReturnType(Class<?> returnType) {
        Type return_type;
        // Check if the return type is primitive
        if (returnType.isPrimitive()) {
            // Return the matching Type enum constant based on the primitive type
            if (returnType == boolean.class) {
                return_type = Type.BOOLEAN;
            } else if (returnType == byte.class) {
                return_type = Type.BYTE;
            } else if (returnType == short.class) {
                return_type = Type.SHORT;
            } else if (returnType == char.class) {
                return_type = Type.CHAR;
            } else if (returnType == int.class) {
                return_type = Type.INT;
            } else if (returnType == long.class) {
                return_type = Type.LONG;
            } else if (returnType == float.class) {
                return_type = Type.FLOAT;
            } else if (returnType == double.class) {
                return_type = Type.DOUBLE;
            } else {
                // Default to VOID if not a common primitive type
                return_type = Type.VOID;
            }
        } else {
            // For reference types, create an ObjectType with the fully qualified name
            return_type = new ObjectType(returnType.getName());
        }
        return return_type;
    }

}
