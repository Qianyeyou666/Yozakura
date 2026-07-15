package gq.yozakura.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientLanguageTest {
    @Test
    public void exposesEnglishAndChineseDisplayModes() {
        assertFalse(ClientLanguage.ENGLISH.isChinese());
        assertTrue(ClientLanguage.CHINESE.isChinese());
        assertEquals("English", ClientLanguage.ENGLISH.getDisplayName());
        assertEquals("中文", ClientLanguage.CHINESE.getDisplayName());
    }

    @Test
    public void resolvesLocalizedTextFromTheSelectedLanguage() {
        assertEquals("Language", ClientLanguage.ENGLISH.select("Language", "语言"));
        assertEquals("语言", ClientLanguage.CHINESE.select("Language", "语言"));
    }
}
