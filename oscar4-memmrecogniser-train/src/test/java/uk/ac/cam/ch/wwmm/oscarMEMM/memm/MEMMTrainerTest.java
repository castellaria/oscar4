package uk.ac.cam.ch.wwmm.oscarMEMM.memm;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

import org.apache.commons.collections.set.UnmodifiableSet;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import uk.ac.cam.ch.wwmm.oscar.chemnamedict.core.ChemNameDictRegistry;
import uk.ac.cam.ch.wwmm.oscar.document.NamedEntity;
import uk.ac.cam.ch.wwmm.oscar.document.ProcessingDocument;
import uk.ac.cam.ch.wwmm.oscar.document.ProcessingDocumentFactory;
import uk.ac.cam.ch.wwmm.oscar.document.TokenSequence;
import uk.ac.cam.ch.wwmm.oscar.ont.OntologyTerms;
import uk.ac.cam.ch.wwmm.oscar.types.BioType;
import uk.ac.cam.ch.wwmm.oscarMEMM.MEMMRecogniser;
import uk.ac.cam.ch.wwmm.oscarrecogniser.saf.StandoffResolver.ResolutionMode;
import uk.ac.cam.ch.wwmm.oscarrecogniser.tokenanalysis.NGramBuilder;
import uk.ac.cam.ch.wwmm.oscartokeniser.Tokeniser;
import ch.unibe.jexample.Given;
import ch.unibe.jexample.JExample;

@RunWith(JExample.class)
public class MEMMTrainerTest {

	@Test
	public MEMMTrainer testConstructor() {
		MEMMTrainer trainer = new MEMMTrainer(ChemNameDictRegistry.getDefaultInstance());
		assertNotNull(trainer);
		return trainer;
	}

	@Given("testConstructor")
	public String testUntrainedStatus(MEMMTrainer trainer) throws Exception {
		String xml = trainer.getModel().writeModel().toXML();
		assertEquals("<model />", xml);
		return xml;
	}

