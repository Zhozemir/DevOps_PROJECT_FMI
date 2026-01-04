package com.fmi.devops_project_fmi.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {

    private final String version;

    public VersionController(@Value("${app.version:dev}") String version) {
        this.version = version;
    }

    @GetMapping("/api/version")
    public String version() {
        return version;
    }

}
