package ch.hslu.swda.g06.order.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.hslu.swda.g06.order.model.timeprovider.ITimeProvider;

class BaseDBObjectTest {
    private BaseDBObject baseDBObject;
    private ITimeProvider timeProvider;
    private long eTag;

    @BeforeEach
    public void setup() {
        eTag = 12345;
        timeProvider = mock(ITimeProvider.class);
        baseDBObject = new BaseDBObject(timeProvider, eTag);
    }

    @Test
    void testGetEtag() {
        long result = baseDBObject.getEtag();
        assertEquals(eTag, result);
    }

    @Test
    void testCanEditFalse() {
        boolean canEdit = baseDBObject.canEdit(54321);
        assertFalse(canEdit);
    }

    @Test
    void testCanEditTrue() {
        boolean canEdit = baseDBObject.canEdit(eTag);
        assertTrue(canEdit);
    }

    @Test
    void testSetCurrentEtag() {
        long fixedTime = 123456789L;
        when(timeProvider.getCurrentTimeMillis()).thenReturn(fixedTime);

        baseDBObject.setCurrentEtag();
        long result = baseDBObject.getEtag();

        assertEquals(fixedTime, result);
    }
}
