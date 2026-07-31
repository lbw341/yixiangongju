package com.toolplatform.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class FrontendForwardController {

    @RequestMapping(value = {
            "/",
            "/login",
            "/home",
            "/tools",
            "/tools/{id:[\\w\\-]+}",
            "/category/{name:[\\w\\-\\u4e00-\\u9fa5]+}",
            "/profile",
            "/manage",
            "/messages",
            "/feedback",
            "/search"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
