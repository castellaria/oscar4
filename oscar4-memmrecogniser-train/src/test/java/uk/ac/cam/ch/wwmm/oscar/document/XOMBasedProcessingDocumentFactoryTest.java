package uk.ac.cam.ch.wwmm.oscar.document;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;

import org.junit.Test;

import uk.ac.cam.ch.wwmm.oscar.types.BioTag;
import uk.ac.cam.ch.wwmm.oscar.types.BioType;
import uk.ac.cam.ch.wwmm.oscar.types.NamedEntityType;
import uk.ac.cam.ch.wwmm.oscartokeniser.Tokeniser;

public class XOMBasedProcessingDocumentFactoryTest {

	@Test
	public void testTokeniseOnAnnotationBoundaries() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/sciXmlPaper.xml");
		Document sourceDoc = new Builder().build(in);
//		"the quick methylbrown fox jumps over thechlorinated dog"
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = (XOMBasedProcessingDocument) XOMBasedProcessingDocumentFactory.getInstance().makeTokenisedDocument(tokeniser, sourceDoc, false, false, false);
		//empty tokenSequence for the empty HEADER in the sourceDoc
		assertEquals(2, procDoc.getTokenSequences().size());
		List <IToken> tokens = procDoc.getTokenSequences().get(1).getTokens(); 
		assertEquals(8, tokens.size());
		assertEquals("the", tokens.get(0).getSurface());
		assertEquals("quick", tokens.get(1).getSurface());
		assertEquals("methylbrown", tokens.get(2).getSurface());
		assertEquals("fox", tokens.get(3).getSurface());
		assertEquals("jumps", tokens.get(4).getSurface());
		assertEquals("over", tokens.get(5).getSurface());
		assertEquals("thechlorinated", tokens.get(6).getSurface());
		assertEquals("dog", tokens.get(7).getSurface());
		
		String sourceString = "the quick methylbrown fox jumps over thechlorinated dog";
		XOMBasedProcessingDocumentFactory.getInstance().tokeniseOnAnnotationBoundaries(tokeniser, sourceString, procDoc, 0, procDoc.getDoc().getRootElement(), tokens);
		
		assertEquals(10, tokens.size());
		assertEquals("the", tokens.get(0).getSurface());
		assertEquals("quick", tokens.get(1).getSurface());
		assertEquals("methyl", tokens.get(2).getSurface());
		assertEquals("brown", tokens.get(3).getSurface());
		assertEquals("fox", tokens.get(4).getSurface());
		assertEquals("jumps", tokens.get(5).getSurface());
		assertEquals("over", tokens.get(6).getSurface());
		assertEquals("the", tokens.get(7).getSurface());
		assertEquals("chlorinated", tokens.get(8).getSurface());
		assertEquals("dog", tokens.get(9).getSurface());
	}
	

	@Test
	public void testMergeNeTokens() {
		String source = "foo ethyl acetate bar";
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		IProcessingDocument procDoc = ProcessingDocumentFactory.getInstance().makeTokenisedDocument(tokeniser, source);
		List <IToken> tokens = procDoc.getTokenSequences().get(0).getTokens();
		assertEquals(4, tokens.size());
		assertEquals("foo", tokens.get(0).getSurface());
		assertEquals("ethyl", tokens.get(1).getSurface());
		assertEquals("acetate", tokens.get(2).getSurface());
		assertEquals("bar", tokens.get(3).getSurface());
		
		//FIXME manually setting the biotag is necessary as this is done by
		//XOMBasedProcessingDocumentFactory.tokeniseOnAnnotationBoundaries
		tokens.get(0).setBioTag(new BioType(BioTag.O));
		tokens.get(1).setBioTag(new BioType(BioTag.B, NamedEntityType.COMPOUND));
		tokens.get(2).setBioTag(new BioType(BioTag.I, NamedEntityType.COMPOUND));
		tokens.get(3).setBioTag(new BioType(BioTag.O));
		XOMBasedProcessingDocumentFactory.getInstance().mergeNeTokens(tokens, source, 0);
		
		assertEquals(3, tokens.size());
		assertEquals("foo", tokens.get(0).getSurface());
		assertEquals("ethyl acetate", tokens.get(1).getSurface());
		assertEquals("bar", tokens.get(2).getSurface());
		
	}
	
	@Test
	public void testTidyHyphensAfterNE() throws Exception{
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/sciXmlPaper.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = (XOMBasedProcessingDocument) XOMBasedProcessingDocumentFactory.getInstance().makeTokenisedDocument(tokeniser, sourceDoc, false, false, false);
		List <IToken> tokens = procDoc.getTokenSequences().get(1).getTokens(); 
		assertEquals(8, tokens.size());
		
		String sourceString = "the quick methylbrown fox jumps over thechlorinated dog";
		XOMBasedProcessingDocumentFactory.getInstance().tokeniseOnAnnotationBoundaries(tokeniser, sourceString, procDoc, 0, procDoc.getDoc().getRootElement(), tokens);
		assertEquals(10, tokens.size());
		
		//produces "the" "quick" "methyl" "brown" "fox" "jumps" "over" "the" "chlorinated" "dog"
		tokens.get(3).setSurface("-brown");
		tokens.get(2).setBioTag(new BioType(BioTag.B, NamedEntityType.COMPOUND));
		//produces "the" "quick" "methyl" "-brown" "fox" "jumps" "over" "the" "chlorinated" "dog"
		
		XOMBasedProcessingDocumentFactory.getInstance().tidyHyphensAfterNEs(tokeniser, tokens);
		assertEquals(11, tokens.size());
		assertEquals("the", tokens.get(0).getSurface());
		assertEquals("quick", tokens.get(1).getSurface());
		assertEquals("methyl", tokens.get(2).getSurface());
		assertEquals("-", tokens.get(3).getSurface());
		assertEquals("brown", tokens.get(4).getSurface());
		assertEquals("fox", tokens.get(5).getSurface());
		assertEquals("jumps", tokens.get(6).getSurface());
		assertEquals("over", tokens.get(7).getSurface());
		assertEquals("the", tokens.get(8).getSurface());
		assertEquals("chlorinated", tokens.get(9).getSurface());
		assertEquals("dog", tokens.get(10).getSurface());
	}
	
	
	@Test
	public void testMakeTokenSequenceStandard() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeDocument(sourceDoc);
		Element para = (Element) procDoc.getDoc().query("//P").get(0);
