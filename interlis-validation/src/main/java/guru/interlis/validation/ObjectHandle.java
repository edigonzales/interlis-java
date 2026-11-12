package guru.interlis.validation;

/** Run-local identity of one object occurrence, including anonymous links. */
record ObjectHandle(long ordinal) {
    ObjectHandle {
        if (ordinal < 1) {
            throw new IllegalArgumentException("Object ordinal must be positive");
        }
    }
}
