package com.opendigitaleducation.explorer.tests;


import static com.opendigitaleducation.explorer.ingest.impl.HtmlAnalyserMessageTransformer.parseHtml;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class HtmlAnalyserMessageTransformerTest {

    @Test
    public void testParseRawText() {
        assertEquals("Coucou", parseHtml("Coucou"));
    }
    @Test
    public void testParseSimpleHTML() {
        assertEquals("What is Lorem Ipsum? Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.",
                parseHtml("<div>\n" +
                "<h2>What is Lorem Ipsum?</h2>\n" +
                "<p><strong>Lorem Ipsum</strong> is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.</p>\n" +
                "</div>\n\n"));
    }
    @Test
    public void testParseComplexHTML() {
        assertEquals("Coucou", parseHtml("<div>Coucou</div>"));
    }
    @Test
    public void testParseBadHtml() {
        assertEquals("Coucou", parseHtml("<div>Coucou"));
    }
}
