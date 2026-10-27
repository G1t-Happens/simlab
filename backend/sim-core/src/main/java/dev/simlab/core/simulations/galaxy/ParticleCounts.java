package dev.simlab.core.simulations.galaxy;

record ParticleCounts(int disk, int bulge, int halo) {

    ParticleCounts {
        if (disk < 1 || bulge < 1 || halo < 1) {
            throw new IllegalArgumentException(
                    "A galaxy needs particles in its disk, bulge and halo, but got %d, %d and %d"
                            .formatted(disk, bulge, halo));
        }
    }

    int stars() {
        return disk + bulge;
    }

    int total() {
        return disk + bulge + halo;
    }
}
