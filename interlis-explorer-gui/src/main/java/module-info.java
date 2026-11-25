/** Local Swing application for browsing an INTERLIS transfer. */
module guru.interlis.explorer.gui {
    requires java.desktop;
    requires java.prefs;
    requires java.sql;
    requires com.formdev.flatlaf;
    requires org.fife.RSyntaxTextArea;
    requires org.fife.AutoComplete;
    requires guru.interlis.model;
    requires guru.interlis.compiler;
    requires guru.interlis.workspace;
    requires guru.interlis.repository;
    requires guru.interlis.transfer;
    requires guru.interlis.xtf;
    requires guru.interlis.explorer;
    requires guru.interlis.validation;
    requires guru.interlis.validation.jts;
}
