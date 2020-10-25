package com.crowdin.cli.commands.actions;

import com.crowdin.cli.client.ClientGlossary;
import com.crowdin.cli.commands.NewAction;
import com.crowdin.cli.commands.Outputter;
import com.crowdin.cli.properties.BaseProperties;
import com.crowdin.cli.properties.NewBasePropertiesUtilBuilder;
import com.crowdin.client.glossaries.model.Glossary;
import com.crowdin.client.glossaries.model.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class GlossaryListActionTest {

    ClientGlossary clientMock;
    BaseProperties pb = NewBasePropertiesUtilBuilder
        .minimalBuilt()
        .build();
    Outputter outputter = Outputter.getDefault();
    NewAction<BaseProperties, ClientGlossary> action;

    @BeforeEach
    public void beforeEach() {
        clientMock = mock(ClientGlossary.class);

        List<Glossary> glossaries = Arrays.asList(
            new Glossary() {{
                    setId(42L);
                    setName("forty-two");
                    setTerms(2);
                }},
            new Glossary() {{
                    setId(43L);
                    setName("forty-three");
                    setTerms(1);
                }}
        );
        List<Term> termsFor42 = Arrays.asList(
            new Term() {{
                    setId(52L);
                    setText("fifty-two");
                    setDescription("How");
                }},
            new Term() {{
                    setId(53L);
                    setText("fifty-three");
                    setDescription("you");
                }}
        );
        List<Term> termsFor43 = Arrays.asList(
            new Term() {{
                    setId(54L);
                    setText("fifty-four");
                    setText("doin'");
                }}
        );
        when(clientMock.listGlossaries())
            .thenReturn(glossaries);
        when(clientMock.listTerms(eq(42L)))
            .thenReturn(termsFor42);
        when(clientMock.listTerms(eq(43L)))
            .thenReturn(termsFor43);
    }

    @Test
    public void test_standard() {
        action = new GlossaryListAction(false, false);
        action.act(outputter, pb, clientMock);

        verify(clientMock).listGlossaries();
        verifyNoMoreInteractions(clientMock);
    }

    @Test
    public void test_plainView() {
        action = new GlossaryListAction(true, false);
        action.act(outputter, pb, clientMock);

        verify(clientMock).listGlossaries();
        verifyNoMoreInteractions(clientMock);
    }

    @Test
    public void test_verbose() {
        action = new GlossaryListAction(false, true);
        action.act(outputter, pb, clientMock);

        verify(clientMock).listGlossaries();
        verify(clientMock).listTerms(eq(42L));
        verify(clientMock).listTerms(eq(43L));
        verifyNoMoreInteractions(clientMock);
    }
}
