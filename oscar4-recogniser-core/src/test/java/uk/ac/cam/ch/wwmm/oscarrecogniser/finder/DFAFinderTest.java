package uk.ac.cam.ch.wwmm.oscarrecogniser.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.ListMultimap;

import uk.ac.cam.ch.wwmm.oscar.document.IToken;
import uk.ac.cam.ch.wwmm.oscar.document.ITokenSequence;
import uk.ac.cam.ch.wwmm.oscar.document.NamedEntity;
import uk.ac.cam.ch.wwmm.oscar.ont.OntologyTerms;
import uk.ac.cam.ch.wwmm.oscar.tools.StringTools;
import uk.ac.cam.ch.wwmm.oscar.types.NamedEntityType;
import uk.ac.cam.ch.wwmm.oscarrecogniser.saf.StandoffResolver;
import uk.ac.cam.ch.wwmm.oscartokeniser.Tokeniser;

/**
 * @author Sam Adams
 * @author dmj30
 */
public class DFAFinderTest {

    @Test
    public void testFindSimpleTermsLowerCase()  {
        NamedEntityType ANIMAL = NamedEntityType.valueOf("ANIMAL");
        Map<String,NamedEntityType> terms = new HashMap<String, NamedEntityType>();
        terms.put("fox", ANIMAL);
        terms.put("dog", ANIMAL);
        Finder finder = new Finder(terms);
        String s = "The quick brown fox jumps over the lazy dog.";
        List<NamedEntity> neList = finder.findNamedEntities(s);
        assertEquals(2, neList.size());
        assertTrue(neList.contains(new NamedEntity("fox", 16, 19, ANIMAL)));
        assertTrue(neList.contains(new NamedEntity("dog", 40, 43, ANIMAL)));
    }

    @Test
    public void testFindSimpleTermsCapitalised() {
        // lower-case terms, upper-case in text
        NamedEntityType ANIMAL = NamedEntityType.valueOf("ANIMAL");
        Map<String,NamedEntityType> terms = new HashMap<String, NamedEntityType>();
        terms.put("fox", ANIMAL);
        terms.put("dog", ANIMAL);
        Finder finder = new Finder(terms);
        String s = "The quick brown Fox jumps over the lazy Dog.";
        List<NamedEntity> neList = finder.findNamedEntities(s);
        assertEquals(2, neList.size());
        assertTrue(neList.contains(new NamedEntity("Fox", 16, 19, ANIMAL)));
        assertTrue(neList.contains(new NamedEntity("Dog", 40, 43, ANIMAL)));
    }

    @Test
    public void testFindSimpleTermsMixedCase() {
        NamedEntityType ANIMAL = NamedEntityType.valueOf("ANIMAL");
        Map<String,NamedEntityType> terms = new HashMap<String, NamedEntityType>();
        terms.put("fox", ANIMAL);
        terms.put("dog", ANIMAL);
        Finder finder = new Finder(terms);
        String s = "The quick brown FOX jumps over the lazy doG.";
        List<NamedEntity> neList = finder.findNamedEntities(s);
        assertEquals(1, neList.size());
//        assertTrue(neList.contains(new NamedEntity("FOX", 16, 19, ANIMAL)));
        assertTrue(neList.contains(new NamedEntity("doG", 40, 43, ANIMAL)));
    }


    @Test
    public void testFindSimpleTermsDifferentCase() {
        // upper-case terms, lower-case in text
        NamedEntityType ANIMAL = NamedEntityType.valueOf("ANIMAL");
        Map<String,NamedEntityType> terms = new HashMap<String, NamedEntityType>();
        terms.put("Fox", ANIMAL);
        terms.put("Dog", ANIMAL);
        Finder finder = new Finder(terms);
        String s = "The quick brown fox jumps over the lazy dog.";
        List<NamedEntity> neList = finder.findNamedEntities(s);
        assertEquals(0, neList.size());
    }


    @Test
    public void testFindMultiTokenTerm() {
        NamedEntityType ANIMAL = NamedEntityType.valueOf("ANIMAL");
        Map<String,NamedEntityType> terms = new HashMap<String, NamedEntityType>();
        terms.put("brown fox", ANIMAL);
        terms.put("red fox", ANIMAL);
        terms.put("fox", ANIMAL);
        Finder finder = new Finder(terms);
        String s = "The quick brown fox jumps over the lazy Dog.";
        List<NamedEntity> neList = finder.findNamedEntities(s);
        assertEquals(2, neList.size());
        assertTrue(neList.contains(new NamedEntity("brown fox", 10, 19, ANIMAL)));
        assertTrue(neList.contains(new NamedEntity("fox", 16, 19, ANIMAL)));
    }

