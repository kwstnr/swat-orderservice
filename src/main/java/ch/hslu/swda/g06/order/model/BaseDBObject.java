package ch.hslu.swda.g06.order.model;

import ch.hslu.swda.g06.order.model.timeprovider.ITimeProvider;

public class BaseDBObject {
    private long eTag;

    private ITimeProvider timeProvider;

    public BaseDBObject(ITimeProvider timeProvider, final long eTag) {
        this(timeProvider);
        this.eTag = eTag;
    }

    public BaseDBObject(ITimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public long getEtag() {
        return this.eTag;
    }

    public void setCurrentEtag() {
        this.eTag = this.timeProvider.getCurrentTimeMillis();
    }

    public boolean canEdit(long eTag) {
        return eTag == this.eTag;
    }
}
