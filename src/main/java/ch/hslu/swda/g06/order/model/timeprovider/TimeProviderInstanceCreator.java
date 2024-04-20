package ch.hslu.swda.g06.order.model.timeprovider;

import java.lang.reflect.Type;

import com.google.gson.InstanceCreator;

public class TimeProviderInstanceCreator implements InstanceCreator<ITimeProvider> {
    @Override
    public ITimeProvider createInstance(Type type) {
        return new SystemTimeProvider();
    }
}
