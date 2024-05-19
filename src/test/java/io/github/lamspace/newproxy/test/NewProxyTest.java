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

package io.github.lamspace.newproxy.test;

import io.github.lamspace.newproxy.InvocationInterceptor;
import io.github.lamspace.newproxy.NewProxy;
import io.github.lamspace.newproxy.interfaces.BarService;
import io.github.lamspace.newproxy.interfaces.FooService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link NewProxy} Test Class.
 *
 * @author Lam Tong
 */
public class NewProxyTest {

    private static final Logger logger = Logger.getLogger(NewProxyTest.class.getName());

    private static FooService fooService;

    private static BarService barService;

    private static InnerService innerService;

    private static HelloService helloService;

    @BeforeAll
    public static void setUp() {
        fooService = new FooService() {

            @Override
            public boolean equals(Object obj) {
                return this == obj;
            }

            @Override
            public int hashCode() {
                return super.hashCode();
            }

            @Override
            public String toString() {
                return getClass().getName() + "@" + Integer.toHexString(hashCode());
            }

            @Override
            public void foo() {
                logger.log(Level.INFO, "FooServiceImpl invoked...");
            }

            @Override
            public int add(int x, double y) {
                logger.log(Level.INFO, "add x and y");
                return (int) (x + y);
            }

            @Override
            public String concat(String x, String y) {
                return x.concat(y);
            }

            @Override
            public String repeat(String x, int count, String message) {
                for (int i = 0; i < count; i++) {
                    x = x.concat(x);
                }
                logger.log(Level.INFO, message);
                return x;
            }

        };
        barService = () -> logger.log(Level.INFO, "BarServiceImpl invoked...");
        innerService = () -> "InnerServiceImpl, whose interface is not public.";
        helloService = () -> logger.log(Level.INFO, "Sub InnerService01Impl invoked...");
    }

