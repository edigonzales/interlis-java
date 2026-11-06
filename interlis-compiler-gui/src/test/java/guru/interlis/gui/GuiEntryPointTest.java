package guru.interlis.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GuiEntryPointTest {
    @Test
    void rejectsUnsupportedArgumentsBeforeAccessingTheDesktop() {
        assertThrows(IllegalArgumentException.class, () -> GuiMain.main(new String[]{"invalid"}));
    }
}
