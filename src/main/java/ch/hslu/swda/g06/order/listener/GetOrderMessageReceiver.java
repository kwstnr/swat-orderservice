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

import ch.hslu.swda.g06.order.model.Order;
import ch.hslu.swda.g06.order.repository.IOrderRepository;

@Component
public class GetOrderMessageReceiver {
    private static final Gson GSON = new Gson();

    @Autowired
    private IOrderRepository orderRepository;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @RabbitListener(queues = "order.get")
    public void getOrder(Message message) {
        MessageProperties messageProperties = message.getMessageProperties();
        byte[] messageBody = message.getBody();
        String id = new String(messageBody, StandardCharsets.UTF_8);

        Order order = orderRepository.findById(id).orElse(null);

        MessageReceiverUtils.sendOrderResponse(order, messageProperties, GSON, amqpTemplate);
    }

    @RabbitListener(queues = "order.getAll")
    public void getAllOrders(Message message) {
        MessageProperties properties = message.getMessageProperties();
        List<Order> orders = orderRepository.findAll();
        MessageReceiverUtils.sendOrdersResponse(orders, properties, GSON, amqpTemplate);
    }
}
