package dev.simlab.server.simulation;

import dev.simlab.core.SimulationDefinition;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Offers every simulation of {@code sim-core}: each implementation of {@link SimulationDefinition} in its
 * packages becomes a bean without being registered anywhere
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan(
        basePackageClasses = SimulationDefinition.class,
        useDefaultFilters = false,
        includeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SimulationDefinition.class))
class SimulationConfiguration {}
