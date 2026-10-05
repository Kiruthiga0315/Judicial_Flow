package com.judicialflow.duration.service;

import lombok.Getter;

@Getter
public class SimpleLinearRegression {
    private final double intercept;
    private final double slope;
    private final double mae;
    private final int sampleSize;

    public SimpleLinearRegression(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y must have same length");
        }
        this.sampleSize = x.length;
        if (sampleSize == 0) {
            this.intercept = 0;
            this.slope = 0;
            this.mae = 0;
            return;
        } else if (sampleSize == 1) {
            this.intercept = y[0];
            this.slope = 0;
            this.mae = 0;
            return;
        }

        double sumX = 0, sumY = 0, sumX2 = 0;
        for (int i = 0; i < sampleSize; i++) {
            sumX += x[i];
            sumY += y[i];
            sumX2 += x[i] * x[i];
        }
        
        double xBar = sumX / sampleSize;
        double yBar = sumY / sampleSize;

        double xxBar = 0, xyBar = 0;
        for (int i = 0; i < sampleSize; i++) {
            xxBar += (x[i] - xBar) * (x[i] - xBar);
            xyBar += (x[i] - xBar) * (y[i] - yBar);
        }
        
        if (xxBar == 0) {
            this.slope = 0;
            this.intercept = yBar;
        } else {
            this.slope = xyBar / xxBar;
            this.intercept = yBar - slope * xBar;
        }
        
        // Calculate MAE on training data
        double totalError = 0;
        for (int i = 0; i < sampleSize; i++) {
            double predicted = predict(x[i]);
            totalError += Math.abs(predicted - y[i]);
        }
        this.mae = totalError / sampleSize;
    }

    public double predict(double x) {
        return intercept + slope * x;
    }
}
