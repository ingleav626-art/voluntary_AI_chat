package org.example.client.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoginController 结构测试")
class LoginControllerTest {

    @Test
    @DisplayName("构造 LoginController")
    void shouldInstantiate() {
        LoginController controller = new LoginController();
        assertNotNull(controller);
    }

    @Test
    @DisplayName("构造后 controller 不为 null")
    void controllerShouldNotBeNull() {
        LoginController controller = new LoginController();
        assertNotNull(controller);
    }
}
