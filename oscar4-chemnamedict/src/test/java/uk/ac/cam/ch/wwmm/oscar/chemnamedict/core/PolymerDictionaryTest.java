package uk.ac.cam.ch.wwmm.oscar.chemnamedict.core;

import org.junit.Assert;
import org.junit.Test;

import uk.ac.cam.ch.wwmm.oscar.chemnamedict.ChemNameDictRegistry;
import uk.ac.cam.ch.wwmm.oscar.chemnamedict.IChemNameDict;

public class PolymerDictionaryTest extends AbstractDictionaryTest {

	@Test
	public void testACompound() throws Exception {
		IChemNameDict dict = new PolymerDictionary();
		Assert.assertNotNull(dict);
		Assert.assertTrue(dict.hasName("HPEI25K"));
	}
	
	@Test
	public void testAcccessViaRegistry() throws Exception {
		IChemNameDict dict = new PolymerDictionary();
		ChemNameDictRegistry registry = ChemNameDictRegistry.getInstance();
		registry.register(dict);
		Assert.assertNotNull(registry);
		Assert.assertTrue(registry.hasName("HPEI25K"));
	}

	@Override
	public IChemNameDict getDictionary() throws Exception {
		return new PolymerDictionary();
	}
	
}
