package com.moveinsync.distance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CoordinateTest {

    @Test
    @DisplayName("Valid coordinates are created and rounded to 7 decimal places")
    void testValidCoordinates() {
        Coordinate coord = Coordinate.of(12.9715987654, 77.5945678912);
        assertThat(coord.getLatitude()).isEqualTo(12.9715988);
        assertThat(coord.getLongitude()).isEqualTo(77.5945679);
    }

    @Test
    @DisplayName("Invalid latitudes and longitudes throw IllegalArgumentException")
    void testInvalidCoordinates() {
        assertThatThrownBy(() -> Coordinate.of(91.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be between -90 and +90");

        assertThatThrownBy(() -> Coordinate.of(-95.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Coordinate.of(12.0, 185.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be between -180 and +180");

        assertThatThrownBy(() -> Coordinate.of(Double.NaN, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Haversine distance between identical points is exactly 0.0")
    void testHaversineSamePoint() {
        Coordinate c = Coordinate.of(12.9716, 77.5946);
        assertThat(c.haversineDistanceKm(c)).isEqualTo(0.0);
        assertThat(c.haversineDistanceMeters(c)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Haversine distance Bangalore to Mumbai is approximately 840 km")
    void testHaversineBangaloreToMumbai() {
        Coordinate bangalore = Coordinate.of(12.9716, 77.5946);
        Coordinate mumbai = Coordinate.of(19.0760, 72.8777);

        double distance = bangalore.haversineDistanceKm(mumbai);
        assertThat(distance).isBetween(830.0, 850.0);
    }

    @Test
    @DisplayName("Spatial grid snapping collapses near-coincident coordinates to the same grid cell key")
    void testGridCellSnapping() {
        Coordinate officeEntrance1 = Coordinate.of(12.935212, 77.624481);
        Coordinate officeEntrance2 = Coordinate.of(12.935249, 77.624515);

        // At 4 decimal places (~11 meters), both snap to the same grid key
        String cell1 = officeEntrance1.toGridCell(4);
        String cell2 = officeEntrance2.toGridCell(4);

        assertThat(cell1).isEqualTo(cell2);
    }
}
