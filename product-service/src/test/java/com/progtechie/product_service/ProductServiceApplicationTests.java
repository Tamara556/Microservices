package com.progtechie.product_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.progtechie.product_service.dto.ProductRequest;
import com.progtechie.product_service.model.Product;
import com.progtechie.product_service.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class ProductServiceApplicationTests {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2");

    static {
        mongoDBContainer.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void cleanUp() {
        productRepository.deleteAll();
    }

    @Test
    void shouldCreateProduct() throws Exception {
        ProductRequest productRequest = new ProductRequest(
                null,
                "iPhone 15",
                "New Apple phone",
                BigDecimal.valueOf(1500)
        );

        String requestJson = objectMapper.writeValueAsString(productRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        Assertions.assertEquals(1, productRepository.findAll().size());
    }

    @Test
    void shouldReturnAllProducts() throws Exception {
        // Arrange
        Product product1 = Product.builder()
                .name("iPhone 15")
                .description("Apple phone")
                .price(BigDecimal.valueOf(1500))
                .build();

        Product product2 = Product.builder()
                .name("Samsung S24")
                .description("Samsung phone")
                .price(BigDecimal.valueOf(1300))
                .build();

        productRepository.saveAll(List.of(product1, product2));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.get("/api/product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].name", is("iPhone 15")))
                .andExpect(jsonPath("$[1].name", is("Samsung S24")));
    }
}
