package com.example.mockinterview.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/dashboard.html";
    }

    @GetMapping("/dashboard/interview/{id}")
    public String interview(@PathVariable String id) {
        return "forward:/interview.html";
    }

    @GetMapping("/dashboard/interview/{id}/start")
    public String start(@PathVariable String id) {
        return "forward:/start.html";
    }

    @GetMapping("/dashboard/interview/{id}/feedback")
    public String feedback(@PathVariable String id) {
        return "forward:/feedback.html";
    }

    @GetMapping("/dashboard/questions")
    public String questions() {
        return "forward:/questions.html";
    }
}
