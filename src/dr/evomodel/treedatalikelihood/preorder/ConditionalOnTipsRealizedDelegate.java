package dr.evomodel.treedatalikelihood.preorder;

import dr.evolution.tree.Tree;
import dr.evomodel.continuous.MultivariateDiffusionModel;
import dr.evomodel.treedatalikelihood.continuous.ConjugateRootTraitPrior;
import dr.evomodel.treedatalikelihood.continuous.ContinuousDataLikelihoodDelegate;
import dr.evomodel.treedatalikelihood.continuous.ContinuousRateTransformation;
import dr.evomodel.treedatalikelihood.continuous.ContinuousTraitPartialsProvider;
import dr.math.distributions.MultivariateNormalDistribution;
import dr.math.matrixAlgebra.WrappedVector;

/**
 * @author Marc A. Suchard
 */
public class ConditionalOnTipsRealizedDelegate extends AbstractRealizedContinuousTraitDelegate {

    static final private boolean DEBUG = false;

    final protected int dimPartial;

    public ConditionalOnTipsRealizedDelegate(String name,
                                             Tree tree,
                                             MultivariateDiffusionModel diffusionModel,
                                             ContinuousTraitPartialsProvider dataModel,
                                             ConjugateRootTraitPrior rootPrior,
                                             ContinuousRateTransformation rateTransformation,
//                                             BranchRateModel rateModel,
                                             ContinuousDataLikelihoodDelegate likelihoodDelegate) {
        super(name, tree, diffusionModel, dataModel, rootPrior, rateTransformation, likelihoodDelegate);

        this.likelihoodDelegate = likelihoodDelegate;
        this.dimPartial = dimTrait + likelihoodDelegate.getPrecisionType().getMatrixLength(dimTrait);
        partialNodeBuffer = new double[numTraits * dimPartial];
        partialPriorBuffer = new double[numTraits * dimPartial];

        tmpMean = new double[dimTrait];
    }

    @Override
    protected void simulateRoot(final int nodeIndex) {

        likelihoodDelegate.getIntegrator().getPostOrderPartial(
                likelihoodDelegate.getRootProcessDelegate().getPriorBufferIndex(),
                partialPriorBuffer);

        likelihoodDelegate.getPostOrderPartial(nodeIndex, partialNodeBuffer);

        if (DEBUG) {
            System.err.println("Simulate root node " + nodeIndex);
        }

        int offsetPartial = 0;
        int offsetSample = dimNode * nodeIndex;
        for (int trait = 0; trait < numTraits; ++trait) {

            simulateTraitForRoot(offsetSample, offsetPartial);

            offsetSample += dimTrait;
            offsetPartial += dimPartial;
        }
    }

    protected void simulateTraitForRoot(final int offsetSample, final int offsetPartial) {

        final double rootPrec = partialNodeBuffer[offsetPartial + dimTrait];

        if (DEBUG) {
            System.err.println("\trootPrec: " + rootPrec);
        }

        if (Double.isInfinite(rootPrec)) {

            System.arraycopy(partialNodeBuffer, offsetPartial, sample, offsetSample, dimTrait);

        } else {

            final double priorPrec = partialPriorBuffer[offsetPartial + dimTrait];
            final double totalPrec = priorPrec + rootPrec;

            for (int i = 0; i < dimTrait; ++i) {
                tmpMean[i] = (rootPrec * partialNodeBuffer[offsetPartial + i]
                        + priorPrec * partialPriorBuffer[offsetPartial + i])
                        / totalPrec;
            }

            if (DEBUG) {
                System.err.println("\tpriorPrec: " + priorPrec);
                System.err.println("\trootMean: " + new WrappedVector.Raw(partialNodeBuffer, offsetPartial, dimTrait));
                System.err.println("\tprioMean: " + new WrappedVector.Raw(partialPriorBuffer, offsetPartial, dimTrait));
                System.err.println("\tweigMean: " + new WrappedVector.Raw(tmpMean, 0, dimTrait));
            }


            final double sqrtScale = Math.sqrt(1.0 / totalPrec);

            MultivariateNormalDistribution.nextMultivariateNormalCholesky(
                    tmpMean, 0, // input mean
                    cholesky, sqrtScale, // input variance
                    sample, offsetSample, // output sample
                    tmpEpsilon);

            if (DEBUG) {
                System.err.println("\tsample: " + new WrappedVector.Raw(sample, offsetSample, dimTrait));
            }
        }
    }

    @Override
    protected void simulateNode(final BranchNodeOperation operation, final double branchNormalization) {
        final int nodeIndex = operation.getNodeNumber();
        likelihoodDelegate.getPostOrderPartial(nodeIndex, partialNodeBuffer);

        int offsetPartial = 0;
        int offsetSample = dimNode * nodeIndex;
        int offsetParent = dimNode * operation.getParentNumber();

        final double branchPrecision = 1.0 / (operation.getBranchLength() * branchNormalization);

        if (DEBUG) {
            System.err.println("Simulate for node " + nodeIndex);
        }
        for (int trait = 0; trait < numTraits; ++trait) {

            simulateTraitForNode(nodeIndex, trait, offsetSample, offsetParent, offsetPartial, branchPrecision);

            offsetSample += dimTrait;
            offsetParent += dimTrait;
            offsetPartial += dimPartial;
        }
    }

    @Override
    protected void simulateNode(NodeOperation operation) {

    }

    protected void simulateTraitForNode(final int nodeIndex,
                                        final int traitIndex,
                                        final int offsetSample,
                                        final int offsetParent,
                                        final int offsetPartial,
                                        final double branchPrecision) {

        final double nodePrecision = partialNodeBuffer[offsetPartial + dimTrait];

        if (Double.isInfinite(nodePrecision)) {

            if (DEBUG) {
                System.err.println("\tCopy from node partial");
            }

            System.arraycopy(partialNodeBuffer, offsetPartial, sample, offsetSample, dimTrait);

        } else if (Double.isInfinite(branchPrecision)) {

            if (DEBUG) {
                System.err.println("\tCopy from parent sample");
            }

            System.arraycopy(sample, offsetParent, sample, offsetSample, dimTrait);

        } else {

            final double totalPrecision = nodePrecision + branchPrecision;

            for (int i = 0; i < dimTrait; ++i) {
                tmpMean[i] = (nodePrecision * partialNodeBuffer[offsetPartial + i]
                        + branchPrecision * sample[offsetParent + i]) / totalPrecision;
            }

            final double sqrtScale = Math.sqrt(1.0 / totalPrecision);

            MultivariateNormalDistribution.nextMultivariateNormalCholesky(
                    tmpMean, 0, // input mean
                    cholesky, sqrtScale, // input variance
                    sample, offsetSample, // output sample
                    tmpEpsilon);
        }

        if (DEBUG) {
            System.err.println("\tSample value: " + new WrappedVector.Raw(sample, offsetSample, dimTrait));
        }
    }

    final ContinuousDataLikelihoodDelegate likelihoodDelegate;
    final double[] partialNodeBuffer;
    final double[] partialPriorBuffer;
    final double[] tmpMean;
}
