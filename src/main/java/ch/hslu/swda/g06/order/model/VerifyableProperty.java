package ch.hslu.swda.g06.order.model;

public class VerifyableProperty<T> {
    private T value;
    private boolean verified;
    private Reason reason;

    public VerifyableProperty(final T value) {
        this.value = value;
        this.verified = false;
    }

    public boolean isVerified() {
        return this.verified;
    }

    public void verify() {
        this.verified = true;
    }

    public void setValue(final T value) {
        this.value = value;
    }

    public T getValue() {
        return this.value;
    }

    public Reason getReason() {
        return this.reason;
    }
}
