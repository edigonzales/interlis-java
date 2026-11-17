package guru.interlis.validator.cli;

import guru.interlis.validation.InputRole;

import java.nio.file.Path;

record ValidationInput(Path path, InputRole role, XtfHeaderProbe.Header header) {
    String format() {
        return header.xtf23() ? "xtf23" : "xtf24";
    }
}
