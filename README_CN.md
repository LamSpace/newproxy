# NewProxy: New Proxy for Java

[English](./README.md)

![Gitter](https://img.shields.io/gitter/room/lamspace/newproxy)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
![Static Badge](https://img.shields.io/badge/NewProxy-New%20Proxy%20for%20Java-color=red)
![GitHub Repo stars](https://img.shields.io/github/stars/lamspace/newproxy)
![GitHub forks](https://img.shields.io/github/forks/lamspace/newproxy)
![GitHub Release](https://img.shields.io/github/v/release/lamspace/newproxy)
![Maven Central Version](https://img.shields.io/maven-central/v/io.github.lamspace/newproxy)

---

## NewProxy 是什么?

> **NewProxy** 是 **Proxy** 的扩展, 与 **CGLIB** 相似. 但是它尽可能与 **CGLIB** 一样快, 甚至比 **CGLIB** 更快,
> 比 **CGLIB** 更简单, 比 **CGLIB** 更小.

详情见 [GitHub](https://github.com/LamSpace/newproxy-samples) 或者 [Gitee](https://gitee.com/LamTong/newproxy-samples).

---

## NewProxy 能做什么?

**NewProxy** 是一个可以为接口和类生成动态代理类的工具.

**NewProxy** 沿用 **JDK** 内置的 **Proxy** 类思想来生成动态代理类, 为目标接口和类生成代理类, 调用其方法, 并将结果返回给调用者.

但是相比较 **Proxy** 而言, **NewProxy** 生成的动态代理类本身并不继承 **NewProxy** 类. 相反, 它仅仅实现指定的接口和类
(包含接口 InvocationDispatcher), 极大减少了所生成的动态代理类的复杂度.

此外, **NewProxy** 同样支持为一个类生成动态代理类, 这和 **CGLIB** 相似.

**NewProxy** 有如下特点:

* 若接口(类)使用 **public** 修饰符, 则生成的动态代理类的修饰符是 **public final**.
* 若接口(类)不是 **public** 的, 则生成的动态代理类的修饰符是 **final**.
* 检查给定的目标类是否是动态代理类.
* 检查给定的目标实例是否是动态代理类的实例.
* 若给定的目标实例是动态代理类的实例, 获取其 InvocationInterceptor 实例.

---

## 快速开始

**NewProxy** 的使用与 **Proxy** 一样简单.

首先需要将 **NewProxy** 依赖库引入到项目中.

```xml

<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>newproxy</artifactId>
    <version>${latest.version}</version>
</dependency>
```

### 样例 1: 为接口生成动态代理类

```java
public interface Foo {

    void foo();

}
```

示例代码如下:

```java
import io.github.lamspace.newproxy.InvocationInterceptor;
import io.github.lamspace.newproxy.MethodDecorator;
import io.github.lamspace.newproxy.NewProxy;

public static void main(String[] args) {
    InvocationInterceptor interceptor = new InvocationInterceptor() {
        @Override
        public Object intercept(Object proxy, MethodDecorator method, Object[] args) {
            return method.invoke(proxy, fooImpl, args);
        }
    };
    Foo foo = (Foo) NewProxy.newProxyInstance(Foo.class.getClassLoader(), interceptor, null, null, Foo.class);
    foo.foo();
}
```

---

### 样例 2: 为类生成动态代理类, 其基类(或父类)的构造方法没有参数

```java
public class Bar {

    public void bar() {
        // ...
    }

}
```

```java
import io.github.lamspace.newproxy.InvocationInterceptor;
import io.github.lamspace.newproxy.MethodDecorator;
import io.github.lamspace.newproxy.NewProxy;

public static void main(String[] args) {
    InvocationInterceptor interceptor = new InvocationInterceptor() {
        @Override
        public Object intercept(Object proxy, MethodDecorator method, Object[] args) throws Throwable {
            return method.invoke(proxy, null, args);
        }
    };
    Bar bar = (Bar) NewProxy.newProxyInstance(Bar.class.getClassLoader(), interceptor, null, null, Bar.class);
    bar.bar();
}
```

### 样例 3: 为类生成动态代理类, 其基类(或父类)的构造方法有参数

```java
public class Bar {

    private final String s;

    public Bar(Strin s) {
        this.s = s;
    }

    public void bar() {
        //...
    }

}
```

```java
import io.github.lamspace.newproxy.InvocationInterceptor;
import io.github.lamspace.newproxy.MethodDecorator;

public static void main(String[] args) {
    InvocationInterceptor interceptor = new InvocationInterceptor() {
        @Override
        public Object intercept(Object proxy, MethodDecorator method, Object[] args) throws Throwable {
            return method.invoke(proxy, null, args);
        }
    };
    Bar bar = (Bar) NewProxy.newProxyInstance(Bar.class.getClassLoader(), interceptor, new Class<?>[]{String.class}, new Object[]{"Hello World!"}, Bar.class);
    bar.bar();
}
```

### Case 4: 为类和接口生成动态代理类

...

---

## 底层支持

**NewProxy** 建立在 **Byte Code Engineering Library** (简称为 **BCEL**) 之上. **NewProxy** 不导入任何关于 **BCEL**
的第三方库, 而是直接使用 JDK 内置的 **BCEL** 支持. 关于**BCEL** 的更多细节, 可以参考
[BCEL](https://commons.apache.org/proper/commons-bcel/) 官方网站.

---

## 前提条件

至少 JDK 8.

---

## 代码贡献

欢迎各位提交代码, 详情见 [CONTRIBUTING](./CONTRIBUTING.md).

*Cheers!*

---
