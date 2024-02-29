package ch.hslu.swda.g06.order.model;

public class VerifyPropertyDto<T> {
    private String orderId;
    private T propertyValue;
    private boolean verified;
    private Reason reason;

    public VerifyPropertyDto(final String orderId, final T propertyValue, final boolean verified) {
        this.orderId = orderId;
        this.propertyValue = propertyValue;
        this.verified = verified;
    }

    public VerifyPropertyDto(String orderId, T propertyValue) {
        this(orderId, propertyValue, false);
    }

    public String getOrderId() {
        return orderId;
    }

    public T getPropertyValue() {
        return propertyValue;
    }

    public boolean getVerified() {
        return this.verified;
    }

    public Reason getReason() {
        return this.reason;
    }

    public void verify() {
        this.verified = true;
    }
}
