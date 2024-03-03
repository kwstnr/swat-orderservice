package ch.hslu.swda.g06.order.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

public class BaseDBObjectTest {

    @Test
    void testGetEtag() {
        long eTag = 12345;
        BaseDBObject item = new BaseDBObject(eTag);

        long result = item.getEtag();

        assertEquals(eTag, result);
    }

    @Test
    void testCanEditFalse() {
        long eTag = 12345;
        BaseDBObject item = new BaseDBObject(eTag);

        boolean canEdit = item.canEdit(54321);

        assertFalse(canEdit);
    }

    @Test
    void testCanEditTrue() {
        long eTag = 12345;
        BaseDBObject item = new BaseDBObject(eTag);

        boolean canEdit = item.canEdit(12345);

        assertTrue(canEdit);
    }
}
