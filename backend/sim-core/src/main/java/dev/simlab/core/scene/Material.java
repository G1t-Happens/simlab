package dev.simlab.core.scene;

public record Material(Color color, Finish finish) {

    public enum Finish {
        /** diffuse, like paper or plaster */
        MATTE,
        /** shiny with soft highlights, like polished plastic */
        GLOSSY,
        /** soft with a sheen at grazing angles, like cotton or velvet */
        FABRIC
    }
}