	@Given("testConstructor,testUntrainedStatus")
	public Element testLearning(MEMMTrainer trainer, String untrainedXML)
			throws Exception {
		List <Document> sourceDocs = new ArrayList<Document>();
		InputStream stream = this
				.getClass()
				.getClassLoader()
				.getResourceAsStream(
					"uk/ac/cam/ch/wwmm/oscarMEMM/memm/paper.xml");
		try {
			sourceDocs.add(new Builder().build(stream));	
		}
		finally {
			IOUtils.closeQuietly(stream);
		}
		trainer.trainOnDocs(sourceDocs);
		Element trainedModel = trainer.getModel().writeModel();
		assertNotSame(untrainedXML, trainedModel.toXML());

		assertEquals("model", trainedModel.getLocalName());
		assertEquals(1, trainedModel.getChildElements("etd").size());
		assertEquals(1, trainedModel.getChildElements("memm").size());

		Element memmModel = trainedModel.getFirstChildElement("memm");
		assertEquals("memm", memmModel.getLocalName());
		Elements elements = memmModel.getChildElements();
		for (int i = 0; i < elements.size(); i++)
			assertEquals("maxent", elements.get(i).getLocalName());

		
		return trainedModel;
	}

	
	@Test
	public void testRecognising() throws Exception {
		List<String> expectedSurfaceList = Arrays.asList("ether", "ether", "ether ketone", "ketone", "ketone", "nitrogen", "nitrogen", "bisphthalazinone", "sulfonated difluoride ketone", "difluoride ketone", "ketone", "potassium", "potassium carbonate", "mmol", "DMSO", "toluene", "Nitrogen", "Nitrogen", "water", "toluene", "DMSO", "methanol", "water", "water", "polymer", "7a");
		List<String> expectedTypeList = Arrays.asList("ONT", "ONT", "CM", "CM", "ONT", "CM", "ONT", "CM", "CM", "CM", "ONT", "ONT", "CM", "CM", "CM", "CM", "CM", "ONT", "CM", "CM", "CM", "CM", "CM", "CM", "ONT", "CM");

		String sentence = "Preparation of Sulfonated Poly(phthalazinone ether ether ketone) 7a. To a 25 mL three-necked round-bottomed flask fitted with a Dean-stark trap, a condenser, a nitrogen inlet/outlet, and magnetic stirrer was added bisphthalazinone monomer 4 (0.6267 g, 1 mmol), sulfonated difluoride ketone 5 (0.4223 g, 1 mmol), anhydrous potassium carbonate (0.1935 g, 1.4 mmol), 5 mL of DMSO, and 6 mL of toluene. Nitrogen was purged through the reaction mixture with stirring for 10 min, and then the mixture was slowly heated to 140 \u00B0C and kept stirring for 2 h. After water generated was azoetroped off with toluene. The temperature was slowly increased to 175 \u00B0C. The temperature was maintained for 20 h, and the viscous solution was cooled to 100 \u00B0C followed by diluting with 2 mL of DMSO and, thereafter, precipitated into 100 mL of 1:  1 (v/v) methanol/water. The precipitates were filtered and washed with water for three times. The fibrous residues were collected and dried at 110 \u00B0C under vacuum for 24 h. A total of 0.9423 g of polymer 7a was obtained in high yield of 93%.";
		List<String> actualSurfaceList = new ArrayList<String>();
		List<String> actualTypeList = new ArrayList<String>();
		MEMMRecogniser memm = new MEMMRecogniser(
				trainModel(), OntologyTerms.getDefaultInstance(),
				new ChemNameDictRegistry(Locale.ENGLISH));
		ProcessingDocument procdoc = ProcessingDocumentFactory.getInstance()
				.makeTokenisedDocument(Tokeniser.getDefaultInstance(), sentence);
		List<NamedEntity> neList = memm.findNamedEntities(procdoc.getTokenSequences(), ResolutionMode.MARK_BLOCKED);
		System.out.println(neList);
		assertEquals("Number of recognised entities: ", 26, neList.size());
		for (NamedEntity namedEntity : neList) {
			actualSurfaceList.add(namedEntity.getSurface());
			actualTypeList.add(namedEntity.getType().getName());
		}
		assertEquals("Chemical Names recognised",expectedSurfaceList,actualSurfaceList);
		assertEquals("Chemical Types recognised",expectedTypeList,actualTypeList);
	}
	
	@Given("testLearning")
	public void testExtractTrainingData(Element trainedModel) throws Exception{
		MEMMModel model = new MEMMModel(trainedModel);
		assertEquals("Number of Chemical words in ExtractedTrainingData size",453, model.getExtractedTrainingData().getChemicalWords().size());
		assertEquals("Number of non-chemical words in ExtractedTrainingData size",1178, model.getExtractedTrainingData().getNonChemicalWords().size());
	}
	
	
	@Test
	/**
	 * To check that the same model is always produced from the same input
	 */
	public void testDeterministicModelProduction() throws Exception {
		Element model1 = trainModel().writeModel();
		Element model2 = trainModel().writeModel();
		assertTrue(model1.toXML().equals(model2.toXML()));
		
		MEMMModel model = new MEMMModel(model1);
		//previously, reading a model has triggered a change in the
		//ExtractedTrainingData causing a different model to be produced

		Element model3 = trainModel().writeModel();
		
		assertTrue(model1.toXML().equals(model2.toXML()));
		assertTrue(model1.toXML().equals(model3.toXML()));
	}
	
	
	@Test
	public void testEventCollection() throws Exception {
		InputStream in = ClassLoader.getSystemResourceAsStream("uk/ac/cam/ch/wwmm/oscarMEMM/memm/eventCollectionTest.xml");
		List <Document> sourceDocs = new ArrayList<Document>();
		try {
			sourceDocs.add(new Builder().build(in));
		}
		finally {
			IOUtils.closeQuietly(in);
		}
		MEMMTrainer trainer = new MEMMTrainer(ChemNameDictRegistry.getDefaultInstance());
		
		trainer.trainOnDocs(sourceDocs);
		assertEquals(6, trainer.evsByPrev.keySet().size());
		assertTrue(trainer.evsByPrev.keySet().contains(BioType.fromString("B-CM")));
		assertFalse(trainer.evsByPrev.keySet().contains(BioType.fromString("I-CM")));
		assertTrue(trainer.evsByPrev.keySet().contains(BioType.fromString("B-RN")));
		assertTrue(trainer.evsByPrev.keySet().contains(BioType.fromString("I-RN")));
		assertTrue(trainer.evsByPrev.keySet().contains(BioType.fromString("B-ASE")));
		assertTrue(trainer.evsByPrev.keySet().contains(BioType.fromString("I-ASE")));
		assertTrue(trainer.evsByPrev.keySet().contains(BioType.fromString("O")));
	}
	
