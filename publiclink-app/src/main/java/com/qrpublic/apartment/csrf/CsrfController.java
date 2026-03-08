package com.qrpublic.apartment.csrf;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/csrf-simulation")
public class CsrfController {
    @GetMapping("/csrf-index")
    public String csrfIndex(){
        log.info("GET /csrf-index.");
        return "csrf-index-page";
    }

}
