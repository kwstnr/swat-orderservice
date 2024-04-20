package ch.hslu.swda.g06.order;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

import ch.hslu.swda.g06.order.model.CreateOrderDto;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.OrderArticle;
import ch.hslu.swda.g06.order.model.OrderState;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Testcontainers
class CreateOrderIT {
    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.2.5");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Container
    private static final RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:management")
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
    void configureRabbitMq() {

    }

    @BeforeEach
    void setupSwdaExchange() {
        amqpAdmin.declareExchange(new TopicExchange("swda", true, false, Map.of("alternate-exchange", "swda.orphan")));
    }

    @BeforeEach
    void setupQueues() {
        amqpAdmin.declareQueue(new Queue("order.post", false));
        amqpAdmin.declareQueue(new Queue("article.verify", false));
        amqpAdmin.declareQueue(new Queue("user.verify", false));
        amqpAdmin.declareQueue(new Queue("order.created", false));
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
    void createOrderITOrderCreated() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.created");
        messageProperties.setContentType("application/json");

        OrderArticle article = new OrderArticle("articleId", 12, 2);
        CreateOrderDto createOrderDto = new CreateOrderDto("customerId", "employeeId", List.of(article), "filialId");
        Gson gson = new Gson();
        String body = gson.toJson(createOrderDto);

        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.convertAndSend("swda", "order.post", message);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Order> orders = mongoTemplate.findAll(Order.class, "orders");
            assertEquals(1, orders.size(), "Order should be created");
            assertEquals(createOrderDto.getCustomerId(), orders.get(0).getCustomerId(), "Customer ID should match");
            assertEquals(createOrderDto.getEmployeeId(), orders.get(0).getEmployeeId(), "Employee ID should match");
            assertEquals(createOrderDto.getFilialId(), orders.get(0).getFilialId(), "Filial ID should match");
            assertEquals(createOrderDto.getArticles().size(), orders.get(0).getArticles().size(),
                    "Article size should match");
            assertEquals(OrderState.Bestellt, orders.get(0).getState(), "Order should have state 'Bestellt'");
            assertEquals(24, orders.get(0).getTotalPrice(), "Total price should match");
        });
    }
}
