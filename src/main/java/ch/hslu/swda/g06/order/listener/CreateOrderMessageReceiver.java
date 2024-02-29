package ch.hslu.swda.g06.order.listener;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import ch.hslu.swda.g06.order.Logging.SendLog;
import ch.hslu.swda.g06.order.model.CreateOrderDto;
import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.model.OrderArticle;
import ch.hslu.swda.g06.order.model.VerifyPropertyDto;
import ch.hslu.swda.g06.order.repository.IOrderRepository;

@Component
public class CreateOrderMessageReceiver {
        private static final Gson GSON = new Gson();

        @Autowired
        private IOrderRepository orderRepository;

        @Autowired
        private AmqpTemplate amqpTemplate;

        @RabbitListener(queues = "order.post")
        public void createOrder(Message message) {
                MessageProperties properties = message.getMessageProperties();
                CreateOrderDto orderDto = GSON.fromJson(new String(message.getBody(), StandardCharsets.UTF_8),
                                CreateOrderDto.class);
                Order order = new Order(orderDto);
                orderRepository.save(order);

                SendLog.sendOrderCreated(order, properties.getCorrelationId(), GSON,
                                amqpTemplate);

                this.sendVerifyUserMessage(properties.getCorrelationId(),
                                orderDto.getCustomerId(), order.getOrderId());
                this.sendVerifyArticlesMessage(properties.getCorrelationId(),
                                orderDto.getArticles(), order.getOrderId());
                MessageReceiverUtils.sendOrderResponse(order, properties, GSON, amqpTemplate);
        }

    private void sendVerifyArticlesMessage(final String correlationId, final List<OrderArticle> articles,
            final String orderId) {
        VerifyPropertyDto<List<OrderArticle>> verifyPropertyDto = new VerifyPropertyDto<>(orderId,
                articles);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentEncoding("application/json");
        messageProperties.setReplyTo("order.verifyArticle");
        MessageReceiverUtils.sendMessage(verifyPropertyDto, messageProperties, "article.verify", GSON, amqpTemplate);
    }

    private void sendVerifyUserMessage(final String correlationId, final String userId, final String orderId) {
        VerifyPropertyDto<String> verifyPropertyDto = new VerifyPropertyDto<>(orderId, userId);
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(correlationId);
        messageProperties.setContentEncoding("application/json");
        messageProperties.setReplyTo("order.verifyCustomer");
        MessageReceiverUtils.sendMessage(verifyPropertyDto, messageProperties, "user.verify", GSON, amqpTemplate);
    }
}
