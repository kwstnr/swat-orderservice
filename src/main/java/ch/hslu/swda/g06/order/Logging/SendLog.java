package ch.hslu.swda.g06.order.Logging;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.MessageProperties;

import com.google.gson.Gson;

import ch.hslu.swda.g06.order.Logging.model.Action;
import ch.hslu.swda.g06.order.Logging.model.Log;
import ch.hslu.swda.g06.order.listener.MessageReceiverUtils;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.OrderArticle;
import ch.hslu.swda.g06.order.model.Reason;

public class SendLog {
    public static void sendOrderFailedForArticlesLog(Order order, OrderArticle orderArticle, Reason reason,
            String correlationId,
            Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action(String.format("Order failed for articleId '%s' for reason '%s'",
                orderArticle.getArticleId(), reason), "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson, amqpTemplate);
    }

    public static void sendOrderFailedForCustomerLog(Order order, String customerId,
            Reason reason, String correlationId,
            Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action(String.format("Order failed for customer '%s' for reason '%s'",
                customerId, reason), "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson,
                amqpTemplate);
    }

    public static void sendOrderFailedWrongCustomerIdLog(Order order,
            String correlationId,
            Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action(String.format("Order with id '%s' failed because of wrong customerId",
                order.getOrderId()), "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson,
                amqpTemplate);
    }

    public static void sendOrderFailedWrongArticleIdLog(Order order,
            String correlationId,
            Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action(String.format("Order with id '%s' failed because of wrong verified articleId",
                order.getOrderId()), "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson,
                amqpTemplate);
    }

    public static void sendOrderFailedWrongArticleAmountLog(Order order,
            String correlationId,
            Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action(String.format("Order with id '%s' failed because of wrong verified article amount",
                order.getOrderId()), "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson,
                amqpTemplate);
    }

    public static void sendOrderFailedWrongArticleUnitPriceLog(Order order,
            String correlationId,
            Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action(String.format("Order with id '%s' failed because of wrong verified article price",
                order.getOrderId()), "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson,
                amqpTemplate);
    }

    public static void sendOrderCreated(Order order, String correlationId, Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action("Order created", "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson, amqpTemplate);
    }

    public static void sendAnnulatedOrder(Order order, String correlationId, Gson gson, AmqpTemplate amqpTemplate) {
        Action action = new Action("Order annulated", "order", order.getOrderId());
        Log log = new Log(action, order.getFilialId());

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentType("application/json");
        MessageReceiverUtils.sendMessage(log, messageProperties, "log.post", gson, amqpTemplate);
    }
}