    @Test
    public void testFindHyphenatedTerm() {
        NamedEntityType ANIMAL = NamedEntityType.valueOf("ANIMAL");
        Map<String,NamedEntityType> terms = new HashMap<String, NamedEntityType>();
        terms.put("brown - fox", ANIMAL);
        Finder finder = new Finder(terms);
        String s = "The quick brown - fox jumps over the lazy Dog.";
        List<NamedEntity> neList = finder.findNamedEntities(s);
        assertEquals(1, neList.size());
        assertTrue(neList.contains(new NamedEntity("brown - fox", 10, 21, ANIMAL)));
    }

    @Test
    @Ignore
    // TODO - DFAFinder does not generate representations for term tokens in the same way that it does for input text
    public void testFindHyphenatedTermMatchEndash() {
        NamedEntityType ANIMAL = NamedEntityType.valueOf("ANIMAL");
        Map<String,NamedEntityType> terms = new HashMap<String, NamedEntityType>();
        terms.put("brown - fox", ANIMAL);
        Finder finder = new Finder(terms);
        String s = "The quick brown \u2013 fox jumps over the lazy Dog.";
        List<NamedEntity> neList = finder.findNamedEntities(s);
        assertEquals(1, neList.size());
        assertTrue(neList.contains(new NamedEntity("brown \u2013 fox", 10, 21, ANIMAL)));
    }

    @Test
    public void testChebiTerms() throws Exception {
        ListMultimap<String,String> ontology = OntologyTerms.getDefaultInstance().getOntology();
        NamedEntityType ONT = NamedEntityType.valueOf("ONT");
        boolean fail = false;
        for (String term : ontology.keySet()) {
            Finder finder = new Finder(Collections.singletonMap(term, ONT));
            String s = "I know that "+term+" is a chemical entity!";
            List<NamedEntity> neList = finder.findNamedEntities(s);
            List<NamedEntity> resolved = StandoffResolver.resolveStandoffs(neList);
            if (resolved.size() != 1) {
                System.err.println(resolved.size()+"\t"+term);
                for (NamedEntity ne : resolved) {
                    System.err.println("    "+ne.getStart()+"-"+ne.getEnd()+" "+ne.getSurface());
                }
                fail = true;
            }
            assertEquals(term, resolved.get(0).getSurface());
        }
        assertFalse(fail);
    }


    static class Finder extends DFAFinder {

        private Map<String, NamedEntityType> terms;

        Finder(Map<String,NamedEntityType> terms) {
            this.terms = terms;
            init();
        }

        @Override
        protected void loadTerms() {
            for (Map.Entry<String, NamedEntityType> term : terms.entrySet()) {
                addNamedEntity(term.getKey(), term.getValue(), true);
            }
        }

        public List<NamedEntity> findNamedEntities(String s) {
            ITokenSequence t = Tokeniser.getDefaultInstance().tokenise(s);
            NECollector nec = new NECollector();
            List<RepresentationList> repsList = generateTokenRepresentations(t);
            findItems(t, repsList, nec);
            return nec.getNes();
        }

        private List<RepresentationList> generateTokenRepresentations(ITokenSequence t) {
            List<RepresentationList> repsList = new ArrayList<RepresentationList>();
            for(IToken token : t.getTokens()) {
                repsList.add(generateTokenRepresentations(token));
            }
            return repsList;
        }

        protected RepresentationList generateTokenRepresentations(IToken token) {
            RepresentationList tokenRepresentations = new RepresentationList();
            String value = token.getSurface();
            tokenRepresentations.addRepresentation(value);
            String normalisedValue = StringTools.normaliseName(value);
            if (!normalisedValue.equals(value)) {
                tokenRepresentations.addRepresentation(normalisedValue);
            }
            if (value.length() == 1) {
                if (StringTools.isHyphen(value)) {
                    tokenRepresentations.addRepresentation("$HYPH");
                } else if (StringTools.isMidElipsis(value)) {
                    tokenRepresentations.addRepresentation("$DOTS");
                }
            }
//            System.err.println(tokenRepresentations.toList());
            return tokenRepresentations;
        }

    }

}
