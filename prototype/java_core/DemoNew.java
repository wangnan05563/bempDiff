package com.demo;

class Demo {
    private String name = "current";

    public String sayHello(String user) {
        if (user == null || user.trim().isEmpty()) {
            return "Hello, guest";
        }
        return "Hello, " + user.trim();
    }

    public int calc(int a, int b) {
        return a * b;
    }

    public boolean isAdmin(String user) {
        return "admin".equalsIgnoreCase(user);
    }
}
