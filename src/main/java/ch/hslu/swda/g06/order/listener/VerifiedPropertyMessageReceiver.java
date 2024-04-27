package ch.hslu.swda.g06.order.listener;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ch.hslu.swda.g06.order.Logging.SendLog;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.OrderArticle;
import ch.hslu.swda.g06.order.model.OrderState;
import ch.hslu.swda.g06.order.model.Reason;
import ch.hslu.swda.g06.order.model.VerifyPropertyDto;
import ch.hslu.swda.g06.order.repository.IOrderRepository;

@Component
public class VerifiedPropertyMessageReceiver {
    private static final Gson GSON = new Gson();

    @Autowired
    private IOrderRepository orderRepository;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @RabbitListener(queues = "order.verifyCustomer")
    public void verifyCustomer(Message message) {
        VerifyPropertyDto<String> verifyPropertyDto = GSON.fromJson(
                new String(message.getBody(), StandardCharsets.UTF_8),
                VerifyPropertyDto.class);
        Order order = orderRepository.findById(verifyPropertyDto.getOrderId()).orElse(null);
        if (order == null) {
            return;
        }
        OrderState previousOrderState = order.getState();
        if (verifyPropertyDto.getVerified() && order.getCustomerId().equals(verifyPropertyDto.getPropertyValue())) {
            order.verifyCustomerId();
            if (order.getState() == OrderState.Bestätigt && order.getState() != previousOrderState) {
                sendOrderConfirmationMessage(order);
                sendOrderBillMessage(order);
            }
        } else {
            order.setOrderState(OrderState.Failed);
            if (!verifyPropertyDto.getVerified()) {
                if (verifyPropertyDto.getReason() != null && order.getState() != previousOrderState)
                    SendLog.sendOrderFailedForCustomerLog(order, verifyPropertyDto.getPropertyValue(),
                            (Reason) verifyPropertyDto.getReason(), message.getMessageProperties().getCorrelationId(),
                            GSON, amqpTemplate);
            } else {
                if (order.getState() != previousOrderState)
                    SendLog.sendOrderFailedWrongCustomerIdLog(order, message.getMessageProperties().getCorrelationId(),
                            GSON,
                            amqpTemplate);
            }
        }
        orderRepository.save(order);
    }

    @RabbitListener(queues = "order.verifyArticle")
    public void verifyArticleId(Message message) {
        VerifyPropertyDto<OrderArticle> verifyPropertyDto = GSON.fromJson(
                new String(message.getBody(), StandardCharsets.UTF_8),
                new TypeToken<VerifyPropertyDto<OrderArticle>>() {
                }.getType());
        Order order = orderRepository.findById(verifyPropertyDto.getOrderId()).orElse(null);
        if (order == null) {
            return;
        }
        OrderState previousOrderState = order.getState();
        if (verifyPropertyDto.getVerified() && order.getArticles().stream()
                .anyMatch(article -> article.getArticleId()
                        .equals(verifyPropertyDto.getPropertyValue().getArticleId())
                        && article.getAmount() == verifyPropertyDto.getPropertyValue().getAmount()
                        && article.getUnitPrice() == verifyPropertyDto.getPropertyValue().getUnitPrice())) {
            order.verifyArticleId(verifyPropertyDto.getPropertyValue().getArticleId());
            if (order.getState() == OrderState.Bestätigt && order.getState() != previousOrderState) {
                sendOrderConfirmationMessage(order);
                sendOrderBillMessage(order);
            }
            order.setCurrentEtag();
            orderRepository.save(order);
        } else {
            order.setOrderState(OrderState.Failed);
            if (!verifyPropertyDto.getVerified()) {
                if (verifyPropertyDto.getReason() != null && order.getState() != previousOrderState)
                    SendLog.sendOrderFailedForArticlesLog(order, verifyPropertyDto.getPropertyValue(),
                            (Reason) verifyPropertyDto.getReason(), message.getMessageProperties().getCorrelationId(),
                            GSON, amqpTemplate);
            } else {
                if (order.getState() != previousOrderState && !order.getArticles().stream()
                        .anyMatch(article -> article.getArticleId()
                                .equals(verifyPropertyDto.getPropertyValue().getArticleId()))) {
                    SendLog.sendOrderFailedWrongArticleIdLog(order,
                            message.getMessageProperties().getCorrelationId(),
                            GSON,
                            amqpTemplate);
                } else if (order.getState() != previousOrderState && order.getArticles().stream()
                        .anyMatch(article -> article.getArticleId()
                                .equals(verifyPropertyDto.getPropertyValue().getArticleId())
                                && article.getAmount() != verifyPropertyDto.getPropertyValue().getAmount())) {
                    SendLog.sendOrderFailedWrongArticleAmountLog(order,
                            message.getMessageProperties().getCorrelationId(),
                            GSON,
                            amqpTemplate);
                } else if (order.getState() != previousOrderState && order.getArticles().stream()
                        .anyMatch(article -> article.getArticleId()
                                .equals(verifyPropertyDto.getPropertyValue().getArticleId())
                                && article.getUnitPrice() != verifyPropertyDto.getPropertyValue().getUnitPrice())) {
                    SendLog.sendOrderFailedWrongArticleUnitPriceLog(order,
                            message.getMessageProperties().getCorrelationId(),
                            GSON,
                            amqpTemplate);
                }

            }

        }
        orderRepository.save(order);
    }

    private void sendOrderConfirmationMessage(Order order) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType("application/json");
        messageProperties.setCorrelationId(UUID.randomUUID().toString());
        MessageReceiverUtils.sendMessage(order, messageProperties, "mail.confirmation", GSON, amqpTemplate);
    }

    private void sendOrderBillMessage(Order order) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType("application/json");
        messageProperties.setCorrelationId(UUID.randomUUID().toString());
        MessageReceiverUtils.sendMessage(order, messageProperties, "bill.create", GSON, amqpTemplate);
    }
}
