package com.progtechie.orderservice.service;

import com.progtechie.orderservice.dto.InventoryResponse;
import com.progtechie.orderservice.dto.OrderLineItemsDto;
import com.progtechie.orderservice.dto.OrderRequest;
import com.progtechie.orderservice.entity.Order;
import com.progtechie.orderservice.entity.OrderLineItems;
import com.progtechie.orderservice.event.OrderPlacedEvent;
import com.progtechie.orderservice.repository.OrderRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObservationRegistry observationRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDto().stream()
                .map(this::mapToEntity)
                .collect(Collectors.toList());
        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = orderLineItems.stream()
                .map(OrderLineItems::getSkuCode)
                .collect(Collectors.toList());

        Observation observation = Observation
                .createNotStarted("inventory-service-lookup", observationRegistry)
                .lowCardinalityKeyValue("call", "inventory-service");

        return observation.observe(() -> {
            InventoryResponse[] inventoryResponses = fetchInventoryStatuses(skuCodes);

            boolean allInStock = inventoryResponses != null &&
                    inventoryResponses.length == skuCodes.size() &&
                    List.of(inventoryResponses).stream().allMatch(InventoryResponse::isInStock);

            if (!allInStock) {
                log.warn("Some products are not in stock: {}", skuCodes);
                throw new IllegalArgumentException("Product is not in stock, please try again later");
            }

            orderRepository.save(order);
            applicationEventPublisher.publishEvent(new OrderPlacedEvent(this, order.getOrderNumber()));
            log.info("Order {} placed successfully", order.getOrderNumber());

            return "Order Placed";
        });
    }

    private InventoryResponse[] fetchInventoryStatuses(List<String> skuCodes) {
        return webClientBuilder.build().get()
                .uri("http://inventory-service/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
    }

    private OrderLineItems mapToEntity(OrderLineItemsDto dto) {
        OrderLineItems item = new OrderLineItems();
        item.setPrice(dto.getPrice());
        item.setQuantity(dto.getQuantity());
        item.setSkuCode(dto.getSkuCode());
        return item;
    }
}
