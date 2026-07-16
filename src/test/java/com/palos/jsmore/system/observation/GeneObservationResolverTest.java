package com.palos.jsmore.system.observation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeneObservationResolverTest {
    @Test
    void malformedGeneIdentifiersAreSkippedWithoutBreakingTheSnapshot() {
        GeneticModule module = new GeneticModule(List.of(
                new InvalidStringGene(),
                new ThrowingStringGene()
        ));

        List<ObservedGene> genes = assertDoesNotThrow(() -> GeneObservationResolver.resolve(module));

        assertTrue(genes.isEmpty());
    }

    private static final class GeneticModule {
        private final GeneData geneData;

        private GeneticModule(List<Object> genes) {
            this.geneData = new GeneData(genes);
        }

        private GeneData getGeneData() {
            return this.geneData;
        }
    }

    private static final class GeneData {
        private final GeneHolder geneDataHolder;

        private GeneData(List<Object> genes) {
            this.geneDataHolder = new GeneHolder(genes);
        }
    }

    private static final class GeneHolder {
        private final List<Object> genes;

        private GeneHolder(List<Object> genes) {
            this.genes = genes;
        }

        private List<Object> getGENE_SET() {
            return this.genes;
        }
    }

    private static final class InvalidStringGene {
        @Override
        public String toString() {
            return "not a valid resource path";
        }
    }

    private static final class ThrowingStringGene {
        @Override
        public String toString() {
            throw new IllegalStateException("broken optional gene");
        }
    }
}
