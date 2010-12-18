package uk.ac.cam.ch.wwmm.oscar.obo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author Sam Adams
 */
public class OntologyTermsTest {

    @Test
    public void testOntologyNotNull() {
        OntologyTerms ontologyTerms = OntologyTerms.getDefaultInstance();
        assertNotNull(ontologyTerms.getOntology());
    }

    @Test
    public void testOntologyNotEmpty() {
        OntologyTerms ontologyTerms = OntologyTerms.getDefaultInstance();
        assertFalse(ontologyTerms.getOntology().isEmpty());
    }

}