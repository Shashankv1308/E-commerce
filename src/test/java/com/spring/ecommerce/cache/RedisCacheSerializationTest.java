package com.spring.ecommerce.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spring.ecommerce.config.JacksonRedisSerializer;
import com.spring.ecommerce.order.OrderStatus;
import com.spring.ecommerce.order.PaymentMethod;
import com.spring.ecommerce.order.PaymentStatus;
import com.spring.ecommerce.order.dto.OrderItemResponse;
import com.spring.ecommerce.order.dto.OrderResponse;

/**
 * Tests the exact serialization/deserialization path used by RedisConfig
 * for cached DTOs. Validates that the ObjectMapper configuration (NON_FINAL
 * typing, ISO dates, enum handling) produces correct round-trip results.
 *
 * Pure unit test — no Spring context, no Redis server required.
 * Catches the class of bugs that caused the GET /orders/{id} 500 error.
 */
class RedisCacheSerializationTest
{
    private JacksonRedisSerializer serializer;

    @BeforeEach
    void setUp()
    {
        // Mirror exact ObjectMapper config from RedisConfig
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        serializer = new JacksonRedisSerializer(mapper);
    }

    private OrderResponse buildSampleOrder()
    {
        OrderItemResponse item1 = new OrderItemResponse();
        item1.setProductId(10L);
        item1.setProductName("Wireless Keyboard");
        item1.setQuantity(2);
        item1.setPriceAtPurchase(49.99);
        item1.setSubtotal(99.98);

        OrderItemResponse item2 = new OrderItemResponse();
        item2.setProductId(20L);
        item2.setProductName("USB-C Hub");
        item2.setQuantity(1);
        item2.setPriceAtPurchase(29.99);
        item2.setSubtotal(29.99);

        OrderResponse order = new OrderResponse();
        order.setOrderId(100L);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.AWAITING_PAYMENT);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setTotalAmount(129.97);
        order.setCreatedAt(LocalDateTime.of(2026, 3, 9, 15, 30, 24));

        List<OrderItemResponse> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        order.setItems(items);

