/*
 * MG94CodonModel.java
 *
 * Copyright (c) 2002-2016 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.substmodel.codon;

import dr.evomodel.substmodel.*;
import dr.evolution.datatype.Codons;
import dr.inference.model.Parameter;
import dr.math.matrixAlgebra.WrappedMatrix;
import dr.util.Author;
import dr.util.Citable;
import dr.util.Citation;

import java.util.Collections;
import java.util.List;

/**
 * Muse-Gaut model of codon evolution
 *
 * @author Marc A. Suchard
 * @author Guy Baele
 * @author Philippe lemey
 */
public class MG94CodonModel extends AbstractCodonModel implements Citable,
        ParameterReplaceableSubstitutionModel, DifferentialMassProvider {

    protected Parameter alphaParameter;
    protected Parameter betaParameter;

    protected final int numSynTransitions;
    protected final int numNonsynTransitions;

    public MG94CodonModel(Codons codonDataType, Parameter alphaParameter, Parameter betaParameter,
                          FrequencyModel freqModel) {
        this(codonDataType, alphaParameter, betaParameter, freqModel,
                new DefaultEigenSystem(codonDataType.getStateCount()));
    }

    public MG94CodonModel(Codons codonDataType,
                          Parameter alphaParameter,
                          Parameter betaParameter,
                          FrequencyModel freqModel, EigenSystem eigenSystem) {
        super("MG94", codonDataType, freqModel, eigenSystem);

        this.alphaParameter = alphaParameter;
        addVariable(alphaParameter);
        alphaParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 0.0,
                alphaParameter.getDimension()));

        this.betaParameter = betaParameter;
        addVariable(betaParameter);
        betaParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 0.0,
                betaParameter.getDimension()));

        numSynTransitions = getNumSynTransitions();
        numNonsynTransitions = getNumNonsynTransitions();
    }

    private int countRates(int i, int j) {
        int count = 0;

        for (byte rate : rateMap) {
            if (rate == i || rate == j) {
                count++;
            }
        }
        return count;
    }

    protected int getNumSynTransitions() {
        return 2 * countRates(1, 2);
    }

    protected int getNumNonsynTransitions() {
        return 2 * countRates(3, 4);
    }

    protected double getNormalizationValue(double[][] matrix, double[] pi) {
        double norm = 1.0;
        if (doNormalization) {
            double ratio =
                    getAlpha() + getBeta();
            //(numSynTransitions * getAlpha() + numNonsynTransitions * getBeta()) / (numSynTransitions + numNonsynTransitions);
            norm = super.getNormalizationValue(matrix, pi) / ratio;
        }
        return norm;
    }

    public double getAlpha() {
        return alphaParameter.getParameterValue(0);
    }

    public double getBeta() {
        return betaParameter.getParameterValue(0);
    }

    protected void setupRelativeRates(double[] rates) {

        double alpha = getAlpha() / numSynTransitions;
        double beta = getBeta() / numNonsynTransitions;
        for (int i = 0; i < rateCount; i++) {
            switch (rateMap[i]) {
                case 0:
                    rates[i] = 0.0;
                    break;            // codon changes in more than one codon position
                case 1:
                    rates[i] = alpha;
                    break;        // synonymous transition
                case 2:
                    rates[i] = alpha;
                    break;        // synonymous transversion
                case 3:
                    rates[i] = beta;
                    break;         // non-synonymous transition
                case 4:
                    rates[i] = beta;
                    break;            // non-synonymous transversion
            }
        }
    }

    public void setNormalization(boolean normalize) {
        this.doNormalization = normalize;
    }

    private boolean doNormalization = true;

    @Override
    public Citation.Category getCategory() {
        return Citation.Category.SUBSTITUTION_MODELS;
    }

    @Override
    public String getDescription() {
        return "Muse-Gaut codon substitution model";
    }

    @Override
    public List<Citation> getCitations() {
        return Collections.singletonList(CITATION);
    }

    public static Citation CITATION = new Citation(
            new Author[]{
                    new Author("SV", "Muse"),
                    new Author("BS", "Gaut")
            },
            "A likelihood approach for comparing synonymous and nonsynonymous nucleotide substitution rates, with application to the chloroplast genome",
            1994,
            "Mol Biol Evol",
            11, 715, 724
    );

    @Override
    public ParameterReplaceableSubstitutionModel factory(Parameter oldParameter, Parameter newParameter) {
        if (oldParameter == alphaParameter) {
            return new MG94CodonModel(codonDataType, newParameter, betaParameter, freqModel);
        } else if (oldParameter == betaParameter) {
            return new MG94CodonModel(codonDataType, alphaParameter, newParameter, freqModel);
        } else {
            throw new RuntimeException("Not yet implemented!");
        }
    }

    @Override
    public double[] getDifferentialMassMatrix(double time, Parameter parameter) {
        WrappedMatrix.ArrayOfArray infinitesimalDifferentialMatrix = getInfinitesimalDifferentialMatrix(parameter);
        double[] result = DifferentiableSubstitutionModelUtil.getDifferentialMassMatrix(time, stateCount, infinitesimalDifferentialMatrix, eigenDecomposition);
        return result;
    }

    protected WrappedMatrix.ArrayOfArray getInfinitesimalDifferentialMatrix(Parameter parameter) {
        if (parameter == alphaParameter || parameter == betaParameter) {
            final double alphaPlusBetaInverse = 1.0 / (getAlpha() + getBeta());
            final double normalizingConstant = setupMatrix();
            final double[] Q = new double[stateCount * stateCount];
            getInfinitesimalMatrix(Q);
            final double[] differentialRates = new double[rateCount];
            setupDifferentialRates(parameter, differentialRates, normalizingConstant);


            WrappedMatrix.ArrayOfArray differentialMassMatrix = new WrappedMatrix.ArrayOfArray(new double[stateCount][stateCount]);
            setupQMatrix(differentialRates, freqModel.getFrequencies(), differentialMassMatrix.getArrays());
            makeValid(differentialMassMatrix.getArrays(), stateCount);
            final double weightedNormalizationGradient
                    = getNormalizationValue(differentialMassMatrix.getArrays(), freqModel.getFrequencies()) - alphaPlusBetaInverse;

            for (int i = 0; i < stateCount; i++) {
                for (int j = 0; j < stateCount; j++) {
                    final double result = differentialMassMatrix.get(i, j) - Q[i * stateCount + j] * weightedNormalizationGradient;
                    differentialMassMatrix.set(i, j, result);
                }
            }

            return differentialMassMatrix;
        } else {
            throw new RuntimeException("Not yet implemented");
        }
    }

    protected void setupDifferentialRates(Parameter parameter, double[] differentialRates, double normalizingConstant) {
        if (parameter == alphaParameter) {
            for (int i = 0; i < rateCount; i++) {
                switch (rateMap[i]) {
                    case 0:
                        differentialRates[i] = 0.0;
                        break;
                    case 1:
                        differentialRates[i] = 1.0 / normalizingConstant / numSynTransitions;
                        break;        // synonymous transition
                    case 2:
                        differentialRates[i] = 1.0 / normalizingConstant / numSynTransitions;
                        break;        // synonymous transversion
                    case 3:
                        differentialRates[i] = 0.0;
                        break;
                    case 4:
                        differentialRates[i] = 0.0;
                        break;
                }
            }
        } else if (parameter == betaParameter) {
            for (int i = 0; i < rateCount; i++) {
                switch (rateMap[i]) {
                    case 0:
                        differentialRates[i] = 0.0;
                        break;
                    case 1:
                        differentialRates[i] = 0.0;
                        break;
                    case 2:
                        differentialRates[i] = 0.0;
                        break;
                    case 3:
                        differentialRates[i] = 1.0/ normalizingConstant / numNonsynTransitions;
                        break;         // non-synonymous transition
                    case 4:
                        differentialRates[i] = 1.0 / normalizingConstant / numNonsynTransitions;
                        break;            // non-synonymous transversion
                }
            }
        } else {
            throw new RuntimeException("Not yet implemented!");
        }
    }
}