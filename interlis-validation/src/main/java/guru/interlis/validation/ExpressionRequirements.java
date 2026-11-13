package guru.interlis.validation;

/** Runtime prerequisites discovered while binding a program. */
record ExpressionRequirements(boolean populationRequired, boolean datasetPathRequired, boolean relationPathRequired) { }
