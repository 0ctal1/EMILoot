package btw.community.emiloot.data;

import java.util.Random;

final class ProbeRandom extends Random {
    private double probability = 1.0;
    @Override public int nextInt(int bound) { if (bound > 0) { probability /= bound; } return 0; }
    double probability() {
        return probability;
    }
}
