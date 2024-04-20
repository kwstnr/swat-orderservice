package ch.hslu.swda.g06.order;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import ch.hslu.swda.g06.order.Logging.model.Log;
import ch.hslu.swda.g06.order.model.CreateOrderDto;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.OrderArticle;
import ch.hslu.swda.g06.order.model.OrderState;
import ch.hslu.swda.g06.order.model.VerifyPropertyDto;
import ch.hslu.swda.g06.order.model.timeprovider.ITimeProvider;
import ch.hslu.swda.g06.order.model.timeprovider.TimeProviderInstanceCreator;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Testcontainers
class CreateOrderIT {
    private static final GsonBuilder gsonBuilder = new GsonBuilder();
    private static Gson gson;

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
        amqpAdmin.declareQueue(new Queue("order.post", false));
        amqpAdmin.declareQueue(new Queue("article.verify", false));
        amqpAdmin.declareQueue(new Queue("user.verify", false));
        amqpAdmin.declareQueue(new Queue("order.created", false));
        amqpAdmin.declareQueue(new Queue("log.post", false));
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

    @Test
    void createOrderITOrderCreatedMessage() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.created");
        messageProperties.setContentType("application/json");

        OrderArticle article = new OrderArticle("articleId", 12, 2);
        CreateOrderDto createOrderDto = new CreateOrderDto("customerId", "employeeId", List.of(article), "filialId");
        String body = gson.toJson(createOrderDto);

        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.convertAndSend("swda", "order.post", message);

        Message orderCreatedMessage = rabbitTemplate.receive("order.created", 5000);
        Order createdOrder = gson.fromJson(new String(orderCreatedMessage.getBody(), StandardCharsets.UTF_8),
                Order.class);

        assertNotNull(createdOrder);
        assertEquals(createOrderDto.getCustomerId(), createdOrder.getCustomerId(), "CustomerId should match");
        assertEquals(createOrderDto.getEmployeeId(), createdOrder.getEmployeeId(), "EmployeeId should match");
        assertEquals(createOrderDto.getFilialId(), createdOrder.getFilialId(), "FilialId should match");
        assertEquals(24, createdOrder.getTotalPrice(), "TotalPrice should match");
        assertEquals(OrderState.Bestellt, createdOrder.getState(), "OrderState should be Bestellt");
    }

    @Test
    void createOrderITVerifyUserMessageSent() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.created");
        messageProperties.setContentType("application/json");

        OrderArticle article = new OrderArticle("articleId", 12, 2);
        CreateOrderDto createOrderDto = new CreateOrderDto("customerId", "employeeId", List.of(article), "filialId");
        String body = gson.toJson(createOrderDto);

        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.convertAndSend("swda", "order.post", message);

        Message verifyUserMessage = rabbitTemplate.receive("user.verify", 5000);
        var verifyPropertyDto = gson
                .fromJson(new String(verifyUserMessage.getBody(), StandardCharsets.UTF_8), VerifyPropertyDto.class);

        assertNotNull(verifyUserMessage);
        assertEquals(createOrderDto.getCustomerId(), verifyPropertyDto.getPropertyValue(),
                "PropertyValue of Sent Message should equal CustomerId");
    }

    @Test
    void createOrderITVerifyArticlesMessageSent() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.created");
        messageProperties.setContentType("application/json");

        OrderArticle article = new OrderArticle("articleId", 12, 2);
        CreateOrderDto createOrderDto = new CreateOrderDto("customerId", "employeeId", List.of(article), "filialId");
        String body = gson.toJson(createOrderDto);

        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.convertAndSend("swda", "order.post", message);

        Message verifyArticlesMessage = rabbitTemplate.receive("article.verify", 5000);
        Type verifyPropertyType = new TypeToken<VerifyPropertyDto<List<OrderArticle>>>() {
        }.getType();
        VerifyPropertyDto<List<OrderArticle>> verifyPropertyDto = gson
                .fromJson(new String(verifyArticlesMessage.getBody(), StandardCharsets.UTF_8), verifyPropertyType);

        assertNotNull(verifyArticlesMessage);
        assertEquals(1, verifyPropertyDto.getPropertyValue().size(), "Should contain one Article");
        assertEquals(article.getArticleId(), verifyPropertyDto.getPropertyValue().get(0).getArticleId(),
                "ArticleId should match");
        assertEquals(article.getAmount(), verifyPropertyDto.getPropertyValue().get(0).getAmount(),
                "Amount should match");
        assertEquals(article.getUnitPrice(), verifyPropertyDto.getPropertyValue().get(0).getUnitPrice(), "UnitPrice should match");
    }
     

    
     @Test
    void createOrderITLogMessageSent() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.created");
        messageProperties.setContentType("application/json");

        OrderArticle article = new OrderArticle("articleId", 12, 2);
        CreateOrderDto createOrderDto = new CreateOrderDto("customerId", "employeeId", List.of(article), "filialId");
        String body = gson.toJson(createOrderDto);

        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.convertAndSend("swda", "order.post", message);

        Message createLogMessage = rabbitTemplate.receive("log.post", 5000);
        Log log = gson.fromJson(new String(createLogMessage.getBody(), StandardCharsets.UTF_8), Log.class);

        assertNotNull(log);
        assertEquals(createOrderDto.getFilialId(), log.getStoreId(), "FilialId should match");
        assertEquals("Order created", log.getAction().getAction(), "Action should be Order created");
        assertEquals("order", log.getAction().getEntityName(), "EntityName should be order");
    }
}
