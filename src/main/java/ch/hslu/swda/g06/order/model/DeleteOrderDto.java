package ch.hslu.swda.g06.order.model;

public class DeleteOrderDto {
    private String orderId;
    private long eTag;

    public DeleteOrderDto() {
    }

    public String getOrderId() {
        return this.orderId;
    }

    public long getETag() {
        return this.eTag;
    }
}
