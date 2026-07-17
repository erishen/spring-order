package com.example.order.controller;

import com.example.order.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final List<User> USERS = Arrays.asList(
            new User("u1", "Alice Johnson", "alice@example.com"),
            new User("u2", "Bob Smith", "bob@example.com"),
            new User("u3", "Carol Williams", "carol@example.com"),
            new User("u4", "Diana Brown", "diana@example.com"),
            new User("u5", "Evan Davis", "evan@example.com")
    );

    @GetMapping
    public List<User> getUsers() {
        return USERS;
    }
}
