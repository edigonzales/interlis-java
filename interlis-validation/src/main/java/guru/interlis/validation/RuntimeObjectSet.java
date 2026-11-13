package guru.interlis.validation;

import java.util.OptionalLong;

/** Immutable scope of repeatable object-set queries. */
@FunctionalInterface
interface RuntimeObjectSet {
    OptionalLong count();
}
