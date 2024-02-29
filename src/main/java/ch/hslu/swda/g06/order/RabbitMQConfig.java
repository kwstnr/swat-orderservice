package ch.hslu.swda.g06.order;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public Exchange swdaExchange() {
        return new TopicExchange("swda", true, false, Map.of("alternate-exchange", "swda.orphan"));
    }

    @Bean
    public Queue orderGetQueue() {
        return new Queue("order.get", false);
    }

    @Bean
    public Queue orderGetAllQueue() {
        return new Queue("order.getAll", false);
    }

    @Bean
    public Queue orderPostQueue() {
        return new Queue("order.post", false);
    }

    @Bean
    public Queue verifyCustomerQueue() {
        return new Queue("order.verifyCustomer", false);
    }

    @Bean
    public Queue verifyArticleQueue() {
        return new Queue("order.verifyArticle", false);
    }

    @Bean
    public Queue orderDeleteQueue() {
        return new Queue("order.delete", false);
    }

    @Bean
    public Binding bindOrderPostQueueToExchange(@Qualifier("orderPostQueue") Queue orderPostQueue,
            Exchange swdaExchange) {
        return BindingBuilder.bind(orderPostQueue).to(swdaExchange).with("order.post").noargs();
    }

    @Bean
    public Binding bindVerifyCustomerQueueToExchange(@Qualifier("verifyCustomerQueue") Queue verifyCustomerQueue,
            Exchange swdaExchange) {
        return BindingBuilder.bind(verifyCustomerQueue).to(swdaExchange).with("order.verifyCustomer").noargs();
    }

    @Bean
    public Binding bindVerifyArticleQueueToExchange(@Qualifier("verifyArticleQueue") Queue verifyArticleQueue,
            Exchange swdaExchange) {
        return BindingBuilder.bind(verifyArticleQueue).to(swdaExchange).with("order.verifyArticle").noargs();
    }

    @Bean
    public Binding bindOrderGetQueueToExchange(@Qualifier("orderGetQueue") Queue orderGetQueue,
            Exchange swdaExchange) {
        return BindingBuilder.bind(orderGetQueue).to(swdaExchange).with("order.get").noargs();
    }

    @Bean
    public Binding bindOrderGetAllQueueToExchange(@Qualifier("orderGetAllQueue") Queue orderGetAllQueue,
            Exchange swdaExchange) {
        return BindingBuilder.bind(orderGetAllQueue).to(swdaExchange).with("order.getAll").noargs();
    }

    @Bean
    public Binding bindOrderDeleteQueueToExchange(@Qualifier("orderDeleteQueue") Queue orderDeleteQueue,
            Exchange swdaExchange) {
        return BindingBuilder.bind(orderDeleteQueue).to(swdaExchange).with("order.delete").noargs();
    }
}
