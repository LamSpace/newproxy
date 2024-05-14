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

import sun.misc.VM;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;
import sun.reflect.misc.ReflectUtil;
import sun.security.util.SecurityConstants;

import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * {@code Dynamic Proxy} is an amazing mechanism for generating proxy objects at runtime, which can intercept and
 * enhance behaviors without modifying the original class.<br/>
 * Currently, {@link Proxy} in {@code JDK} and {@code CGLIB} both provide capabilities to create dynamic proxy
 * class and instances.<br/>
 * To create a proxy class for some interface {@code Foo} via JDK:
 * <blockquote><pre>
 *     InvocationHandler handler = new DefaultInvocationHandler(...);
 *     Class<?> proxyClass = Proxy.getProxyClass(Foo.class.getClassLoader(), Foo.class);
 *     Foo f = (Foo) proxyClass.getConstructor(InvocationHandler.class).newInstance(handler);
 * </pre></blockquote>
 * or more simply:
 * <blockquote><pre>
 *     Foo f = (Foo) Proxy.newProxyInstance(Foo.class.getClassLoader(),
 *                                          new Class<?>[] {Foo.class},
 *                                          handler);
 * </pre></blockquote>
 * <br/><br/>
 * <h3>Dynamic Proxy Class</h3>
 * A dynamic proxy class (simply referred to as a proxy class below) is a class that implements a list of interfaces
 * specified at runtime when the class is created, with behavior as described below. A proxy interface is such an
 * interface implemented by a proxy class. A proxy instance is an instance of a proxy class. Each proxy
 * instance has an associated invocation handler object, which implements the interface {@link InvocationInterceptor}.
 * A method invocation on a proxy instance through one of its proxy interfaces will be dispatched to the
 * {@link InvocationInterceptor#intercept(Object, MethodDecorator, Object[])} method of the instance's invocation handler,
 * passing the proxy instance, a {@link java.lang.reflect.Method} object identifying the method that was invoked,
 * and an array of type {@link Object} containing the arguments. The invocation handler processes the encoded method
 * invocation as appropriate and the result it returns will be returned as the result of the method invocation
 * on the proxy instance.<br/><br/>
 * A proxy class has the following properties:
 * <ul>
 *     <li>Proxy class are public, final and not abstract if all proxy interfaces are public.</li>
 *     <li>Proxy class are non-public, final and not abstract if any of the proxy interfaces is non-public.</li>
 *     <li>The unqualified name of a proxy class is unspecified. The space of class names that begins with the
 *     String {@code "$NewProxy"} should be, however, revered for proxy classes.</li>
 *     <li>A proxy class implements exactly the interfaces specified at its creation, in the same order.</li>
 *     <li>If a proxy class implements a non-public interface, then it will be defined in the same package as
 *     that interface. Otherwise, the package of a proxy class is also unspecified. Note that package sealing will
 *     not prevent a proxy class from being successfully defined in a particular package at runtime, and neither
 *     will classes defined by the same class loader and the same package with particular signers.</li>
 *     <li>Since a proxy class implements all the interfaces specified at its creation, invoking {@code getInterfaces}
 *     on its {@code Class} object will return an array containing the same list of interfaces (in the order at its
 *     creation), invoking {@code getMethods} on its {@code Class} object will return an array of {@code Method}
 *     objects that include all the methods in those interfaces, and invoking {@code getMethod} will find
 *     methods in the proxy interfaces as would be expected.</li>
 *     <li>The {@code isProxyClass} method will return true if it is passed a proxy class -- a class returned by
 *     {@code getProxyClass} or the class of an object returned by {@code newProxyInstance} -- and false
 *     otherwise.</li>
 *     <li>The {@link java.security.ProtectionDomain} of a proxy class is the same as that of system classes
 *     loaded by the bootstrap class loader, such as {@link java.lang.Object}, because the code for a proxy class
 *     is generated by trusted system code. This protection domain will typically be grated
 *     {@link java.security.AllPermission}.</li>
 *     <li>Each proxy class has one public constructor that takes one argument, an implementation of the interface
 *     {@link InvocationInterceptor}, to set the invocation handler for a proxy instance. Rather than having to use
 *     {@code reflection API} to access the public constructor, a proxy instance can be also be created by calling
 *     the {@link #newProxyInstance(ClassLoader, InvocationInterceptor, Class[])} method, which combines the actions of
 *     calling {@link #getProxyClass(ClassLoader, Class[])} with invoking the constructor with an invocation
 *     handler.</li>
 * </ul>
 * <br/>
 * A proxy instance has the following properties:
 * <ul>
 *     <li>Given a proxy instance {@code proxy} and one of the interfaces implemented by its proxy class {@code Foo},
 *     the following expression will return true:
 *     <blockquote><pre>
 *         proxy instanceof Foo
 *     </pre></blockquote>
 *     and the following cast operation will succeed (rather than throwing a {@link ClassCastException})
 *     <blockquote><pre>
 *         (Foo) proxy
 *     </pre></blockquote></li>
 *     <li>Each proxy instance has an associated invocation handler, the one that was passed to its constructor.
 *     The static {@link #getInvocationHandler(Object)} method will return the invocation handler associated with
 *     the proxy instance passed as its argument.</li>
 *     <li>An interface method invocation on a proxy instance will be encoded and dispatched to the invocation
 *     handler's {@link InvocationInterceptor#intercept(Object, MethodDecorator, Object[])} method as described in the documentation
 *     for that method.</li>
 *     <li>An invocation of the {@code equals}, {@code hashCode} or {@code toString} methods declared in
 *     {@link Object} on a proxy instance will be encoded and dispatched to the invocation handler's
 *     {@link InvocationInterceptor#intercept(Object, MethodDecorator, Object[])} method in the same manner as interface method
 *     invocations are encoded and dispatched, as described above. The declaring class the {@code Method} object class
 *     passed to {@code invoke} will be {@link java.lang.Object}. Other public methods of a proxy instance
 *     inherited from {@link java.lang.Object} ara not overridden by a proxy class, so invocation of those
 *     methods behave like they do for instance of {@link java.lang.Object}.</li>
 * </ul>
 * <br/>
 * <h3>Methods Duplicated in Multiple Proxy Interfaces</h3>
 *
 * <p>When two or more interfaces of a proxy class contain a method with the same name and parameter signature,
 * the order of the proxy class's interfaces becomes significant. When such a <i>duplicate method</i>
 * is invoked on a proxy instance, the {@code Method} object passed to the invocation handler will not
 * necessarily be the one whose declaring class is assignable from the reference type of the interface
 * that the proxy's method was invoked through. This limitation exists because the corresponding method
 * implementation in the generated proxy class cannot determine which interface it was invoked through.
 * Therefore, when a duplicate method is invoked on a proxy instance, the {@code Method} object for the
 * method in the foremost interface that contains the method (either directly or inherited through a superinterface)
 * in the proxy class's list of interfaces is passed to the invocation handler's {@code invoke} method,
 * regardless of the reference type through which the method invocation occurred.
 *
 * <p>If a proxy interface contains a method with the same name and parameter signature as the {@code hashCode},
 * {@code equals}, or {@code toString} methods of {@code java.lang.Object}, when such a method is invoked
 * on a proxy instance, the {@code Method} object passed to the invocation handler will have
 * {@code java.lang.Object} as its declaring class. In other words, the public, non-final methods of
 * {@code java.lang.Object} logically precede all the proxy interfaces for the determination of which
 * {@code Method} object to pass to the invocation handler.
 *
 * <p>Note also that when a duplicate method is dispatched to an invocation handler, the {@code invoke}
 * method may only throw checked exception types that are assignable to one of the exception types in the
 * {@code throws} clause of the method in <i>all</i> of the proxy interfaces that it can be invoked through. If the
 * {@code invoke} method throws a checked exception that is not assignable to any of the exception types
 * declared by the method in one of the proxy interfaces that it can be invoked through, then an unchecked
 * {@code UndeclaredThrowableException} will be thrown by the invocation on the proxy instance. This restriction
 * means that not all the exception types returned by invoking {@code getExceptionTypes} on the {@code Method} object
 * passed to the {@code invoke} method can necessarily be thrown successfully by the {@code invoke} method.<br/>
 *
 * <h3>Difference Between {@link Proxy} And {@link NewProxy}</h3>
 * The biggest difference between {@link Proxy} and {@link NewProxy} is that a proxy class generated by {@link Proxy}
 * extends {@link Proxy} itself and implements all specified interfaces while a proxy class generated by
 * {@link NewProxy} only implements all interfaces, not extending any class. Due to the fact that a proxy class
 * generated by {@link Proxy} extends {@link Proxy}, only interfaces are supported to create dynamic proxy class.
 * Though {@link NewProxy} only supports creating dynamic proxy class for specified interfaces as {@link Proxy}
 * does, it will be improved to add support to create dynamic proxy class with at most one class with interfaces.<br/>
 * Besides, {@link NewProxy} does not contain the invocation handler instance itself rather than {@link Proxy} does.
 * When creating a proxy class using {@link Proxy}, invocation handler is hold as an instance field of {@link Proxy}.
 * But in {@link NewProxy}, invocation handler instance is hold by the proxy class itself since the proxy class
 * does not extend any class and only implements specified interfaces. By default, the name of invocation handler
 * in the proxy class created by {@link NewProxy} is {@code "handler"}.<br/>
 * Here is an example. If interface {@code Foo} needs to be implemented using {@link Proxy}, generated class can be
 * list as below ({@code Foo} is a public interface):
 * <blockquote><pre>
 * public final class $Proxy0 extends Proxy implements Foo {
 *     private static final Method m0;
 *     private static final Method m1;
 *     private static final Method m2;
 *     // other Method type static fields
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
 *     public $Proxy0(InvocationHandler h) {
 *         super(h);
 *     }
 *
 *     public final boolean equals(Object o) {
 *         try {
 *             return (Boolean) super.h.invoke(this, m0, new Object[]{o});
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     public final int hashCode() {
 *         try {
 *             return (Integer) super.h.invoke(this, m1, (Object[]) null);
 *         } catch (Exception e） {
 *             // process exception here
 *         }
 *     }
 *
 *     public final String toString() {
 *         try {
 *             return (String) super.h.invoke(this, m2, (Object[]) null);
 *         } catch (Exception e) {
 *             // process exception here
 *         }
 *     }
 *
 *     // other methods implementation from interface Foo here
 *
 * }
 * </pre></blockquote>
 * While {@link NewProxy} will generate class as below:
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
 * </pre></blockquote><br/>
 * Overall, {@link Proxy} class not only can be regarded as a container to provide invocation handler, but also
 * a utility class to create dynamic proxy class and instances, to check if an object is a proxy instance or not,
 * or to get the invocation handler instance, etc. Nevertheless, {@link NewProxy} can be regarded as a utility
 * class only, which provides features as below:
 * <ul>
 *     <li>A proxy class can be acquired by invoking {@link #getProxyClass(ClassLoader, Class[])}.</li>
 *     <li>A proxy instance can be acquired by invoking {@link
 *     #newProxyInstance(ClassLoader, InvocationInterceptor, Class[])}.</li>
 *     <li>Checks if an object is a proxy instance or not by invoking {@link #isProxyInstance(Object)}.</li>
 *     <li>Checks if a {@code Class} object is a proxy class or not by invoking {@link #isProxyClass(Class)}</li>
 *     <li>Acquires the invocation handler instance from a proxy instance by invoking
 *     {@link #getInvocationHandler(Object)}</li>
 * </ul>
 * <br/>
 * <h3>Underlying Supports</h3>
 * {@link NewProxy} generates dynamic proxy class through {@link ProxyGenerator} which depends on {@code Byte Code
 * Engineering Library} (simply called <a href="https://commons.apache.org/proper/commons-bcel/">BCEL</a>). BCEL is
 * intended to give users a convenient way to analyze, create and manipulate (binary) Java Class files (those ending
 * with .class). Classes are represented by objects which contain all the symbolic information of the given class:
 * methods, fields and byte code instructions, in particular.
 * <a href="https://commons.apache.org/proper/commons-bcel/">More information here!</a>
 *
 * @author Lam Tong
 * @version 1.0.0
 * @see Proxy
 * @see ProxyGenerator
 * @since 1.0.0
 */
public final class NewProxy {

    private NewProxy() {
    }

    /**
     * a cache of proxy classes
     */
    private static final WeakCache<ClassLoader, Class<?>[], Class<?>> proxyClassCache =
            new WeakCache<>(new KeyFactory(), new ProxyClassFactory());

    /**
     * a method to define class via {@link Proxy#defineClass0(ClassLoader, String, byte[], int, int)}
     */
    private static Method defineClass;

    static {
        // static initializer to acquire a Method object in Proxy with the name "defineClass0"
        // to define Class from generated bytes.
        Class<Proxy> clazz = Proxy.class;
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if ("defineClass0".equals(method.getName()) &&
                    Modifier.isNative(modifiers) && Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers)) {
                method.setAccessible(true);
                defineClass = method;
                break;
            }
        }
        if (defineClass == null) {
            throw new RuntimeException("can not define dynamic proxy class");
        }
    }

    @SuppressWarnings(value = {"DuplicatedCode"})
    @CallerSensitive
    public static Object newProxyInstance(ClassLoader classLoader,
                                          InvocationInterceptor interceptor,
                                          Class<?>... classes) {
        Objects.requireNonNull(interceptor);
        if (classes == null) {
            throw new NullPointerException("classes is null");
        }
        if (classes.length == 0) {
            throw new IllegalArgumentException("classes.length == 0");
        }
        if (!checkClasses(classes)) {
            throw new IllegalArgumentException("classes contains more than one class");
        }
        final Class<?>[] clonedClasses = new Class[classes.length];
        System.arraycopy(classes, 0, clonedClasses, 0, classes.length);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkProxyAccess(Reflection.getCallerClass(), classLoader, classes);
        }
        try {
            Class<?> generatedProxyClass = getProxyClass0(classLoader, clonedClasses);
            if (sm != null) {
                checkNewProxyPermission(Reflection.getCallerClass(), generatedProxyClass);
            }
            Constructor<?> constructor = generatedProxyClass.getConstructor(InvocationInterceptor.class);
            // Access control is required whether the constructor is public or not.
            // Especially when specified classes contain non-public classes,
            // the constructor is not accessible while the constructor's modifier is public.
            // It's strange.
            // Maybe that is a difference between Proxy and NewProxy.
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                constructor.setAccessible(true);
                return null;
            });
            return constructor.newInstance(interceptor);
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException e) {
            throw new InternalError(e.toString(), e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new InternalError(t.toString(), t);
            }
        }
    }

    @SuppressWarnings(value = {"DuplicatedCode"})
    @CallerSensitive
    public static Class<?> getProxyClass(ClassLoader classLoader,
                                         Class<?>... classes) {
        if (classes == null) {
            throw new NullPointerException("classes is null");
        }
        if (classes.length == 0) {
            throw new IllegalArgumentException("classes.length == 0");
        }
        if (!checkClasses(classes)) {
            throw new IllegalArgumentException("classes contains more than one class");
        }
        final Class<?>[] clonedClasses = new Class[classes.length];
        System.arraycopy(classes, 0, clonedClasses, 0, classes.length);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkProxyAccess(Reflection.getCallerClass(), classLoader, classes);
        }
        try {
            return getProxyClass0(classLoader, clonedClasses);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> getProxyClass0(ClassLoader classLoader,
                                           Class<?>... classes) {
        if (classes.length > 65535) {
            throw new IllegalArgumentException("classes limit exceeded");
        }

        // If the proxy class defined by the given loader implementing the given classes exists, this will
        // simply return the cached copy; otherwise, it will create the proxy class via the ProxyClassFactory
        return proxyClassCache.get(classLoader, classes);
    }

    /**
     * Checks if specified classes are all interfaces, or the specified classes contain one class at most,
     * since {@link NewProxy} supports to generate dynamic class for class.
     *
     * @param classes classes to be checked
     * @return true if and only if specified classes contain one class at most; otherwise, returns false.
     */
    @SuppressWarnings(value = {"BooleanMethodIsAlwaysInverted"})
    private static boolean checkClasses(Class<?>[] classes) {
        int length = classes.length;
        long number = Arrays.stream(classes).filter(Class::isInterface).count();
        return length - number <= 1;
    }

    public static boolean isProxyClass(Class<?> clazz) {
        Objects.requireNonNull(clazz);
        Proxied proxied = clazz.getAnnotation(Proxied.class);
        if (proxied == null) {
            return false;
        }
        return InvocationDispatcher.class.isAssignableFrom(clazz) && proxyClassCache.containsValue(clazz);
    }

    public static boolean isProxyInstance(Object o) {
        Objects.requireNonNull(o);
        return isProxyClass(o.getClass());
    }
    @CallerSensitive
    public static InvocationInterceptor getInvocationHandler(Object o)
            throws NoSuchFieldException,
            IllegalAccessException {
        Objects.requireNonNull(o);
        Class<?> clazz = o.getClass();
        if (!isProxyClass(clazz)) {
            throw new IllegalArgumentException("not a proxy instance");
        }
        Field field = clazz.getDeclaredField(Constants.FIELD_INTERCEPTOR);
        field.setAccessible(true);
        Object obj = field.get(o);
        if (!(obj instanceof InvocationInterceptor)) {
            throw new IllegalArgumentException("Wrong type of field \"interceptor\"");
        }
        return (InvocationInterceptor) obj;
    }

    private static void checkProxyAccess(Class<?> caller,
                                         ClassLoader loader,
                                         Class<?>... classes) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            ClassLoader ccl = caller.getClassLoader();
            if (VM.isSystemDomainLoader(loader) && !VM.isSystemDomainLoader(ccl)) {
                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);
            }
            ReflectUtil.checkProxyPackageAccess(ccl, classes);
        }

    }

    private static void checkNewProxyPermission(Class<?> caller,
                                                Class<?> proxyClass) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            if (ReflectUtil.isNonPublicProxyClass(proxyClass)) {
                ClassLoader ccl = caller.getClassLoader();
                ClassLoader pcl = proxyClass.getClassLoader();

                // do permission check if the caller is in a different runtime package of the proxy class
                int n = proxyClass.getName().lastIndexOf(".");
                String pkg = (n == -1) ? "" : proxyClass.getName().substring(0, n);

                n = caller.getName().lastIndexOf(".");
                String callerPkg = (n == -1) ? "" : caller.getName().substring(0, n);

                if (pcl != ccl || !pkg.equals(callerPkg)) {
                    sm.checkPermission(new ReflectPermission("newProxyInPackage." + pkg));
                }
            }
        }
    }

    /**
     * A key used for proxy class with one implemented interface.
     */
    private static final class Key1 extends WeakReference<Class<?>> {

        private final int hash;

        Key1(Class<?> intf) {
            super(intf);
            this.hash = intf.hashCode();
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            Class<?> intf;
            return this == obj ||
                    obj != null &&
                            obj.getClass() == Key1.class &&
                            (intf = get()) != null &&
                            intf == ((Key1) obj).get();
        }

    }

    /**
     * A key used for proxy class with two implemented interfaces.
     */
    private static final class Key2 extends WeakReference<Class<?>> {

        private final int hash;

        private final WeakReference<Class<?>> refs;

        Key2(Class<?> ref1, Class<?> ref2) {
            super(ref1);
            this.hash = 31 * ref1.hashCode() + ref2.hashCode();
            refs = new WeakReference<>(ref2);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            Class<?> aClass1, aClass2;
            return this == obj ||
                    obj != null && obj.getClass() == Key2.class &&
                            (aClass1 = get()) != null && aClass1 == ((Key2) obj).get() &&
                            (aClass2 = refs.get()) != null && aClass2 == ((Key2) obj).refs.get();
        }

    }

    /**
     * A key used for proxy class with any number of implemented interfaces (used here for three or more only)
     */
    private static final class KeyX {

        private final int hash;

        private final WeakReference<Class<?>>[] refs;

        @SuppressWarnings(value = {"unchecked"})
        KeyX(Class<?>[] classes) {
            this.hash = Arrays.hashCode(classes);
            this.refs = (WeakReference<Class<?>>[]) new WeakReference[classes.length];
            for (int i = 0; i < classes.length; i++) {
                refs[i] = new WeakReference<>(classes[i]);
            }
        }

        private static boolean equals(WeakReference<Class<?>>[] refs1,
                                      WeakReference<Class<?>>[] refs2) {
            if (refs1.length != refs2.length) {
                return false;
            }
            for (int i = 0; i < refs1.length; i++) {
                Class<?> interfaceClass = refs1[i].get();
                if (interfaceClass == null || interfaceClass != refs2[i].get()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj ||
                    obj != null && obj.getClass() == KeyX.class && equals(this.refs, ((KeyX) obj).refs);
        }

    }

    /**
     * A function that maps an array of interfaces to an optimal key where Class objects representing
     * interfaces are weakly references.
     */
    private static final class KeyFactory implements BiFunction<ClassLoader, Class<?>[], Object> {

        @Override
        public Object apply(ClassLoader classLoader, Class<?>[] classes) {
            if (classes.length == 1) {
                // the most frequent
                return new Key1(classes[0]);
            }
            if (classes.length == 2) {
                return new Key2(classes[0], classes[1]);
            }
            return new KeyX(classes);
        }

    }

    /**
     * A factory function that generates defines and returns the proxy class given the {@link ClassLoader} and array
     * of interfaces.
     */
    private static final class ProxyClassFactory
            implements BiFunction<ClassLoader, Class<?>[], Class<?>> {

        // prefix for all proxy class names
        private static final String proxyClassNamePrefix = "$NewProxy";

        // next number to use for generation of unique class names
        private static final AtomicLong nextUniqueNumber = new AtomicLong();

        @Override
        public Class<?> apply(ClassLoader classLoader, Class<?>[] classes) {
            Map<Class<?>, Boolean> classSet = new IdentityHashMap<>();
            for (Class<?> aClass : classes) {
                /*
                 * Verify that the class loader resolves the name of the class to the same Class object.
                 */
                Class<?> clazz = null;
                try {
                    clazz = Class.forName(aClass.getName(), false, classLoader);
                } catch (ClassNotFoundException e) {
                    // ignore
                }
                /*
                 * Verify that the Class object actually represents an interface or a class.
                 */
                if (aClass != clazz) {
                    throw new IllegalArgumentException(aClass + " is not visible from class loader");
                }
                /*
                 * Verify that this interface is not a duplicate
                 */
                if (classSet.put(clazz, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException("repeated interface or class: " + clazz.getName());
                }
            }

            String proxyPkg = null;
            int accessFlags = Modifier.PUBLIC | Modifier.FINAL;

            /*
             * Record the package of a non-public proxy interface or class so that the
             * proxy class will be defined in the same package. Verify that
             * all non-public proxy classes are in the same package.
             *
             * Generally, all non-public proxy interfaces or class should be in the same package.
             */
            for (Class<?> aClass : classes) {
                int flags = aClass.getModifiers();
                if (!Modifier.isPublic(flags)) {
                    accessFlags = Modifier.FINAL;
                    String name = aClass.getName();
                    int n = name.lastIndexOf(".");
                    String pkg = (n == -1) ? "" : name.substring(0, n + 1);
                    if (proxyPkg == null) {
                        proxyPkg = pkg;
                    } else if (!pkg.equals(proxyPkg)) {
                        throw new IllegalArgumentException("non-public classes from different packages");
                    }
                }
            }
            if (proxyPkg == null) {
                // if no non-public proxy classes, use default package name
                proxyPkg = NewProxy.class.getPackage() + ".";
            }
            if (proxyPkg.startsWith("package ")) {
                proxyPkg = proxyPkg.substring(8);
            }
            /*
             * Choose a name from the proxy class to generate.
             */
            long number = nextUniqueNumber.getAndIncrement();
            String proxyClassName = proxyPkg + proxyClassNamePrefix + number;
            /*
             * Generate the specified proxy class
             */
            byte[] proxyClassFile = ProxyGenerator.generate(proxyClassName, accessFlags, classes);
            try {
                return (Class<?>) defineClass.invoke(Proxy.class, classLoader, proxyClassName, proxyClassFile, 0, proxyClassFile.length);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("defineClass0 in class Proxy can not be accessed");
            } catch (InvocationTargetException e) {
                throw new RuntimeException("invocation on defineClass0 in class Proxy fails");
            } catch (ClassFormatError e) {
                throw new IllegalArgumentException(e.toString());
            }
        }

    }

}
