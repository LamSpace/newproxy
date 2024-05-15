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

import java.lang.reflect.Method;

/**
 * {@link InvocationDispatcher} is the interface implemented by the proxy class, enabling the proxy class dispatches
 * method invocations on the proxy instance. When a method is invoked on a proxy instance,
 * {@link #dispatch(Object, Method, Object...) dispatch} method will be invoked to dispatch the method invocations
 * depending on whether the declaring class of the method is interface or not. If the declaring class is an interface,
 * then method invocation will be dispatched to the implementation which implemented the interface. Otherwise,
 * method invocation will be dispatched to the superclass of the proxy class.<br/>
 * Generally, this interface must be implemented by generated dynamic proxy class automatically
 * via byte code generation technology, enabling method invocation to be dispatched to an appropriate implementation.
 * And this interface is also a mark interface to indicate if the specified class is a proxy class or not, see in
 * {@link NewProxy#isProxyClass(Class)}.
 *
 * @author Lam Tong
 * @version 1.0.0
 * @since 1.0.0
 */
public interface InvocationDispatcher {

    /**
     * Dispatches the method invocation on a proxy instance and return the result. This method will be invoked on
     * a proxy instance when a method is invoked on it.
     *
     * @param object object on which the method was invoked. If the declaring class of the method is an interface,
     *               then the object will be the actual implementation of that interface. Otherwise, the object will
     *               be the proxy instance itself
     * @param method the {@link Method} instance corresponding to the method invoked on the proxy instance
     * @param args   an array of objects containing the values of the arguments passed in the method invocation
     *               on the proxy instance, or {@code null} if this method takes no arguments. Arguments of primitive
     *               types are wrapped in instances of the appropriate primitive wrapper class, such as
     *               {@link Integer} or {@link Boolean}
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
    Object dispatch(Object object, Method method, Object... args) throws Throwable;

}
