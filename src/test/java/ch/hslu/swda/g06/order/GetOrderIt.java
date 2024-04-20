package ch.hslu.swda.g06.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.timeprovider.ITimeProvider;
import ch.hslu.swda.g06.order.model.timeprovider.TimeProviderInstanceCreator;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Testcontainers
class GetOrderIt {
    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static Gson gson;

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.2.5");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Container
    static final RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:management")
            .withExposedPorts(5672, 15672);

    @DynamicPropertySource
    static void rabbitMQProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", (rabbitMQContainer::getHost));
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("spring.rabbitmq.exchange", () -> "swda");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setupGson() {
        gsonBuilder.registerTypeAdapter(ITimeProvider.class, new TimeProviderInstanceCreator());
        gson = gsonBuilder.create();
    }

    @BeforeEach
    void setupSwdaExchange() {
        amqpAdmin.declareExchange(new TopicExchange("swda", true, false, Map.of("alternate-exchange", "swda.orphan")));
    }

    @BeforeEach
    void setupQueues() {
        amqpAdmin.declareQueue(new Queue("order.getAll", false));
        amqpAdmin.declareQueue(new Queue("order.get", false));
        amqpAdmin.declareQueue(new Queue("order.response", false));
    }

    @AfterEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }

    @AfterEach
    void resetExchanges() {
        amqpAdmin.deleteExchange("swda");
        amqpAdmin.deleteExchange("swda.orphan");
    }

    @Test
    void GetOrderITNull() {
        String orderId = "orderId";
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.response");
        messageProperties.setContentType("application/json");
        Message message = new Message(orderId.getBytes(), messageProperties);
        rabbitTemplate.send("swda", "order.get", message);

        Message getOrderMessage = rabbitTemplate.receive("order.response", 5000);
        Order receivedOrder = gson.fromJson(new String(getOrderMessage.getBody()), Order.class);

        assertNotNull(getOrderMessage);
        assertNull(receivedOrder);
    }

    @Test
    void GetOrderITExistingOrder() {
        Order order = new Order("customerId", "employeeId", "filialId", List.of());
        mongoTemplate.save(order);

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.response");
        messageProperties.setContentType("application/json");
        Message message = new Message(order.getOrderId().getBytes(), messageProperties);
        rabbitTemplate.send("swda", "order.get", message);

        Message getOrderMessage = rabbitTemplate.receive("order.response", 5000);
        Order receivedOrder = gson.fromJson(new String(getOrderMessage.getBody()), Order.class);

        assertNotNull(getOrderMessage);
        assertNotNull(receivedOrder);
        assertEquals(order.getOrderId(), receivedOrder.getOrderId(), "Order ID should match");
        assertEquals(order.getCustomerId(), receivedOrder.getCustomerId(), "Customer ID should match");
        assertEquals(order.getEmployeeId(), receivedOrder.getEmployeeId(), "Employee ID should match");
        assertEquals(order.getFilialId(), receivedOrder.getFilialId(), "Filial ID should match");
        assertEquals(order.getTotalPrice(), receivedOrder.getTotalPrice(), "Total price should match");
    }

    @Test
    void GetOrdersITNoOrders() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.response");
        messageProperties.setContentType("application/json");
        Message message = new Message("".getBytes(), messageProperties);
        rabbitTemplate.send("swda", "order.getAll", message);

        Message getOrderMessage = rabbitTemplate.receive("order.response", 5000);
        Type orderListType = new TypeToken<List<Order>>() {
        }.getType();
        List<Order> receivedOrders = gson.fromJson(new String(getOrderMessage.getBody()), orderListType);

        assertNotNull(getOrderMessage);
        assertNotNull(receivedOrders);
        assertEquals(0, receivedOrders.size(), "No orders should be returned");
    }

    @Test
    void GetOrdersITExistingOrder() {
        Order order = new Order("customerId", "employeeId", "filialId", List.of());
        mongoTemplate.save(order);

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.response");
        messageProperties.setContentType("application/json");
        Message message = new Message("".getBytes(), messageProperties);
        rabbitTemplate.send("swda", "order.getAll", message);

        Message getOrderMessage = rabbitTemplate.receive("order.response", 5000);
        Type orderListType = new TypeToken<List<Order>>() {
        }.getType();
        List<Order> receivedOrders = gson.fromJson(new String(getOrderMessage.getBody()), orderListType);

        assertNotNull(getOrderMessage);
        assertNotNull(receivedOrders);
        assertEquals(1, receivedOrders.size(), "One order should be returned");
        assertEquals(order.getOrderId(), receivedOrders.get(0).getOrderId(), "Order ID should match");
        assertEquals(order.getCustomerId(), receivedOrders.get(0).getCustomerId(), "Customer ID should match");
        assertEquals(order.getEmployeeId(), receivedOrders.get(0).getEmployeeId(), "Employee ID should match");
        assertEquals(order.getFilialId(), receivedOrders.get(0).getFilialId(), "Filial ID should match");
        assertEquals(order.getTotalPrice(), receivedOrders.get(0).getTotalPrice(), "Total price should match");
    }
}