    /**
     * Case: generate a proxy instance for a plain interface with {@code public} modifier,
     * and generated proxy class start with "public final" modifiers, and the package name
     * equals to {@link NewProxy}.
     */
    @Test
    public void testForSinglePlainInterface() {
        InvocationInterceptor interceptor = (proxy, method, args) -> {
            String name = method.getMethod().getName();
            logger.log(Level.INFO, "Method: " + name + " invoked");
            return method.invoke(proxy, fooService, args);
        };
        FooService proxy = (FooService) NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null, FooService.class);
        proxy.foo();
        int modifiers = proxy.getClass().getModifiers();
//         generated proxy class start with "public final" modifiers
        Assertions.assertTrue(Modifier.isPublic(modifiers), "Modifiers: not public");
        Assertions.assertTrue(Modifier.isFinal(modifiers), "Modifiers: not final");
        String className = proxy.getClass().getName(),
                packageName = className.substring(0, className.lastIndexOf("."));
        Assertions.assertEquals(packageName, NewProxy.class.getPackage().getName());
    }

    /**
     * Case: generate a proxy instance for multiple plain interfaces with {@code public} modifier,
     * and generated proxy class start with "public final" modifiers, and the package name equals to {@link NewProxy}.
     */
    @Test
    public void testForMultiplePlainInterfaces() {
        InvocationInterceptor interceptor = (proxy, method, args) -> {
            String name = method.getMethod().getName();
            logger.log(Level.INFO, "Method: " + name + " invoked");
            if (method.getMethod().getDeclaringClass() == FooService.class) {
                return method.invoke(proxy, fooService, args);
            } else if (method.getMethod().getDeclaringClass() == BarService.class) {
                return method.invoke(proxy, barService, args);
            }
            return method.invoke(proxy, null, args);
        };
        Object o = NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null, FooService.class, BarService.class);
        FooService foo = (FooService) o;
        foo.foo();
        BarService bar = (BarService) o;
        bar.bar();
        int modifiers = o.getClass().getModifiers();
        // generated proxy class start with "public final" modifiers
        Assertions.assertTrue(Modifier.isPublic(modifiers), "Modifiers: not public");
        Assertions.assertTrue(Modifier.isFinal(modifiers), "Modifiers: not final");
        String className = o.getClass().getName(),
                packageName = className.substring(0, className.lastIndexOf("."));
        Assertions.assertEquals(packageName, NewProxy.class.getPackage().getName());
    }

    /**
     * Case: try to generate a proxy interface with repeated interfaces,
     * and expect to throw an {@link IllegalArgumentException}.
     */
    @Test
    public void testForRepeatedInterfaces() {
        InvocationInterceptor interceptor = (proxy, method, args) -> method.invoke(proxy, fooService, args);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Object o = NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null, FooService.class, FooService.class);
            FooService foo = (FooService) o;
            foo.foo();
        }, "No exception for Repeated interfaces");
    }

    /**
     * Case: try to generate a proxy interface with no interfaces, and
     * expect to throw an {@link IllegalArgumentException}.
     */
    @Test
    public void testForEmptyInterfaces() {
        InvocationInterceptor interceptor = (proxy, method, args) -> null;
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Object o = NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null);
            FooService foo = (FooService) o;
            foo.foo();
        }, "No Exception for Empty interfaces");
    }

    /**
     * Case: try to generate a proxy interface with a null interface array, and
     * expect to throw an {@link IllegalArgumentException}.
     */
    @Test
    public void testForNullInterfaceArray() {
        InvocationInterceptor interceptor = (proxy, method, args) -> method.invoke(proxy, fooService, args);
        Assertions.assertThrows(NullPointerException.class, () -> {
            Object o = NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null, null);
            FooService foo = (FooService) o;
            foo.foo();
        }, "No Exception for Null Interface Array");
    }

    /**
     * Case: try to generate a proxy interface with an inner interface, and generated proxy class only start
     * with "final" modifiers, and the package name equals to the specified interface itself.
     */
    @Test
    public void testForSingleInnerService() {
        InvocationInterceptor interceptor = (proxy, method, args) -> method.invoke(proxy, innerService, args);
        InnerService innerService = (InnerService) NewProxy.newProxyInstance(InnerService.class.getClassLoader(), interceptor, null, null, InnerService.class);
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "invoke InnerService.inner() -> " + innerService.inner());
        }
        int modifiers = innerService.getClass().getModifiers();
        Assertions.assertFalse(Modifier.isPublic(modifiers), "Modifiers: public");
        Assertions.assertTrue(Modifier.isFinal(modifiers), "Modifiers: not final");
        String className = innerService.getClass().getName(),
                packageName = className.substring(0, className.lastIndexOf("."));
        Assertions.assertEquals(packageName, InnerService.class.getPackage().getName());
    }

    /**
     * Case: try to generate a proxy interface with multiple inner interfaces, and generated proxy class only start
     * with "final" modifiers, and the package name equals to the specified interface itself.
     */
    @Test
    public void testForMultipleInnerService() {
        InvocationInterceptor interceptor = (proxy, method, args) -> {
            if (method.getMethod().getDeclaringClass() == HelloService.class) {
                return method.invoke(proxy, helloService, args);
            } else if (method.getMethod().getDeclaringClass() == InnerService.class) {
                return method.invoke(proxy, innerService, args);
            }
            return null;
        };
        Object o = NewProxy.newProxyInstance(InnerService.class.getClassLoader(), interceptor, null, null, HelloService.class, InnerService.class);
        HelloService helloService = (HelloService) o;
        helloService.hello();
        InnerService innerService = (InnerService) o;
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "innerService inner() -> " + innerService.inner());
        }
        int modifiers = innerService.getClass().getModifiers();
        Assertions.assertFalse(Modifier.isPublic(modifiers), "Modifiers: public");
        Assertions.assertTrue(Modifier.isFinal(modifiers), "Modifiers: not final");
        String className = innerService.getClass().getName(),
                packageName = className.substring(0, className.lastIndexOf("."));
        Assertions.assertEquals(packageName, InnerService.class.getPackage().getName());
    }

    /**
     * Case: try to generate a proxy class, and check if the class is a proxy class.
     */
    @Test
    public void testForProxyClass() {
        FooService service = (FooService) NewProxy.newProxyInstance(FooService.class.getClassLoader(), (proxy, method, args) -> null, null, null, FooService.class);
        Assertions.assertTrue(NewProxy.isProxyClass(service.getClass()));
        Assertions.assertFalse(NewProxy.isProxyClass(Object.class));
    }

    /**
     * Case: try to generate a proxy class, and check if the object is a proxy instance.
     */
    @Test
    public void testForProxyInstance() {
        FooService service = (FooService) NewProxy.newProxyInstance(FooService.class.getClassLoader(), (proxy, method, args) -> null, null, null, FooService.class);
        Assertions.assertTrue(NewProxy.isProxyInstance(service));
        Assertions.assertFalse(NewProxy.isProxyInstance(new Object()));
    }

    /**
     * Case: try to generate a proxy class, and create an object using Reflection API with that
     * proxy class.
     */
    @Test
    public void testForDynamicProxyClass() {
        Class<?> proxyClass = NewProxy.getProxyClass(FooService.class.getClassLoader(), null, FooService.class);
        Assertions.assertTrue(NewProxy.isProxyClass(proxyClass));
        try {
            Constructor<?> constructor = proxyClass.getConstructor(InvocationInterceptor.class);
            // Be aware that whether the modifier of constructor is public or not, it will be accessible always.
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                constructor.setAccessible(true);
                return null;
            });
            Object o = constructor.newInstance((InvocationInterceptor) (proxy, method, args) -> null);
            Assertions.assertTrue(NewProxy.isProxyInstance(o));
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Case: try to acquire the invocation handler instance of a proxy instance.
     */
    @Test
    public void testForAcquireInvocationHandlerInstance() {
        InvocationInterceptor interceptor = (proxy, method, args) -> {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Method name: " + method.getMethod().getName());
            }
            return method.invoke(proxy, fooService, args);
        };
        FooService fooService = (FooService) NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null, FooService.class);
        InvocationInterceptor invocationInterceptor;
        try {
            invocationInterceptor = NewProxy.getInvocationInterceptor(fooService);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        Assertions.assertNotNull(invocationInterceptor);
        Assertions.assertEquals(invocationInterceptor, interceptor);
    }

    /**
     * Case: try to dump the dynamic proxy class via system properties.
     */
    @Test
    public void testForDumpDynamicProxyClassViaSystemProperties() {
        InvocationInterceptor interceptor = ((proxy, method, args) -> {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Method name: " + method.getMethod().getName());
            }
            return method.invoke(proxy, fooService, args);
        });
        FooService fooService = (FooService) NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null, FooService.class);
        fooService.foo();
    }

    /**
     * Case: try to generate multiple proxy instances in multiple threads with the same interface.
     */
    @Test
    public void testForMultipleThreadsGeneration() {
        InvocationInterceptor interceptor = (proxy, method, args) -> method.invoke(proxy, fooService, args);
        int count = 30;
        CountDownLatch latch = new CountDownLatch(30);
        ExecutorService pool = Executors.newCachedThreadPool();
        for (int i = 0; i < count; i++) {
            pool.execute(() -> {
                FooService fooService = (FooService) NewProxy.newProxyInstance(FooService.class.getClassLoader(), interceptor, null, null, FooService.class);
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "Thread name: " + Thread.currentThread().getName() + ", class name: " + fooService.getClass().getName() + ", object: " + fooService);
                }
                latch.countDown();
            });
        }
        try {
            // wait until all logs are printed.
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Case: try to generate a proxy class for a single class.
     */
    @Test
    public void testForProxyClassGenerationForSingleClass() {
        InvocationInterceptor interceptor = ((proxy, method, args) -> {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Method name: " + method.getMethod().getName());
            }
            return method.invoke(proxy, null, args);
        });
        ProxySample proxySample = (ProxySample) NewProxy.newProxyInstance(ProxySample.class.getClassLoader(), interceptor, new Class[]{String.class}, new Object[]{"Hello, World"}, ProxySample.class);
        proxySample.sayHi();
        Assertions.assertEquals("Hello, Lam Tong", proxySample.hello("Lam Tong"));
        Assertions.assertEquals("6.0", proxySample.add(1, 2.0, 3L));
    }

    /**
     * Case: try to generate a proxy class for a single class only.
     */
    @Test
    public void testForProxyClassOnly() {
        Class<?> proxyClass = NewProxy.getProxyClass(ProxySample.class.getClassLoader(), new Class[]{String.class}, ProxySample.class);
        Assertions.assertNotNull(proxyClass);
    }

    /**
     * Case: try to generate a proxy class for multiple classes.
     */
    @Test
    public void testForProxyClassGenerationForMultipleClasses() {
        InvocationInterceptor interceptor = ((proxy, method, args) -> {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Method name: " + method.getMethod().getName());
            }
            return method.invoke(proxy, null, args);
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            ProxySample proxySample = (ProxySample) NewProxy.newProxyInstance(ProxySample.class.getClassLoader(), interceptor, new Class[]{String.class}, new Object[]{"Hello, World"}, ProxySample.class, ProxySample.class);
        }, "No exception when creating proxy for two classes.");
    }

    /**
     * Case: try to generate a proxy class for a single class and a single interface.
     */
    @Test
    public void testForSingleClassAndSingleInterface() {
        InvocationInterceptor interceptor = ((proxy, method, args) -> {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "Method name: " + method.getMethod().getName());
            }
            if (method.getMethod().getDeclaringClass().isInterface()) {
                return method.invoke(proxy, barService, args);
            }
            return method.invoke(proxy, null, args);
        });
        ProxySample proxySample = (ProxySample) NewProxy.newProxyInstance(ProxySample.class.getClassLoader(), interceptor, new Class[]{String.class}, new Object[]{"Hello, World"}, ProxySample.class, BarService.class);
        BarService service = (BarService) proxySample;
        service.bar();
        Assertions.assertEquals("Hello, Lam Tong", proxySample.hello("Lam Tong"));
        Assertions.assertEquals("6.0", proxySample.add(1, 2.0, 3L));
    }

}
