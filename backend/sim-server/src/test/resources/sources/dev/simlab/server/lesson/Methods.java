package dev.simlab.server.lesson;

import java.util.List;

/** Methods whose source the tests cut out, sim-core's sources have no methods of the same name */
final class Methods {

    /** has Javadoc and an annotation */
    @Deprecated
    void documented() {
        // a comment inside the method
    }

    // has a line comment
    void commented() {}

    void bare() {}

    void scaled(double[] values, double factor) {}

    void scaled(List<String> names, int... indices) {}

    /** lets an amount decay at a rate, which neither this comment nor the string below holds */
    double decayed(double amount, double rate, double dt) {
        String unit = "rate in 1/s";
        return amount * Math.exp(-rate * dt);
    }
}
