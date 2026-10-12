package com.docflow.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class DocumentController {

    @GetMapping("/api/hello")
    public String hello() {
        return "Hello from Docflow";
    }
}
