package com.demo;

class Demo {
    private String name = "legacy";

    public String sayHello(String user) {
        if (user == null || user.isEmpty()) {
            return "Hello, guest";
        }
        return "Hello, " + user;
    }

    public int calc(int a, int b) {
        return a + b;
    }
}
