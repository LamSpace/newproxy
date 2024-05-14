package io.github.lamspace.newproxy.test;

public class ProxySample {

    public void sayHi() {
        System.out.println("Hi");
    }

    public String hello(String name) {
        return "Hello, " + name;
    }

    public String add(int x, double y, long z) {
        return String.valueOf(x + y + z);
    }

}
