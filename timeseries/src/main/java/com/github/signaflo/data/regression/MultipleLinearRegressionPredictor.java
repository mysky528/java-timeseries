/*
 * Copyright (c) 2017 Jacob Rachiele
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to
 * do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *
 * Jacob Rachiele
 */

package com.github.signaflo.data.regression;

import com.github.signaflo.data.DoublePair;
import lombok.ToString;
import com.github.signaflo.math.linear.doubles.Matrix;
import com.github.signaflo.math.linear.doubles.QuadraticForm;
import com.github.signaflo.math.linear.doubles.Vector;
import com.github.signaflo.math.stats.distributions.Distribution;
import com.github.signaflo.math.stats.distributions.StudentsT;

import java.util.ArrayList;
import java.util.List;

/**
 * A predictor that makes predictions using a multiple linear regression model.
 */
@ToString
public class MultipleLinearRegressionPredictor implements LinearRegressionPredictor {

    private final LinearRegression model;
    private final Matrix XtXInverse;
    private final int degreesOfFreedom;

    private MultipleLinearRegressionPredictor(MultipleLinearRegression model) {
        this.model = model;
        this.XtXInverse = Matrix.create(model.XtXInverse());
        this.degreesOfFreedom = model.response().length - model.designMatrix().length;
    }

    /**
     * Create a new predictor from the given multiple linear regression model.
     *
     * @param model the regression model to base predictions off of.
     * @return a new predictor.
     */
    public static MultipleLinearRegressionPredictor from(MultipleLinearRegression model) {
        return new MultipleLinearRegressionPredictor(model);
    }

    private DoublePair getInterval(double sampleEstimate, double tValue, double standardError) {
        double lowerValue = sampleEstimate - tValue * standardError;
        double upperValue = sampleEstimate + tValue * standardError;
        return new DoublePair(lowerValue, upperValue);
    }

    @Override
    public LinearRegressionPrediction predict(Vector observation, double alpha) {
        double estimate = estimate(predictorWithIntercept(observation));
        double seFit = standardErrorFit(predictorWithIntercept(observation));
        DoublePair confidenceInterval = confidenceInterval(alpha, predictorWithIntercept(observation), estimate);
        DoublePair predictionInterval = predictionInterval(alpha, predictorWithIntercept(observation), estimate);
        return new MultipleLinearRegressionPrediction(estimate, seFit, confidenceInterval, predictionInterval);
    }

    private LinearRegressionPrediction predictWithIntercept(Vector vector, double alpha) {
        double estimate = estimate(vector);
        double seFit = standardErrorFit(vector);
        DoublePair confidenceInterval = confidenceInterval(alpha, vector, estimate);
        DoublePair predictionInterval = predictionInterval(alpha, vector, estimate);
        return new MultipleLinearRegressionPrediction(estimate, seFit, confidenceInterval, predictionInterval);
    }

    private double estimate(Vector data) {
        return data.dotProduct(Vector.from(model.beta()));
    }

    private Vector predictorWithIntercept(Vector newData) {
        if (model.hasIntercept()) {
            return newData.push(1.0);
        } else {
            return newData;
        }
    }

    private double standardErrorFit(Vector predictor) {
        double product = QuadraticForm.multiply(predictor, XtXInverse);
        return Math.sqrt(model.sigma2() * product);
    }

    private DoublePair confidenceInterval(double alpha, Vector predictor, double estimate) {
        Distribution T = new StudentsT(this.degreesOfFreedom);
        double tValue = T.quantile(1 - (alpha / 2.0));
        // send in predictor instead of newData since predict method also updates for intercept.
        double seFit = standardErrorFit(predictor);
        return getInterval(estimate, tValue, seFit);
    }

    private DoublePair predictionInterval(double alpha, Vector predictor, double estimate) {
        Distribution T = new StudentsT(this.degreesOfFreedom);
        double tValue = T.quantile(1 - (alpha / 2.0));
        double seFit = standardErrorFit(predictor);
        double standardError = Math.sqrt(model.sigma2() + seFit * seFit);
        return getInterval(estimate, tValue, standardError);
    }

    @Override
    public List<LinearRegressionPrediction> predict(Matrix observations, double alpha) {
        List<LinearRegressionPrediction> predictions = new ArrayList<>(observations.nrow());
        for (int i = 0; i < observations.nrow(); i++) {
            predictions.add(predict(observations.getRow(i), alpha));
        }
        return predictions;
    }

    /**
     * Predict a series of responses, one for each row in the design matrix.
     *
     * @param designMatrix the design matrix for the prediction.
     * @param alpha the significance level.
     * @return a list of predictions, one for each row in the design matrix.
     */
    public List<LinearRegressionPrediction> predictDesignMatrix(Matrix designMatrix, double alpha) {
        List<LinearRegressionPrediction> predictions = new ArrayList<>(designMatrix.nrow());
        for (int i = 0; i < designMatrix.nrow(); i++) {
            predictions.add(predictWithIntercept(designMatrix.getRow(i), alpha));
        }
        return predictions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MultipleLinearRegressionPredictor that = (MultipleLinearRegressionPredictor) o;

        return model.equals(that.model);
    }

    @Override
    public int hashCode() {
        return model.hashCode();
    }
}
