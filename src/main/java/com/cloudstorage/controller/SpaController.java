package com.cloudstorage.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping({ "/login", "/register", "/files", "/share", "/share/{code}" })
    public String forward() {
        return "forward:/index.html";
    }

}
