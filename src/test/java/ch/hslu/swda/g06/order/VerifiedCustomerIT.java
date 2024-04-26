package ch.hslu.swda.g06.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

import ch.hslu.swda.g06.order.Logging.model.Log;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.Reason;
import ch.hslu.swda.g06.order.model.VerifyPropertyDto;
import ch.hslu.swda.g06.order.model.timeprovider.ITimeProvider;
import ch.hslu.swda.g06.order.model.timeprovider.TimeProviderInstanceCreator;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Testcontainers
class VerifiedCustomerIT {
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
        amqpAdmin.declareQueue(new Queue("order.verifyCustomer", false));
        amqpAdmin.declareQueue(new Queue("log.post", false));
        amqpAdmin.declareQueue(new Queue("mail.confirmation", false));
        amqpAdmin.declareQueue(new Queue("bill.create", false));
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
    void VerifyCustomerITNotExistingOrder() {
        VerifyPropertyDto<String> verifyPropertyDto = VerifyPropertyDto.Builder.<String>builder().withOrderId("1234")
                .withPropertyValue("Some Value").withVerified(true).withoutReason();

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setContentType("application/json");

        String body = gson.toJson(verifyPropertyDto);
        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.send("oder.verifyCustomer", message);

        Message orderConfirmationMessage = rabbitTemplate.receive("mail.confirmation", 500);
        Message orderBillMessage = rabbitTemplate.receive("bill.create", 500);
        Message orderFailedLogMessage = rabbitTemplate.receive("log.post", 500);

        assertNull(orderConfirmationMessage);
        assertNull(orderBillMessage);
        assertNull(orderFailedLogMessage);
    }

    @Test
    void VerifyCustomerITCustomerNotFound() {
        Order order = new Order("customerId", "employeeId", "filialId", List.of());
        mongoTemplate.save(order);

        VerifyPropertyDto<String> verifyPropertyDto = VerifyPropertyDto.Builder.<String>builder()
                .withOrderId(order.getOrderId())
                .withPropertyValue(order.getCustomerId())
                .withVerified(false)
                .withReason(Reason.NOT_FOUND);

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setContentType("application/json");

        String body = gson.toJson(verifyPropertyDto);
        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.send("order.verifyCustomer", message);

        Message orderConfirmationMessage = rabbitTemplate.receive("mail.confirmation", 500);
        Message orderBillMessage = rabbitTemplate.receive("bill.create", 500);
        Message orderFailedLogMessage = rabbitTemplate.receive("log.post", 1000);

        assertNull(orderConfirmationMessage);
        assertNull(orderBillMessage);
        assertNotNull(orderFailedLogMessage);
        Log log = gson.fromJson(new String(orderFailedLogMessage.getBody()), Log.class);
        assertEquals("Order failed for customer 'customerId' for reason 'NOT_FOUND'", log.getAction().getAction());
        assertEquals("order", log.getAction().getEntityName());
        assertEquals(order.getOrderId(), log.getAction().getEntityId());
    }

    @Test
    void VerifyCustomerITMismatchedCustomerId() {
        Order order = new Order("customerId", "employeeId", "filialId", List.of());
        mongoTemplate.save(order);

        VerifyPropertyDto<String> verifyPropertyDto = VerifyPropertyDto.Builder.<String>builder()
                .withOrderId(order.getOrderId())
                .withPropertyValue("wrong customer")
                .withVerified(true)
                .withoutReason();

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId("correlationId");
        messageProperties.setContentType("application/json");

        String body = gson.toJson(verifyPropertyDto);
        Message message = new Message(body.getBytes(), messageProperties);

        rabbitTemplate.send("order.verifyCustomer", message);

        Message orderConfirmationMessage = rabbitTemplate.receive("mail.confirmation", 500);
        Message orderBillMessage = rabbitTemplate.receive("bill.create", 500);
        Message orderFailedLogMessage = rabbitTemplate.receive("log.post", 1000);

        assertNull(orderConfirmationMessage);
        assertNull(orderBillMessage);
        assertNotNull(orderFailedLogMessage);
        Log log = gson.fromJson(new String(orderFailedLogMessage.getBody()), Log.class);
        assertEquals("Order with id '" + order.getOrderId() + "' failed because of wrong customerId",
                log.getAction().getAction());
        assertEquals("order", log.getAction().getEntityName());
        assertEquals(order.getOrderId(), log.getAction().getEntityId());
    }
}
