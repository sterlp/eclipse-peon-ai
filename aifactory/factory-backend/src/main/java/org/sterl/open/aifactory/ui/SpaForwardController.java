package org.sterl.open.aifactory.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

/** Enables the UI */
@Controller
public class SpaForwardController {
    @GetMapping("/ui/**")
    public String forward(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains(".")) {
            return null; // statische Datei, normal behandeln
        }
        return "forward:/ui/index.html";
    }
}