	@Ignore
	@Test
	public void testModelReloading() throws Exception {
		MEMMModel trained = trainModel();
		Element serialised = trained.writeModel();
		MEMMModel reloaded = new MEMMModel();
		reloaded.readModel(serialised);
		reloaded.chemNameDictNames = (UnmodifiableSet) UnmodifiableSet.decorate(
				ChemNameDictRegistry.getDefaultInstance().getAllNames());
		//FIXME fails unless null is passed as etd argument as trained uses the vanilla n-gram model
		reloaded.nGram = NGramBuilder.buildOrDeserialiseModel(reloaded.etd, reloaded.chemNameDictNames);
		
		assertTrue(trained.nGram.compareTo(reloaded.nGram));
		
		//TODO more input texts
		TokenSequence tokSeq = Tokeniser.getDefaultInstance().tokenise("Preparation of Sulfonated Poly(phthalazinone ether ether ketone) 7a. To a 25 mL three-necked round-bottomed flask fitted with a Dean-stark trap, a condenser, a nitrogen inlet/outlet, and magnetic stirrer was added bisphthalazinone monomer 4 (0.6267 g, 1 mmol), sulfonated difluoride ketone 5 (0.4223 g, 1 mmol), anhydrous potassium carbonate (0.1935 g, 1.4 mmol), 5 mL of DMSO, and 6 mL of toluene. Nitrogen was purged through the reaction mixture with stirring for 10 min, and then the mixture was slowly heated to 140 \u00B0C and kept stirring for 2 h. After water generated was azoetroped off with toluene. The temperature was slowly increased to 175 \u00B0C. The temperature was maintained for 20 h, and the viscous solution was cooled to 100 \u00B0C followed by diluting with 2 mL of DMSO and, thereafter, precipitated into 100 mL of 1:  1 (v/v) methanol/water. The precipitates were filtered and washed with water for three times. The fibrous residues were collected and dried at 110 \u00B0C under vacuum for 24 h. A total of 0.9423 g of polymer 7a was obtained in high yield of 93%.");
		List <NamedEntity> trainedNes1 = trained.findNEs(tokSeq, 0.04);
		List <NamedEntity> trainedNes2 = trained.findNEs(tokSeq, 0.04);
		List <NamedEntity> reloadedNes1 = reloaded.findNEs(tokSeq, 0.04);
		List <NamedEntity> reloadedNes2 = reloaded.findNEs(tokSeq, 0.04);
		assertEquals(trainedNes1, trainedNes2);
		assertEquals(reloadedNes1, reloadedNes2);
		assertEquals(trainedNes1, reloadedNes1);
	}
	

	private MEMMModel trainModel() throws Exception {
		MEMMTrainer trainer = new MEMMTrainer(ChemNameDictRegistry.getDefaultInstance());
		List <Document> sourceDocs = new ArrayList<Document>();
		InputStream stream = this
				.getClass()
				.getClassLoader()
				.getResourceAsStream(
					"uk/ac/cam/ch/wwmm/oscarMEMM/memm/paper.xml");
		try {
			sourceDocs.add(new Builder().build(stream));
		} finally {
			IOUtils.closeQuietly(stream);
		}
		trainer.trainOnDocs(sourceDocs);
		return trainer.getModel();
	}
	
}
