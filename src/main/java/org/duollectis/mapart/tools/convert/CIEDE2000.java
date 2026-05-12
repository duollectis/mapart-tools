package org.duollectis.mapart.tools.convert;

public class CIEDE2000 {

    public static double calculateDeltaE2000(double[] lab1, double[] lab2) {
        double L1 = lab1[0], a1 = lab1[1], b1 = lab1[2];
        double L2 = lab2[0], a2 = lab2[1], b2 = lab2[2];
        double avgL = (L1 + L2) / 2.0;

        double C1 = Math.sqrt(a1 * a1 + b1 * b1);
        double C2 = Math.sqrt(a2 * a2 + b2 * b2);
        double avgC = (C1 + C2) / 2.0;

        double g = 0.5 * (1 - Math.sqrt(Math.pow(avgC, 7) / (Math.pow(avgC, 7) + Math.pow(25, 7))));
        double a1p = a1 * (1 + g);
        double a2p = a2 * (1 + g);

        double C1p = Math.sqrt(a1p * a1p + b1 * b1);
        double C2p = Math.sqrt(a2p * a2p + b2 * b2);
        double avgCp = (C1p + C2p) / 2.0;

        double h1p = Math.toDegrees(Math.atan2(b1, a1p));

        if (h1p < 0) {
            h1p += 360;
        }

        double h2p = Math.toDegrees(Math.atan2(b2, a2p));

        if (h2p < 0) {
            h2p += 360;
        }

        double avgHp = Math.abs(h1p - h2p) > 180 ? (h1p + h2p + 360) / 2.0 : (h1p + h2p) / 2.0;
        double T = 1 - 0.17 * Math.cos(Math.toRadians(avgHp - 30))
                + 0.24 * Math.cos(Math.toRadians(2 * avgHp))
                + 0.32 * Math.cos(Math.toRadians(3 * avgHp + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * avgHp - 63));

        double dhp = h2p - h1p;

        if (Math.abs(dhp) > 180) {
            dhp = h2p <= h1p ? dhp + 360 : dhp - 360;
        }

        double dLp = L2 - L1;
        double dCp = C2p - C1p;
        double dHp = 2 * Math.sqrt(C1p * C2p) * Math.sin(Math.toRadians(dhp / 2.0));

        double sL = 1 + (0.015 * Math.pow(avgL - 50, 2)) / Math.sqrt(20 + Math.pow(avgL - 50, 2));
        double sC = 1 + 0.045 * avgCp;
        double sH = 1 + 0.015 * avgCp * T;

        double dTheta = 30 * Math.exp(-Math.pow((avgHp - 275) / 25, 2));
        double rc = 2 * Math.sqrt(Math.pow(avgCp, 7) / (Math.pow(avgCp, 7) + Math.pow(25, 7)));
        double rt = -rc * Math.sin(Math.toRadians(2 * dTheta));

        return Math.sqrt(Math.pow(dLp / sL, 2) + Math.pow(dCp / sC, 2) + Math.pow(dHp / sH, 2)
                + rt * (dCp / sC) * (dHp / sH));
    }
}
