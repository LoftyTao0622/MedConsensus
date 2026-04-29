package com.zyt.medconsensus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/",
            "/login",
            "/register",
            "/workspace",
            "/consultation",
            "/dashboard"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
