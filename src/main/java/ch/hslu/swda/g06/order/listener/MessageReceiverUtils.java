package ch.hslu.swda.g06.order.listener;

import java.util.List;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.google.gson.Gson;

import ch.hslu.swda.g06.order.model.Order;

public class MessageReceiverUtils {

    public static void sendOrderResponse(Order order, MessageProperties properties, final Gson GSON,
            final AmqpTemplate amqpTemplate) {
        MessageProperties returnMessageProperties = createReturnMessageProperties(properties);
        sendMessage(order, returnMessageProperties, properties.getReplyTo(), GSON, amqpTemplate);
    }

    public static void sendOrdersResponse(List<Order> orders, MessageProperties properties, final Gson GSON,
            final AmqpTemplate amqpTemplate) {
        MessageProperties returnMessageProperties = createReturnMessageProperties(properties);
        sendMessage(orders, returnMessageProperties, properties.getReplyTo(), GSON, amqpTemplate);
    }

    public static void sendMessage(final Object messageBody, final MessageProperties properties,
            final String queueName, final Gson GSON, final AmqpTemplate amqpTemplate) {
        String messageBodyJson = GSON.toJson(messageBody);
        Message message = new Message(messageBodyJson.getBytes(), properties);
        amqpTemplate.send(queueName, message);
    }

    private static MessageProperties createReturnMessageProperties(MessageProperties properties) {
        MessageProperties returnMessageProperties = new MessageProperties();
        returnMessageProperties.setCorrelationId(properties.getCorrelationId());
        returnMessageProperties.setContentType("application/json");
        return returnMessageProperties;
    }

    public static void sendDeleteResponse(final boolean isDeleted, final MessageProperties properties, final Gson GSON,
            final AmqpTemplate amqpTemplate) {
        String deleteResult = GSON.toJson(isDeleted);

        MessageProperties returnMessageProperties = createReturnMessageProperties(properties);

        Message returnMessage = new Message(deleteResult.getBytes(), returnMessageProperties);
        amqpTemplate.send(properties.getReplyTo(), returnMessage);
    }
}
