package com.cloudcost.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class OverviewController {

    @GetMapping("/")
    String overview() {
        return "overview";
    }
}
