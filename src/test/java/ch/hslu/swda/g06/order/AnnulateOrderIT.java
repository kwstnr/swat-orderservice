package ch.hslu.swda.g06.order;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Type;
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
import ch.hslu.swda.g06.order.model.DeleteOrderDto;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.OrderArticle;
import ch.hslu.swda.g06.order.model.OrderState;
import ch.hslu.swda.g06.order.model.timeprovider.ITimeProvider;
import ch.hslu.swda.g06.order.model.timeprovider.TimeProviderInstanceCreator;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Testcontainers
class AnnulateOrderIT {
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
        gson = gsonBuilder.registerTypeAdapter(ITimeProvider.class, new TimeProviderInstanceCreator()).create();
    }

    @BeforeEach
    void setupSwdaExchange() {
        amqpAdmin.declareExchange(new TopicExchange("swda", true, false, Map.of("alternate-exchange", "swda.orphan")));
    }

    @BeforeEach
    void setupQueues() {
        amqpAdmin.declareQueue(new Queue("order.delete", false));
        amqpAdmin.declareQueue(new Queue("order.deleted", false));
        amqpAdmin.declareQueue(new Queue("articles.orderDeleted", false));
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
    void AnnulateOrderITNoOrder() {
        DeleteOrderDto deleteOrderDto = DeleteOrderDto.Builder.builder().withOrderId("orderId").withETag(1234l);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.deleted");
        messageProperties.setContentType("application/json");
        String body = gson.toJson(deleteOrderDto);
        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.send("order.delete", message);

        Message orderDeletedResponse = rabbitTemplate.receive("order.deleted", 5000);
        Message articlesOrderDeletedResponse = rabbitTemplate.receive("articles.orderDeleted", 500);
        Message orderDeletedLogMessage = rabbitTemplate.receive("log.post", 500);

        assertNotNull(orderDeletedResponse);
        new String(orderDeletedResponse.getBody()).equals("false");
        assertNull(articlesOrderDeletedResponse);
        assertNull(orderDeletedLogMessage);
    }

    @Test
    void AnnulateOrderITWithoutMatchingETag() {
        Order order = new Order("customerId", "employeeId", "filialId", List.of());
        mongoTemplate.save(order);

        DeleteOrderDto deleteOrderDto = DeleteOrderDto.Builder.builder().withOrderId(order.getOrderId())
                .withETag(1234l);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.deleted");
        messageProperties.setContentType("application/json");
        String body = gson.toJson(deleteOrderDto);
        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.send("order.delete", message);

        Message orderDeletedResponse = rabbitTemplate.receive("order.deleted", 5000);
        Message articlesOrderDeletedResponse = rabbitTemplate.receive("articles.orderDeleted", 500);
        Message orderDeletedLogMessage = rabbitTemplate.receive("log.post", 500);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order deletedOrder = mongoTemplate.findById(order.getOrderId(), Order.class);
            assertNotNull(deletedOrder);
            assertEquals(order.getState(), deletedOrder.getState());
        });
        assertNotNull(orderDeletedResponse);
        new String(orderDeletedResponse.getBody()).equals("false");
        assertNull(articlesOrderDeletedResponse);
        assertNull(orderDeletedLogMessage);
    }

    @Test
    void AnnulateOrderITWithMatchingETag() {
        OrderArticle orderArticle = new OrderArticle("articleId", 1, 1);
        Order order = new Order("customerId", "employeeId", "filialId", List.of(orderArticle));
        mongoTemplate.save(order);

        DeleteOrderDto deleteOrderDto = DeleteOrderDto.Builder.builder().withOrderId(order.getOrderId())
                .withETag(order.getEtag());
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setReplyTo("order.deleted");
        messageProperties.setContentType("application/json");
        String body = gson.toJson(deleteOrderDto);
        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.send("order.delete", message);

        Message orderDeletedResponse = rabbitTemplate.receive("order.deleted", 5000);
        Message articlesOrderDeletedResponse = rabbitTemplate.receive("articles.orderDeleted", 5000);
        Message orderDeletedLogMessage = rabbitTemplate.receive("log.post", 5000);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order deletedOrder = mongoTemplate.findById(order.getOrderId(), Order.class);
            assertNotNull(deletedOrder);
            assertEquals(OrderState.Annuliert, deletedOrder.getState());
        });
        assertNotNull(orderDeletedResponse);
        String orderDeletedResponseMessage = new String(orderDeletedResponse.getBody());
        assertEquals("true", orderDeletedResponseMessage);

        assertNotNull(articlesOrderDeletedResponse);
        Type listType = new TypeToken<List<OrderArticle>>() {
        }.getType();
        List<OrderArticle> articles = gson.fromJson(new String(articlesOrderDeletedResponse.getBody()),
                listType);
        assertEquals(1, articles.size());
        assertEquals(orderArticle.getArticleId(), articles.get(0).getArticleId());

        assertNotNull(orderDeletedLogMessage);
        Log log = gson.fromJson(new String(orderDeletedLogMessage.getBody()), Log.class);
        assertEquals("Order annulated", log.getAction().getAction());
        assertEquals("order", log.getAction().getEntityName());
        assertEquals(order.getOrderId(), log.getAction().getEntityId());
    }
}
