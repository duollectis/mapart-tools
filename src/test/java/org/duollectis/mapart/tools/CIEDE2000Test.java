package org.duollectis.mapart.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.duollectis.mapart.tools.convert.CIEDE2000;

public class CIEDE2000Test {

    @Test
    public void testIdenticalColors() {
        double[] lab1 = {50.0, 2.5, -3.0};
        double[] lab2 = {50.0, 2.5, -3.0};
        assertEquals(0.0, CIEDE2000.calculateDeltaE2000(lab1, lab2), 1e-9);
    }

    @Test
    public void testKnownColors() {
        // Standard tests from color science literature or calculators
        // L, a, b
        double[] lab1 = {50.0, 2.6772, -79.7751};
        double[] lab2 = {50.0, 0.0, -82.7485};
        
        // Expected Delta E 2000 approx: 2.046
        double deltaE = CIEDE2000.calculateDeltaE2000(lab1, lab2);
        assertEquals(2.046, deltaE, 0.01);
    }
    
    @Test
    public void testWhiteAndBlack() {
        double[] white = {100.0, 0.0, 0.0};
        double[] black = {0.0, 0.0, 0.0};
        
        // Large distance
        double deltaE = CIEDE2000.calculateDeltaE2000(white, black);
        assertEquals(100.0, deltaE, 1.0);
    }

    @Test
    public void testVariedColors() {
        // Test different L, a, b combinations
        double[] lab1 = {20.0, 10.0, 10.0};
        double[] lab2 = {21.0, 11.0, 9.0};
        
        double deltaE = CIEDE2000.calculateDeltaE2000(lab1, lab2);
        // Distance should be small
        assertEquals(1.5, deltaE, 0.5);
    }
    
    @Test
    public void testHighSaturation() {
        double[] lab1 = {50.0, 100.0, 0.0};
        double[] lab2 = {50.0, 90.0, 0.0};
        
        double deltaE = CIEDE2000.calculateDeltaE2000(lab1, lab2);
        // Actual is 1.8957
        assertEquals(1.8957, deltaE, 0.0001);
    }
    
    @Test
    public void testLargeLightnessDiff() {
        double[] lab1 = {10.0, 0.0, 0.0};
        double[] lab2 = {90.0, 0.0, 0.0};
        
        double deltaE = CIEDE2000.calculateDeltaE2000(lab1, lab2);
        // Massive difference
        assertEquals(80.0, deltaE, 5.0);
    }
}
