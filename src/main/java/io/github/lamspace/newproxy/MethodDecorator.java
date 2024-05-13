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

import sun.reflect.CallerSensitive;

import java.lang.reflect.Method;

/**
 * {@link MethodDecorator} decorates {@link Method} instance and provides a method similar to
 * {@link Method#invoke(Object, Object...)}, which enables method invocation on the proxy instance
 * to be dispatched to the target object, either the implementation of the interface or the superclass
 * of the proxy class.
 *
 * @author Lam Tong
 * @version 1.0.0
 * @since 1.0.0
 */
public final class MethodDecorator {

    /**
     * real {@link Method} instance to be decorated
     */
    private final Method method;

    private MethodDecorator(Method method) {
        this.method = method;
    }

    /**
     * Static factory method to create a {@link MethodDecorator} instance.
     *
     * @param method {@link Method} instance
     * @return {@link MethodDecorator} instance
     */
    public static MethodDecorator of(Method method) {
        return new MethodDecorator(method);
    }

    /**
     * Get the decorated {@link Method} instance.
     *
     * @return {@link Method} instance
     */
    public Method getMethod() {
        return this.method;
    }

    /**
     * Invokes the underlying method represented by this {@link MethodDecorator} object, on the specified object with
     * the specified parameters. Individual parameters are automatically unwrapped to match primitive formal parameters,
     * and both primitive and reference parameters are subject to method invocation conversions as necessary.<br/>
     * Typically, the underlying method should be {@code public} and not {@code static}, since dynamic proxy class does
     * not support {@code static} methods.<br/>
     * If the number of formal parameters required by the underlying method is zero, the supplier {@code args} array
     * may be of length zero, or null.<br/>
     * If the method completes normally, the value it returns is returned to the caller of invocation; if the value
     * has a primitive type, it is first appropriately wrapped in an object. However, if the value has the type of
     * array of a primitive type, the elements of the array are not wrapped in objects; in other words, an array of
     * primitive type is returned. If the underlying method return type is void, the value returned by this method
     * is {@code null}.
     *
     * @param proxy  the proxy instance from which the underlying method is invoked
     * @param object actual object to be invoked. Required when the declaring class of the method is interface, and
     *               this object usually is an implementation of that interface.
     * @param args   the arguments used for the method invocation
     * @return the result from which the method invocation returns
     * @throws Throwable //todo
     */
    @CallerSensitive
    public Object invoke(Object proxy, Object object, Object... args) throws Throwable {
        InvocationDispatcher dispatcher = ((InvocationDispatcher) proxy);
        if (this.method.getDeclaringClass().isInterface()) {
            return dispatcher.dispatch(object, this.method, args);
        }
        return dispatcher.dispatch(proxy, this.method, args);
    }

}