//		String text = "the quick methylbrown ethyl acetate jumps over thechlorinated dog";
		String text = para.getValue();
		int offset = Integer.parseInt(para.getAttributeValue("xtspanstart"));
		
		ITokenSequence tokSeq = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, false, false, null, procDoc, para, text, offset);
		assertEquals(9, tokSeq.getTokens().size());
		assertEquals("the", tokSeq.getTokens().get(0).getSurface());
		assertEquals("quick", tokSeq.getTokens().get(1).getSurface());
		assertEquals("methylbrown", tokSeq.getTokens().get(2).getSurface());
		assertEquals("ethyl", tokSeq.getTokens().get(3).getSurface());
		assertEquals("acetate", tokSeq.getTokens().get(4).getSurface());
		assertEquals("jumps", tokSeq.getTokens().get(5).getSurface());
		assertEquals("over", tokSeq.getTokens().get(6).getSurface());
		assertEquals("thechlorinated", tokSeq.getTokens().get(7).getSurface());
		assertEquals("dog", tokSeq.getTokens().get(8).getSurface());
	}
	
	@Test
	public void testMakeTokenSequenceTokeniseForNes() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeDocument(sourceDoc);
		Element e = (Element) procDoc.getDoc().getRootElement();
		Element para = (Element) procDoc.getDoc().query("//P").get(0); 
		String text = para.getValue();
		int offset = Integer.parseInt(para.getAttributeValue("xtspanstart"));
		
		ITokenSequence tokSeq = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, false, null, procDoc, e, text, offset);
		assertEquals(11, tokSeq.getTokens().size());
		assertEquals("the", tokSeq.getTokens().get(0).getSurface());
		assertEquals("quick", tokSeq.getTokens().get(1).getSurface());
		assertEquals("methyl", tokSeq.getTokens().get(2).getSurface());
		assertEquals("brown", tokSeq.getTokens().get(3).getSurface());
		assertEquals("ethyl", tokSeq.getTokens().get(4).getSurface());
		assertEquals("acetate", tokSeq.getTokens().get(5).getSurface());
		assertEquals("jumps", tokSeq.getTokens().get(6).getSurface());
		assertEquals("over", tokSeq.getTokens().get(7).getSurface());
		assertEquals("the", tokSeq.getTokens().get(8).getSurface());
		assertEquals("chlorinated", tokSeq.getTokens().get(9).getSurface());
		assertEquals("dog", tokSeq.getTokens().get(10).getSurface());
	}
	
	@Test
	public void testMakeTokenSequenceTokeniseForAndMergeNes() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeDocument(sourceDoc);
		Element e = (Element) procDoc.getDoc().getRootElement();
		Element para = (Element) procDoc.getDoc().query("//P").get(0); 
		String text = para.getValue();
		int offset = Integer.parseInt(para.getAttributeValue("xtspanstart"));
		
		ITokenSequence tokSeq = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, true, null, procDoc, e, text, offset);
		assertEquals(10, tokSeq.getTokens().size());
		assertEquals("the", tokSeq.getTokens().get(0).getSurface());
		assertEquals("quick", tokSeq.getTokens().get(1).getSurface());
		assertEquals("methyl", tokSeq.getTokens().get(2).getSurface());
		assertEquals("brown", tokSeq.getTokens().get(3).getSurface());
		assertEquals("ethyl acetate", tokSeq.getTokens().get(4).getSurface());
		assertEquals("jumps", tokSeq.getTokens().get(5).getSurface());
		assertEquals("over", tokSeq.getTokens().get(6).getSurface());
		assertEquals("the", tokSeq.getTokens().get(7).getSurface());
		assertEquals("chlorinated", tokSeq.getTokens().get(8).getSurface());
		assertEquals("dog", tokSeq.getTokens().get(9).getSurface());
	}
	
	@Test
	public void testSettingBioTagsTokenSequences() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeDocument(sourceDoc);
		Element e = (Element) procDoc.getDoc().getRootElement();
		Element para = (Element) procDoc.getDoc().query("//P").get(0); 
		String text = para.getValue();
		int offset = Integer.parseInt(para.getAttributeValue("xtspanstart"));
		
		ITokenSequence tokSeq = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, true, null, procDoc, e, text, offset);
		assertEquals(10, tokSeq.getTokens().size());
		assertEquals("the", tokSeq.getTokens().get(0).getSurface());
		assertEquals("quick", tokSeq.getTokens().get(1).getSurface());
		assertEquals("methyl", tokSeq.getTokens().get(2).getSurface());
		assertEquals("brown", tokSeq.getTokens().get(3).getSurface());
		assertEquals("ethyl acetate", tokSeq.getTokens().get(4).getSurface());
		assertEquals("jumps", tokSeq.getTokens().get(5).getSurface());
		assertEquals("over", tokSeq.getTokens().get(6).getSurface());
		assertEquals("the", tokSeq.getTokens().get(7).getSurface());
		assertEquals("chlorinated", tokSeq.getTokens().get(8).getSurface());
		assertEquals("dog", tokSeq.getTokens().get(9).getSurface());
			
	
		assertEquals(NamedEntityType.COMPOUND, tokSeq.getTokens().get(2).getBioTag().getType());
		assertEquals(NamedEntityType.COMPOUND, tokSeq.getTokens().get(4).getBioTag().getType());
		assertEquals(NamedEntityType.REACTION, tokSeq.getTokens().get(8).getBioTag().getType());
	}
	
	@Test
	public void testSettingBioTagsXOMDoc() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();

		IXOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeTokenisedDocument(tokeniser, sourceDoc, true, true, false);
		ITokenSequence tokSeq = procDoc.getTokenSequences().get(1);

		
		assertEquals(10, tokSeq.getTokens().size());
		assertEquals("the", tokSeq.getTokens().get(0).getSurface());
		assertEquals("quick", tokSeq.getTokens().get(1).getSurface());
		assertEquals("methyl", tokSeq.getTokens().get(2).getSurface());
		assertEquals("brown", tokSeq.getTokens().get(3).getSurface());
		assertEquals("ethyl acetate", tokSeq.getTokens().get(4).getSurface());
		assertEquals("jumps", tokSeq.getTokens().get(5).getSurface());
		assertEquals("over", tokSeq.getTokens().get(6).getSurface());
		assertEquals("the", tokSeq.getTokens().get(7).getSurface());
		assertEquals("chlorinated", tokSeq.getTokens().get(8).getSurface());
		assertEquals("dog", tokSeq.getTokens().get(9).getSurface());
			
	
		assertEquals(NamedEntityType.COMPOUND, tokSeq.getTokens().get(2).getBioTag().getType());
		assertEquals(NamedEntityType.COMPOUND, tokSeq.getTokens().get(4).getBioTag().getType());
		assertEquals(NamedEntityType.REACTION, tokSeq.getTokens().get(8).getBioTag().getType());
	}
	
	
	@Test
	public void testTokenIds() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeDocument(sourceDoc);
		Element e = (Element) procDoc.getDoc().getRootElement();
		Element para = (Element) procDoc.getDoc().query("//P").get(0); 
		String text = para.getValue();
		int offset = Integer.parseInt(para.getAttributeValue("xtspanstart"));
		
		ITokenSequence tokSeq = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, false, false, null, procDoc, e, text, offset);
		assertEquals(9, tokSeq.getTokens().size());
		for (int i = 0; i < tokSeq.size(); i++) {
			assertEquals(i, tokSeq.getTokens().get(i).getId());
		}
		
		tokSeq = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, false, null, procDoc, e, text, offset);
		assertEquals(11, tokSeq.getTokens().size());
		for (int i = 0; i < tokSeq.size(); i++) {
			assertEquals(i, tokSeq.getTokens().get(i).getId());
		}
		
		tokSeq = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, true, null, procDoc, e, text, offset);
		assertEquals(10, tokSeq.getTokens().size());
		for (int i = 0; i < tokSeq.size(); i++) {
			assertEquals(i, tokSeq.getTokens().get(i).getId());
		}
	}
	
	@Test
	public void testTokenIndex() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeDocument(sourceDoc);
		//fields aren't initialised by above call
		procDoc.tokensByStart = new HashMap<Integer,IToken>();
		procDoc.tokensByEnd = new HashMap<Integer,IToken>();
		Element e = (Element) procDoc.getDoc().getRootElement();
		Element para = (Element) procDoc.getDoc().query("//P").get(0); 
		String text = para.getValue();
		int offset = Integer.parseInt(para.getAttributeValue("xtspanstart"));
		
		List <IToken> tokens = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, false, false, null, procDoc, e, text, offset).getTokens();
		assertEquals(9, procDoc.getTokensByStart().size());
		assertEquals(9, procDoc.getTokensByEnd().size());
		for (IToken iToken : tokens) {
			assertNotNull(iToken);
			assertTrue(iToken == procDoc.getTokensByStart().get(iToken.getStart()));
			assertTrue(iToken == procDoc.getTokensByEnd().get(iToken.getEnd()));
		}
		
		tokens = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, false, null, procDoc, e, text, offset).getTokens();
		assertEquals(11, procDoc.getTokensByStart().size());
		assertEquals(11, procDoc.getTokensByEnd().size());
		for (IToken iToken : tokens) {
			assertNotNull(iToken);
			assertTrue(iToken == procDoc.getTokensByStart().get(iToken.getStart()));
			assertTrue(iToken == procDoc.getTokensByEnd().get(iToken.getEnd()));
		}
		
		tokens = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, true, null, procDoc, e, text, offset).getTokens();
		assertEquals(10, procDoc.getTokensByStart().size());
		assertEquals(10, procDoc.getTokensByEnd().size());
		for (IToken iToken : tokens) {
			assertNotNull(iToken);
			assertTrue(iToken == procDoc.getTokensByStart().get(iToken.getStart()));
			assertTrue(iToken == procDoc.getTokensByEnd().get(iToken.getEnd()));
		}
	}
	
	@Test
	public void testTokenSequenceAssignments() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscar/document/learningSpecificTokenisation.xml");
		Document sourceDoc = new Builder().build(in);
		Tokeniser tokeniser = Tokeniser.getDefaultInstance();
		XOMBasedProcessingDocument procDoc = XOMBasedProcessingDocumentFactory.getInstance().makeDocument(sourceDoc);
		//FIXME fields aren't initialised by above call... yet
		procDoc.tokensByStart = new HashMap<Integer,IToken>();
		procDoc.tokensByEnd = new HashMap<Integer,IToken>();
		Element e = (Element) procDoc.getDoc().getRootElement();
		Element para = (Element) procDoc.getDoc().query("//P").get(0); 
		String text = para.getValue();
		int offset = Integer.parseInt(para.getAttributeValue("xtspanstart"));
		
		ITokenSequence ts1 = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, false, false, null, procDoc, e, text, offset);
		for (IToken token : ts1.getTokens()) {
			assertTrue(ts1 == token.getTokenSequence());
		}
		assertTrue(e == ((TokenSequence)ts1).getElem());
		
		ITokenSequence ts2 = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, false, null, procDoc, e, text, offset);
		assertNotSame(ts2, ts1);
		for (IToken token : ts2.getTokens()) {
			assertTrue(ts2 == token.getTokenSequence());
		}
		assertTrue(e == ((TokenSequence)ts2).getElem());
		
		ITokenSequence ts3 = XOMBasedProcessingDocumentFactory.getInstance().makeTokenSequence(tokeniser, true, true, null, procDoc, e, text, offset);
		assertNotSame(ts3, ts2);
		assertNotSame(ts3, ts1);
		for (IToken token : ts3.getTokens()) {
			assertTrue(ts3 == token.getTokenSequence());
		}
		assertTrue(e == ((TokenSequence)ts3).getElem());
	}
	
}
