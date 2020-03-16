package com.crowdin.cli.commands.functionality;

import com.crowdin.cli.properties.Params;
import com.crowdin.cli.properties.PropertiesBean;
import com.crowdin.cli.properties.PropertiesBeanBuilder;
import com.crowdin.cli.properties.helper.TempProject;
import com.crowdin.cli.utils.Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PropertiesBuilderTest {

    private TempProject tempProject;

    @BeforeEach
    private void initFolder() {
        tempProject = new TempProject(PropertiesBuilderTest.class);
    }

    @AfterEach
    private void removeFolder() {
        tempProject.delete();
    }

    @Test
    public void testError_EverythingEmpty() {
        assertThrows(NullPointerException.class, () -> new PropertiesBuilder(null, null, null));
    }

    @Test
    public void testOk_MinimalConfigFile() {
        File configFile = new File("crowdin.yml");
        String minimalConfigFileText = new PropertiesBeanBuilder().minimalPropertiesBean().setBasePath(tempProject.getBasePath()).buildToString();
        PropertiesBean minimalBuiltConfigFile = new PropertiesBeanBuilder().minimalBuiltPropertiesBean().setBasePath(tempProject.getBasePath()).build();
        configFile = tempProject.addFile(configFile.getPath(), minimalConfigFileText);

        PropertiesBuilder pBuilder = new PropertiesBuilder(configFile, null, null);
        PropertiesBean builtPb = pBuilder.build();

        assertThat("PropertiesBeans are identical", builtPb, is(minimalBuiltConfigFile));
    }

    @Test
    public void testOk_Params_WithoutConfigFile() {
        Params okParams = new Params() {{
            setIdParam("666");
            setTokenParam("123abc456");
            setSourceParam("/hello/world");
            setTranslationParam("/hello/%two_letters_code%/%file_name%.%file_extension%");
        }};

        PropertiesBuilder pBuilder = new PropertiesBuilder(new File("/crowdin.yml"), null, okParams);
        PropertiesBean pb = pBuilder.build();

        assertEquals(pb.getPreserveHierarchy(), false);
        assertThat(pb.getBasePath(), matchesPattern("([a-zA-Z]:\\\\\\\\|/)"));
        assertEquals(pb.getFiles().size(), 1);
        assertEquals(pb.getFiles().get(0).getSource(), "hello" + Utils.PATH_SEPARATOR + "world");
    }

    @Test
    public void testOk_Params_WithConfigFile() {
        File configFile = new File("crowdin.yml");
        String minimalConfigFileText = new PropertiesBeanBuilder().minimalPropertiesBean().setBasePath(tempProject.getBasePath()).buildToString();
        PropertiesBean minimalBuiltConfigFile = new PropertiesBeanBuilder().minimalBuiltPropertiesBean().setBasePath(tempProject.getBasePath()).build();
        configFile = tempProject.addFile(configFile.getPath(), minimalConfigFileText);

        Params okParams = new Params() {{
            setIdParam("666");
            setTokenParam("123abc456");
            setSourceParam("/hello/world");
            setTranslationParam("/hello/%two_letters_code%/%file_name%.%file_extension%");
        }};

        PropertiesBuilder pBuilder = new PropertiesBuilder(configFile, null, okParams);
        PropertiesBean pb = pBuilder.build();

        assertEquals(pb.getPreserveHierarchy(), false);
        assertThat(pb.getBasePath(), matchesPattern("([a-zA-Z]:\\\\\\\\|/)"));
        assertEquals(pb.getFiles().size(), 1);
        assertEquals(pb.getFiles().get(0).getSource(), "hello" + Utils.PATH_SEPARATOR + "world");
    }
}
