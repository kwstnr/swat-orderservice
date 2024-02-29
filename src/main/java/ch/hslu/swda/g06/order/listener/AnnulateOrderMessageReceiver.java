package ch.hslu.swda.g06.order.listener;

import java.nio.charset.StandardCharsets;
import java.util.List;

import ch.hslu.swda.g06.order.Logging.SendLog;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import ch.hslu.swda.g06.order.model.DeleteOrderDto;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.OrderArticle;
import ch.hslu.swda.g06.order.model.OrderState;
import ch.hslu.swda.g06.order.repository.IOrderRepository;

@Component
public class AnnulateOrderMessageReceiver {
    private static final Gson GSON = new Gson();

    @Autowired
    private IOrderRepository orderRepository;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @RabbitListener(queues = "order.delete")
    public void annulateOrder(Message message) {
        MessageProperties messageProperties = message.getMessageProperties();
        byte[] messageBody = message.getBody();
        DeleteOrderDto deleteOrderDto = GSON.fromJson(new String(messageBody, StandardCharsets.UTF_8),
                DeleteOrderDto.class);

        Order order = orderRepository.findById(deleteOrderDto.getOrderId()).orElse(null);
        if (order == null) {
            MessageReceiverUtils.sendDeleteResponse(false, messageProperties, GSON, amqpTemplate);
            return;
        }
        if (order.canEdit(deleteOrderDto.getETag())) {
            order.setOrderState(OrderState.Annuliert);
            order.setCurrentEtag();
            orderRepository.save(order);
            SendLog.sendAnnulatedOrder(order, messageProperties.getCorrelationId(), GSON, amqpTemplate);
        }

        sendOrderDeletedMessage(messageProperties.getCorrelationId(), order.getArticles());

        MessageReceiverUtils.sendDeleteResponse(true, messageProperties, GSON, amqpTemplate);
    }

    private void sendOrderDeletedMessage(final String correlationId, final List<OrderArticle> articles) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentEncoding("application/json");
        MessageReceiverUtils.sendMessage(articles, messageProperties, "articles.orderDeleted", GSON, amqpTemplate);
    }
}
