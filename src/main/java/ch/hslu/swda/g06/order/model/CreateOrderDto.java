package ch.hslu.swda.g06.order.model;

import java.io.Serializable;
import java.util.List;

public class CreateOrderDto implements Serializable {
    private String customerId;
    private String employeeId;
    private List<OrderArticle> articles;
    private String filialId;

    public CreateOrderDto(final String customerId, final String employeeId,
            final List<OrderArticle> articles, final String filialId) {
        this.customerId = customerId;
        this.employeeId = employeeId;
        this.articles = articles;
        this.filialId = filialId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public List<OrderArticle> getArticles() {
        return articles;
    }

    public String getFilialId() {
        return filialId;
    }
}
