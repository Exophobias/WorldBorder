package com.wimbli.WorldBorder;

import de.bluecolored.bluemap.api.math.Shape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlueMapLinkTest {
    private static final BorderData PATRIAM_RECTANGLE =
        new BorderData(8680, 15384, 4184, 7128, false);

    @Test
    void rectangularMarkerUsesConfiguredCornersExactly() {
        Shape shape = BlueMapLink.borderShape(PATRIAM_RECTANGLE, false);

        assertNotNull(shape);
        assertEquals(4, shape.getPointCount());
        assertEquals(4496, shape.getMin().getX());
        assertEquals(8256, shape.getMin().getY());
        assertEquals(12864, shape.getMax().getX());
        assertEquals(22512, shape.getMax().getY());
    }

    @Test
    void ellipticMarkerUsesBothConfiguredRadii() {
        Shape shape = BlueMapLink.borderShape(PATRIAM_RECTANGLE, true);

        assertNotNull(shape);
        assertEquals(512, shape.getPointCount());
        assertEquals(4496, shape.getMin().getX(), 0.000001);
        assertEquals(8256, shape.getMin().getY(), 0.000001);
        assertEquals(12864, shape.getMax().getX(), 0.000001);
        assertEquals(22512, shape.getMax().getY(), 0.000001);
    }

    @Test
    void invalidBorderIsNotPublished() {
        assertNull(BlueMapLink.borderShape(new BorderData(0, 0, 0, 10), false));
    }
}
