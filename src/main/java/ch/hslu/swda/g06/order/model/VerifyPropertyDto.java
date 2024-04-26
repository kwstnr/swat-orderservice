package ch.hslu.swda.g06.order.model;

public class VerifyPropertyDto<T> {
    private String orderId;
    private T propertyValue;
    private boolean verified;
    private Reason reason;

    public VerifyPropertyDto(final String orderId, final T propertyValue, final boolean verified, final Reason reason) {
        this(orderId, propertyValue, verified);
        this.reason = reason;
    }

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

    public static class Builder {
        public static <T> OrderId<T> builder() {
            return orderId -> propertyValue -> verified -> new BuilderStep<T>(orderId, propertyValue, verified);
        }

        public static class BuilderStep<T> {
            private final String orderId;
            private final T propertyValue;
            private final boolean verified;

            public BuilderStep(String orderId, T propertyValue, boolean verified) {
                this.orderId = orderId;
                this.propertyValue = propertyValue;
                this.verified = verified;
            }

            public VerifyPropertyDto<T> withReason(Reason reason) {
                return new VerifyPropertyDto<>(orderId, propertyValue, verified, reason);
            }

            public VerifyPropertyDto<T> withoutReason() {
                return new VerifyPropertyDto<>(orderId, propertyValue, verified);
            }
        }

        public interface OrderId<T> {
            PropertyValue<T> withOrderId(String orderId);
        }

        public interface PropertyValue<T> {
            Verified<T> withPropertyValue(T propertyValue);
        }

        public interface Verified<T> {
            BuilderStep<T> withVerified(boolean verified);
        }
    }
}
