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

/**
 * {@link InvocationInterceptor} is the interface implemented by the invocation handler of a proxy instance.
 * Each proxy instance has an associated invocation handler. When a method is invoked on a proxy instance,
 * the method invocation is encoded and dispatched to the {@link #intercept(Object, MethodDecorator, Object[])
 * intercept} method of its invocation handler.<br/>
 * Generally, {@link InvocationInterceptor} works as the same as {@link java.lang.reflect.InvocationHandler} while the
 * former one decorates the method instance, enabling the invocation of the method with custom logic without
 * {@code Reflection}.
 *
 * @author Lam Tong
 * @version 1.0.0
 * @since 1.0.0
 */
@FunctionalInterface
public interface InvocationInterceptor {

    /**
     * Processes a method invocation on a proxy instance and returns the result. This method will be invoked on
     * an invocation handler when a method is invoked on a proxy instance that it is associated with.
     *
     * @param proxy  the proxy instance that the method was invoked on
     * @param method the {@link MethodDecorator} instance corresponding to the method invoked on the proxy instance.
     *               The declaring class of the {@link MethodDecorator} object will be the interface that the method
     *               was declared in, which may be a superinterface of the proxy interface that the proxy class
     *               inherits the method through.
     * @param args   an array of objects containing the values of the arguments passed in the method invocation
     *               on the proxy instance, or {@code null} if this method takes no arguments. Arguments of primitive
     *               types are wrapped in instances of the appropriate primitive wrapper class, such as
     *               {@link Integer} or {@link Boolean}.
     * @return the value to return from the method invocation on the proxy instance. If the declared return type is
     * a primitive type, then the value returned by this method must be an instance of the corresponding primitive
     * wrapper class; otherwise, it must be a type assignable to the declared return type. If the value returned by
     * this method is {@code null} and the method's return type is primitive, than a {@link NullPointerException}
     * will be thrown by the method invocation on the proxy instance. If the value returned by this method is otherwise
     * not compatible with the declared return type as described above, a {@link ClassCastException} will be thrown
     * by the method invocation on the proxy instance.
     * @throws Throwable the exception to throw from the method invocation on the proxy instance. The exception's type
     *                   must be assignable either to any of the exception types declared in the {@code throws}
     *                   clause of the method or to the unchecked exception types {@link RuntimeException}
     *                   or {@link Error}. If a checked exception is thrown by this method that is not assignable to any
     *                   of the exception types declared in the {@code throws} clause of the method, then an
     *                   {@link java.lang.reflect.UndeclaredThrowableException} containing the exception
     *                   thrown by this method will be thrown by the method invocation on the proxy instance.
     */
    Object intercept(Object proxy, MethodDecorator method, Object[] args) throws Throwable;

}
