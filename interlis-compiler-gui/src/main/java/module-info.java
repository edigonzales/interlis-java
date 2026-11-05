/** Optional desktop host for the compiler and shared diagram renderer. */
module guru.interlis.compiler.gui {
    requires guru.interlis.projection;
    requires guru.interlis.workspace;
    requires javafx.controls;
    requires javafx.web;
    requires jdk.jsobject;
    requires jdk.httpserver;
    exports guru.interlis.compiler.gui to javafx.graphics;
    opens guru.interlis.compiler.gui to javafx.web;
}
