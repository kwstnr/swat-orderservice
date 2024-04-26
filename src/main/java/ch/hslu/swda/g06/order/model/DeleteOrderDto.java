package ch.hslu.swda.g06.order.model;

public class DeleteOrderDto {
    private final String orderId;
    private final long eTag;

    private DeleteOrderDto(String orderId, long eTag) {
        this.orderId = orderId;
        this.eTag = eTag;
    }

    public String getOrderId() {
        return this.orderId;
    }

    public long getETag() {
        return this.eTag;
    }

    public static class Builder {
        public static OrderId builder() {
            return orderId -> eTag -> new DeleteOrderDto(orderId, eTag);
        }

        public interface OrderId {
            ETag withOrderId(String orderId);
        }

        public interface ETag {
            DeleteOrderDto withETag(long eTag);
        }
    }
}