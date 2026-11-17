/** Host for the JVM validator command. */
module guru.interlis.validator.cli {
    requires java.xml;
    requires guru.interlis.compiler;
    requires guru.interlis.workspace;
    requires guru.interlis.repository;
    requires guru.interlis.transfer;
    requires guru.interlis.xtf;
    requires guru.interlis.validation;
    requires guru.interlis.validation.jts;
}
