package com.assignment.whiteboard.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private String adminUsername = null;

    @PostMapping("/setadmin")
    public ResponseEntity<String> setAdmin(@RequestBody String username) {
        if (adminUsername == null) {
            adminUsername = username;
            return ResponseEntity.ok("Admin username has been set successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin username has already been set!");
        }
    }
}
