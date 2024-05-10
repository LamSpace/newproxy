# NewProxy: New Proxy for Java

![Gitter](https://img.shields.io/gitter/room/lamspace/newproxy)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
![Static Badge](https://img.shields.io/badge/NewProxy-New%20Proxy%20for%20Java-color=red)
![GitHub Repo stars](https://img.shields.io/github/stars/lamspace/newproxy)
![GitHub forks](https://img.shields.io/github/forks/lamspace/newproxy)
![GitHub Release](https://img.shields.io/github/v/release/lamspace/newproxy)
![Maven Central Version](https://img.shields.io/maven-central/v/io.github.lamspace/newproxy)

---

## What does it do?

**NewProxy** is a tool to generate **dynamic proxy** for interfaces.

It follows the concept of JDK's built-in **Proxy** class for generating dynamic proxy classes,
which involves creating a proxy class for the target interfaces and then invoking its methods reflectively,
returning the results to the caller.
In contrast to the JDK's **Proxy** class, **NewProxy** does not inherit from the **Proxy** class;
instead, it merely implements the specified interfaces,
significantly reducing the complexity of the generated proxy classes.

**NewProxy** provides the following features:

* Generate dynamic proxy classes for public interfaces with **public final** modifiers.
* Generate dynamic proxy classes for non-public interface with *final* modifier.
* Check whether the target class if a dynamic proxy class or not.
* Check if the target object is an instance of a dynamic proxy class or not.
* Acquire the invocation handler instance of the target object if its class is a dynamic proxy class.

---

## Quick Start

It's as easy as it is to use **Proxy** to generate dynamic proxy classes in Java.

If you want to use **NewProxy** to generate dynamic proxy classes for interface *Foo*,
you need to do the following:

### Step1. Import the **NewProxy** library into your project

```xml

<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>newproxy</artifactId>
    <version>${version}</version>
</dependency>
```

### Step2. Generate dynamic proxy class

```java
import io.github.lamspace.newproxy.NewProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public static void main(String[] args) {
    InvocationHandler handler = (proxy, method, args1) -> {
        // process method invocation yourself
        return null;
    };
    Object o = NewProxy.newProxyInstance(Foo.class.getClassLoader(),
            handler,
            Foo.class);
    Foo f = (Foo) o;
    f.foo(); // do anything you want
}
```

Pretty easy, isn't it?

---

## Underlying Support

**NewProxy** is built on top of the **Byte Code Engineering Library**
(simply called [BCEL](https://commons.apache.org/proper/commons-bcel/)).
Instead, **NewProxy** does not import any third-party libraries about **BCEL** since JDK has built-in **BCEL** support.
More details can be found in the [BCEL](https://commons.apache.org/proper/commons-bcel/) official website.

---

## Prerequisites

At least JDK 8 is required.

---

## Contributing

Contributions are welcome! Do what you want to do with **Apache License 2.0**!

*Cheers!*

---
