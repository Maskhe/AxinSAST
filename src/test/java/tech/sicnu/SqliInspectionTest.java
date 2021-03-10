package tech.sicnu;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.Locale;

public class SqliInspectionTest extends JavaCodeInsightFixtureTestCase {
    public void testFindSqli() {
        myFixture.copyFileToProject("StringUtils.java");
        myFixture.copyFileToProject("MessageFormat.java");
        myFixture.enableInspections(new SqliInspection());
        myFixture.testHighlightingAllFiles(false, false, false, "Sqli.java");
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
        Locale.setDefault(Locale.CHINESE);
    }

    @Override
    protected String getTestDataPath() {
        return "src/test/testData/";
    }


}
