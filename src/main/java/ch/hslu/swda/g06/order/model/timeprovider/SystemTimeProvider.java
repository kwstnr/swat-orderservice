package ch.hslu.swda.g06.order.model.timeprovider;

public class SystemTimeProvider implements ITimeProvider {
    @Override
    public long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }
}