        return order;
    }

    @Test
    @DisplayName("serialize and deserialize OrderResponse with all field types")
    void fullRoundTrip()
    {
        OrderResponse original = buildSampleOrder();

        byte[] bytes = serializer.serialize(original);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "Serialized bytes should not be empty");

        Object deserialized = serializer.deserialize(bytes);
        assertNotNull(deserialized);
        assertInstanceOf(OrderResponse.class, deserialized,
                "Deserialized object must be OrderResponse, not " + deserialized.getClass().getName());

        OrderResponse result = (OrderResponse) deserialized;
        assertEquals(original.getOrderId(), result.getOrderId());
        assertEquals(original.getOrderStatus(), result.getOrderStatus());
        assertEquals(original.getPaymentStatus(), result.getPaymentStatus());
        assertEquals(original.getPaymentMethod(), result.getPaymentMethod());
        assertEquals(original.getTotalAmount(), result.getTotalAmount(), 0.001);
        assertEquals(original.getCreatedAt(), result.getCreatedAt());
        assertNotNull(result.getItems());
        assertEquals(2, result.getItems().size());
    }

    @Test
    @DisplayName("deserialized items are OrderItemResponse instances, not LinkedHashMap")
    void itemsAreCorrectType()
    {
        OrderResponse original = buildSampleOrder();

        byte[] bytes = serializer.serialize(original);
        OrderResponse result = (OrderResponse) serializer.deserialize(bytes);

        assertInstanceOf(ArrayList.class, result.getItems(),
                "Items list must be ArrayList, not " + result.getItems().getClass().getName());

        Object firstItem = result.getItems().get(0);
        assertInstanceOf(OrderItemResponse.class, firstItem,
                "Items must be OrderItemResponse, not " + firstItem.getClass().getName());

        OrderItemResponse item = (OrderItemResponse) firstItem;
        assertEquals(10L, item.getProductId());
        assertEquals("Wireless Keyboard", item.getProductName());
        assertEquals(2, item.getQuantity());
        assertEquals(49.99, item.getPriceAtPurchase(), 0.001);
        assertEquals(99.98, item.getSubtotal(), 0.001);
    }

    @Test
    @DisplayName("LocalDateTime survives round-trip as ISO string, not array")
    void localDateTimeRoundTrip()
    {
        OrderResponse original = buildSampleOrder();

        byte[] bytes = serializer.serialize(original);
        String json = new String(bytes);
        // Should contain ISO format like "2026-03-09T15:30:24", NOT [2026,3,9,15,30,24]
        assertTrue(json.contains("2026-03-09T15:30:24"),
                "JSON should use ISO date format, got: " + json);
        assertFalse(json.contains("[2026,3,9,"),
                "JSON should NOT use array date format, got: " + json);

        OrderResponse result = (OrderResponse) serializer.deserialize(bytes);
        assertEquals(LocalDateTime.of(2026, 3, 9, 15, 30, 24), result.getCreatedAt());
    }

    @Test
    @DisplayName("enums round-trip correctly")
    void enumRoundTrip()
    {
        OrderResponse original = buildSampleOrder();

        byte[] bytes = serializer.serialize(original);
        OrderResponse result = (OrderResponse) serializer.deserialize(bytes);

        assertEquals(OrderStatus.CONFIRMED, result.getOrderStatus());
        assertEquals(PaymentStatus.AWAITING_PAYMENT, result.getPaymentStatus());
        assertEquals(PaymentMethod.ONLINE, result.getPaymentMethod());
    }

    @Test
    @DisplayName("all OrderStatus values survive round-trip")
    void allOrderStatusValues()
    {
        for (OrderStatus status : OrderStatus.values())
        {
            OrderResponse order = buildSampleOrder();
            order.setOrderStatus(status);

            byte[] bytes = serializer.serialize(order);
            OrderResponse result = (OrderResponse) serializer.deserialize(bytes);

            assertEquals(status, result.getOrderStatus(),
                    "Failed round-trip for OrderStatus." + status);
        }
    }

    @Test
    @DisplayName("all PaymentStatus values survive round-trip")
    void allPaymentStatusValues()
    {
        for (PaymentStatus status : PaymentStatus.values())
        {
            OrderResponse order = buildSampleOrder();
            order.setPaymentStatus(status);

            byte[] bytes = serializer.serialize(order);
            OrderResponse result = (OrderResponse) serializer.deserialize(bytes);

            assertEquals(status, result.getPaymentStatus(),
                    "Failed round-trip for PaymentStatus." + status);
        }
    }

    @Test
    @DisplayName("all PaymentMethod values survive round-trip")
    void allPaymentMethodValues()
    {
        for (PaymentMethod method : PaymentMethod.values())
        {
            OrderResponse order = buildSampleOrder();
            order.setPaymentMethod(method);

            byte[] bytes = serializer.serialize(order);
            OrderResponse result = (OrderResponse) serializer.deserialize(bytes);

            assertEquals(method, result.getPaymentMethod(),
                    "Failed round-trip for PaymentMethod." + method);
        }
    }

    @Test
    @DisplayName("order with empty items list")
    void emptyItemsList()
    {
        OrderResponse order = buildSampleOrder();
        order.setItems(new ArrayList<>());

        byte[] bytes = serializer.serialize(order);
        OrderResponse result = (OrderResponse) serializer.deserialize(bytes);

        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @DisplayName("serialize null returns empty bytes")
    void serializeNull()
    {
        byte[] bytes = serializer.serialize(null);
        assertNotNull(bytes);
        assertEquals(0, bytes.length);
    }

    @Test
    @DisplayName("deserialize null returns null")
    void deserializeNull()
    {
        assertNull(serializer.deserialize(null));
    }

    @Test
    @DisplayName("deserialize empty bytes returns null")
    void deserializeEmptyBytes()
    {
        assertNull(serializer.deserialize(new byte[0]));
    }
}
