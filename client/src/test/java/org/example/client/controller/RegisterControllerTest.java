package org.example.client.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RegisterController 结构测试")
class RegisterControllerTest {

    @Test
    @DisplayName("构造 RegisterController")
    void shouldInstantiate() {
        RegisterController controller = new RegisterController();
        assertNotNull(controller);
    }
}
