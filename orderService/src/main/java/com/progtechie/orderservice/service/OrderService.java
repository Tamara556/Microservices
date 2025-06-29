package com.progtechie.orderservice.service;

import com.progtechie.orderservice.dto.InventoryResponse;
import com.progtechie.orderservice.dto.OrderLineItemsDto;
import com.progtechie.orderservice.dto.OrderRequest;
import com.progtechie.orderservice.entity.Order;
import com.progtechie.orderservice.entity.OrderLineItems;
import com.progtechie.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDto()
                .stream()
                .map(this::mapToEntity)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = extractSkuCodes(orderLineItems);

        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Some products are not in stock. Order not placed.");
        }
    }

    private OrderLineItems mapToEntity(OrderLineItemsDto dto) {
        return OrderLineItems.builder()
                .price(dto.getPrice())
                .quantity(dto.getQuantity())
                .skuCode(dto.getSkuCode())
                .build();
    }

    private List<String> extractSkuCodes(List<OrderLineItems> items) {
        return items.stream()
                .map(OrderLineItems::getSkuCode)
                .toList();
    }
}
