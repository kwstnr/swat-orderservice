package ch.hslu.swda.g06.order.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import ch.hslu.swda.g06.order.model.timeprovider.SystemTimeProvider;

@Document(collection = "orders")
public class Order extends BaseDBObject implements Serializable {
    @Id
    private String orderId;
    private VerifyableProperty<String> customerId;
    private String employeeId;
    private String isoDateString;
    private OrderState state;
    private List<VerifyableProperty<OrderArticle>> articles;
    private String filialId;
    private float totalPrice;

    public Order(final String customerId, final String employeeId,
            final String filialId, final List<OrderArticle> articles) {
        super(new SystemTimeProvider());
        this.orderId = UUID.randomUUID().toString();
        this.customerId = new VerifyableProperty<String>(customerId);
        this.employeeId = employeeId;
        this.state = OrderState.Bestellt;
        this.filialId = filialId;
        this.articles = articles.stream()
                .map(article -> new VerifyableProperty<OrderArticle>(article))
                .collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        this.isoDateString = now.format(DateTimeFormatter.ISO_DATE_TIME);
        this.totalPrice = calculateTotalPrice(articles);
        this.setCurrentEtag();
    }

    public Order(final CreateOrderDto orderDto) {
        this(orderDto.getCustomerId(), orderDto.getEmployeeId(),
                orderDto.getFilialId(), orderDto.getArticles());
    }

    public Order() {
        super(new SystemTimeProvider());
    }

    public String getOrderId() {
        return this.orderId;
    }

    public float getTotalPrice() {
        return this.totalPrice;
    }

    public void setOrderId(final String id) {
        this.orderId = id;
        this.setCurrentEtag();
    }

    public String getCustomerId() {
        return customerId.getValue();
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getIsoDateString() {
        return isoDateString;
    }

    public OrderState getState() {
        return state;
    }

    public List<OrderArticle> getArticles() {
        return articles.stream().map(article -> article.getValue()).collect(Collectors.toList());
    }

    public String getFilialId() {
        return filialId;
    }

    public void verifyCustomerId() {
        this.customerId.verify();
        if (hasNoUnverifiedArticle() && this.state != OrderState.Annuliert) {
            this.state = OrderState.Bestätigt;
        }
        this.setCurrentEtag();
    }

    public void verifyArticleId(final String articleId) {
        for (VerifyableProperty<OrderArticle> article : articles) {
            if (article.getValue().getArticleId().equals(articleId)) {
                article.verify();
                break;
            }
        }

        if (customerId.isVerified() && hasNoUnverifiedArticle() && this.state != OrderState.Annuliert) {
            this.state = OrderState.Bestätigt;
        }
        this.setCurrentEtag();
    }

    public void setOrderState(OrderState state) {
        this.state = state;
        this.setCurrentEtag();
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", customerId=-" + customerId.getValue() +
                ", employeeId=" + employeeId +
                ", isoDateString=" + isoDateString +
                ", state=" + state +
                ", articles=" + articles +
                ", filialId=" + filialId +
                '}';
    }

    private boolean hasNoUnverifiedArticle() {
        return articles.stream().anyMatch(article -> article.isVerified());
    }

    /**
     * Calculates the total price of a list of OrderArticles.
     *
     * @param orderArticles List of OrderArticle objects.
     * @return The total price.
     */
    private float calculateTotalPrice(List<OrderArticle> articles) {
        return articles.stream()
                .map(article -> article.getUnitPrice() * article.getAmount())
                .reduce(0f, Float::sum);
    }
}
