package com.progtechie.orderservice.service;

import com.progtechie.orderservice.dto.InventoryResponse;
import com.progtechie.orderservice.dto.OrderLineItemsDto;
import com.progtechie.orderservice.dto.OrderRequest;
import com.progtechie.orderservice.entity.Order;
import com.progtechie.orderservice.entity.OrderLineItems;
import com.progtechie.orderservice.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "fallbackMethod")
    @TimeLimiter(name = "inventory-service")
    public Mono<String> placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDto()
                .stream()
                .map(this::mapToEntity)
                .toList();

        order.setOrderLineItemsList(orderLineItems);
        List<String> skuCodes = extractSkuCodes(orderLineItems);
        Mono<InventoryResponse[]> skuCode = webClientBuilder.build()
                .get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class);
        webClientBuilder.build()
                .get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class);
        return skuCode
                .flatMap(inventoryResponses -> {
                    System.out.println("*******");
                    boolean allInStock = List.of(inventoryResponses)
                            .stream()
                            .allMatch(InventoryResponse::isInStock);

                    if (allInStock) {
                        orderRepository.save(order);
                        return Mono.just("Order placed successfully!");
                    } else {
                        return Mono.error(new IllegalArgumentException("Some products are not in stock. Order not placed."));
                    }
                });
    }

    private OrderLineItems mapToEntity(OrderLineItemsDto dto) {
        return OrderLineItems.builder()
                .price(dto.getPrice())
                .quantity(dto.getQuantity())
                .skuCode(dto.getSkuCode())
                .build();
    }

    public CompletableFuture<String> fallbackMethod(OrderRequest orderRequest, Throwable ex) {
        return CompletableFuture.supplyAsync(() -> "Oops! Something went wrong! please try again later");
    }

    private List<String> extractSkuCodes(List<OrderLineItems> items) {
        return items.stream()
                .map(OrderLineItems::getSkuCode)
                .toList();
    }
}
