package guru.interlis.validation;

/** Run-local basket occurrence qualified by its input. */
record BasketHandle(long input, long ordinal) {
    BasketHandle {
        if (input < 1 || ordinal < 1) {
            throw new IllegalArgumentException("Input and basket ordinals must be positive");
        }
    }
}
