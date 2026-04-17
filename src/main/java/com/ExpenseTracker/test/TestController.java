package com.ExpenseTracker.test;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
@Tag(name = "Test", description = "Endpoints de prueba y salud de la API")
public class TestController {

    @GetMapping("/hello")
    @Operation(summary = "Saludo de bienvenida")
    public String hello() {
        return "Hello from Togrow API!";
    }

    @GetMapping("/status")
    @Operation(summary = "Estado de la API")
    public String status() {
        return "API is working correctly";
    }
}
