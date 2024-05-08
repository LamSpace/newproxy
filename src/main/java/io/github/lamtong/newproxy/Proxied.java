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

import java.lang.annotation.*;

/**
 * An interface to indicate whether a specified object is a proxy instance or not.<br/><br/>
 *
 * <h3>Role in NewProxy</h3>
 * This interface works as a mark flag to indicate if a specified object is a dynamic proxy instance or not since
 * {@link NewProxy} will append this one into a specified array of interfaces when {@link NewProxy} invokes
 * {@link ProxyGenerator#generate(String, int, Class[])} to generate an array of byte, the binary format of a
 * standard {@code Class} file, which can be used to <b>define</b> a {@code Class} recognized by
 * {@code Java Virtual Machine(JVM)}.<br/>
 * If specified object {@code obj} is derived from a dynamic proxy class, then {@link NewProxy#isProxyClass(Class)}
 * will return true, otherwise return false./
 *
 * @author Lam Tong
 * @version 0.0.1
 * @see NewProxy#isProxyClass(Class)
 * @since 0.0.1
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Documented
@Target(value = {ElementType.TYPE})
public @interface Proxied {
}
