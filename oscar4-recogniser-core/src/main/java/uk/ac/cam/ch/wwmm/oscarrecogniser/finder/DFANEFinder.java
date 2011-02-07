package uk.ac.cam.ch.wwmm.oscarrecogniser.finder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.cam.ch.wwmm.oscar.chemnamedict.ChemNameDictRegistry;
import uk.ac.cam.ch.wwmm.oscar.document.IToken;
import uk.ac.cam.ch.wwmm.oscar.document.ITokenSequence;
import uk.ac.cam.ch.wwmm.oscar.document.NamedEntity;
import uk.ac.cam.ch.wwmm.oscar.exceptions.ResourceInitialisationException;
import uk.ac.cam.ch.wwmm.oscar.ont.OntologyTermIdIndex;
import uk.ac.cam.ch.wwmm.oscar.terms.TermSets;
import uk.ac.cam.ch.wwmm.oscar.tools.OscarProperties;
import uk.ac.cam.ch.wwmm.oscar.tools.StringTools;
import uk.ac.cam.ch.wwmm.oscar.types.NamedEntityType;
import uk.ac.cam.ch.wwmm.oscarrecogniser.tokenanalysis.NGram;
import uk.ac.cam.ch.wwmm.oscarrecogniser.tokenanalysis.PrefixFinder;
import uk.ac.cam.ch.wwmm.oscartokeniser.TokenClassifier;
import uk.ac.cam.ch.wwmm.oscartokeniser.Tokeniser;

/** A subclass of DFAFinder, used to find named entities.
 *
 * Notes:
 * case-sensitivity:
 *   it appears that lower-case terms can match words in text containing upper-case
 *   characters so long as they also contain consecutive lower-case characters, but if
 *   there are upper-case characters in the term, then they must be upper-case
 *   in the text in order to match.
 *
 * @author ptc24
 *
 */
public class DFANEFinder extends DFAFinder {

    private final Logger logger = LoggerFactory.getLogger(DFANEFinder.class);

    private static final long serialVersionUID = -3307600610608772402L;
    private static DFANEFinder myInstance;

    public static Pattern P_TWO_ADJACENT_LOWERCASE = Pattern.compile("[a-z][a-z]");
    public static Pattern P_UPPERCASE_LETTER = Pattern.compile("[A-Z]");
    
    private static final String REP_CM_NON_WORD = "$CMNONWORD";
    private static final String REP_ONT_WORD = "$ONTWORD";
    private static final String REP_IN_CND = "$INCND";
    private static final String REP_POLY_BRACKET_ = "$polybracket-";
    private static final String REP_POLY_ = "$poly-";
    private static final String REP_OPEN_BRACKET = "$-(-";
    private static final String REP_ENDS_IN_ELEMENT = "$ENDSINEM";
    private static final String REP_ELEMENT = "$EM";
    private static final String REP_PREFIX_BODY = "$PREFIXBODY";
    private static final String REP_HYPH = "$HYPH";
    private static final String REP_DOTS = "$DOTS";
    private static final String REP_STOP = "$STOP";
    private static final String REP_CPR_FORMULA = "$CPR_FORMULA";


    /**Get the DFANEFinder singleton, initialising if necessary.
     *
     * @return The DFANEFinder singleton.
     * @throws ResourceInitialisationException 
     */
    public static DFANEFinder getInstance() throws ResourceInitialisationException {
        if (myInstance == null) {
            /* dictionaries are initialised by ChemNaneDictRegistry constructor
            try {
    			ChemNameDictRegistry.getInstance().register(
    				new DefaultDictionary()
    			);
    			ChemNameDictRegistry.getInstance().register(
        			new ChEBIDictionary()
        		);
    		} catch (Exception exception) {
    			throw new Error(
    				"Could not load default dictionary: " + exception,
    				exception
    			);
    		}
    		*/
            myInstance = new DFANEFinder();
        }
        return myInstance;
    }

    /**Re-initialise the DFANEFinder singleton.
     * @throws ResourceInitialisationException 
     *
     */
    @Deprecated
    //TODO - this isn't called - do we need it?
    public static void reinitialise() throws ResourceInitialisationException {
        myInstance = null;
        getInstance();
    }

    /**Destroy the DFANEFinder singleton.
     *
     */
    public static void destroyInstance() {
        myInstance = null;
    }

    /**Checks to see if a string can be tokenised into multiple tokens; if
     * so, deletes the DFANEFinder singleton.
     *
     * @param word The string to test.
     */
    public static void destroyInstanceIfWordTokenises(String word) {
        if (myInstance == null) return;
        ITokenSequence ts = Tokeniser.getInstance().tokenise(word);
        if (ts.getTokens().size() > 1) myInstance = null;
    }

    private DFANEFinder() throws ResourceInitialisationException {
        logger.debug("Initialising DFA NE Finder...");
        super.init();
        logger.debug("Initialised DFA NE Finder");
    }

    @Override
    protected void loadTerms() throws ResourceInitialisationException {
        TermMaps termMaps = TermMaps.getInstance();
        logger.debug("Adding terms to DFA finder...");
        for(String s : termMaps.getNeTerms().keySet()){
            addNamedEntity(s, termMaps.getNeTerms().get(s), true);
        }
        logger.debug("Adding ontology terms to DFA finder...");
        for(String s : OntologyTermIdIndex.getInstance().getAllTerms()){
            addNamedEntity(s, NamedEntityType.ONTOLOGY, false);
        }
        logger.debug("Adding custom NEs ...");
        for(String s : termMaps.getCustEnt().keySet()){
            addNamedEntity(s, NamedEntityType.CUSTOM, true);
        }
        logger.debug("Adding names from ChemNameDict to DFA finder...");
        try {
            for(String s : ChemNameDictRegistry.getInstance().getAllNames()) {
                addNamedEntity(s, NamedEntityType.COMPOUND, false);
            }
        } catch (Exception e) {
            System.err.println("Couldn't add names from ChemNameDict!");
        }
    }

    //public List<NamedEntity> getNEs(TokenSequence t) {
    //	NECollector nec = new NECollector();
    //	findItems(t, nec);
    //	return nec.getNes();
    //}

    /**Finds the NEs from a token sequence.
     *
     * @param t The token sequence
     * @return The NEs.
     * @throws ResourceInitialisationException 
     */
    public List<NamedEntity> findNamedEntities(ITokenSequence t, NGram nGram) throws ResourceInitialisationException {
        NECollector nec = new NECollector();
        List<RepresentationList> repsList = generateTokenRepresentations(t, nGram);
        findItems(t, repsList, nec);
        return nec.getNes();
    }

    private List<RepresentationList> generateTokenRepresentations(ITokenSequence t, NGram nGram) throws ResourceInitialisationException {
        List<RepresentationList> repsList = new ArrayList<RepresentationList>();
        for(IToken token : t.getTokens()) {
            repsList.add(generateTokenRepresentations(token, nGram));
        }
        return repsList;
    }

    protected RepresentationList generateTokenRepresentations(IToken token, NGram nGram) throws ResourceInitialisationException {
        RepresentationList tokenRepresentations = new RepresentationList();
        // Avoid complications with compound refs
        //SciXML dependent - removed 24/11/10 by dmj30
//		if (TokenTypes.isCompRef(t)) {
//			tokenReps.add("$COMPREF");
//			return tokenReps;
//		}
//		if (TokenTypes.isRef(t)) tokenReps.add("$CITREF");
        String value = token.getValue();
        tokenRepresentations.addRepresentation(value);
        String normalisedValue = StringTools.normaliseName(value);

        if (!normalisedValue.equals(value)) {
            tokenRepresentations.addRepresentation(normalisedValue);
        }
        tokenRepresentations.addRepresentations(getSubReRepsForToken(value));
        if (value.length() == 1) {
            if (StringTools.isHyphen(value)) {
                tokenRepresentations.addRepresentation(REP_HYPH);
            } else if (StringTools.isMidElipsis(value)) {
                tokenRepresentations.addRepresentation(REP_DOTS);
            }
        }
        for (NamedEntityType namedEntityType : TokenClassifier.getInstance().classifyToken(value)) {
            if (!NamedEntityType.PROPERNOUN.equals(namedEntityType)
                    || !(value.matches("[A-Z][a-z]+") && TermSets.getDefaultInstance().getUsrDictWords().contains(value.toLowerCase()) && !TermSets.getDefaultInstance().getUsrDictWords().contains(value))) {
                tokenRepresentations.addRepresentation("$"+ namedEntityType.getName());
            }
        }
        boolean stopWord = false;
        Matcher m = PrefixFinder.prefixPattern.matcher(value);
        if (value.length() >= 2 && m.matches()) {
            String lastGroup = m.group(m.groupCount());
            String lastGroupNorm = StringTools.normaliseName(lastGroup);
            if (lastGroup == null || lastGroup.equals("")) {
                tokenRepresentations.addRepresentation("$" + NamedEntityType.LOCANTPREFIX.getName());
            } else {
                if (isChemicalFormula(lastGroup)) {
                    tokenRepresentations.addRepresentation(REP_CPR_FORMULA);
                }
                if (TermSets.getDefaultInstance().getStopWords().contains(lastGroupNorm) ||
                        TermSets.getDefaultInstance().getClosedClass().contains(lastGroupNorm)) {//||
//						ExtractTrainingData.getInstance().nonChemicalWords.contains(lastGroupNorm) ||
//						ExtractTrainingData.getInstance().nonChemicalNonWords.contains(lastGroupNorm)) {
                    if (!isElement(lastGroupNorm)) {
                        stopWord = true;
                    }
                }
//				boolean isModifiedCompRef = false;
//				for (int i = m.start(m.groupCount())+t.getStart(); i < t.getEnd(); i++) {
//					if (!XMLStrings.getInstance().isCompoundReferenceUnderStyle(t.getDoc().getStandoffTable().getElemAtOffset(i))) {
//						isModifiedCompRef = false;
//						break;
//					}
//				}
//				if (isModifiedCompRef) tokenReps.add("$CPR_COMPREF");
            }
        }


        if (isPrefixBody(value)) {
            tokenRepresentations.addRepresentation(REP_PREFIX_BODY);
        }
        if (isElement(normalisedValue)) {
            tokenRepresentations.addRepresentation(REP_ELEMENT);
        }
        if (isEndingWithElementName(value)) {
            tokenRepresentations.addRepresentation(REP_ENDS_IN_ELEMENT);
        }

        try {
//			if (t.getValue().matches(".*[a-z][a-z].*") && !scoreAsStop && !ExtractTrainingData.getInstance().nonChemicalWords.contains(normValue)) {
            if (!stopWord && value.length() > 3 && value.matches(".*[a-z][a-z].*") ) {
                double score;
//				if (ExtractTrainingData.getInstance().chemicalWords.contains(normValue)) score = 100;
                if (ChemNameDictRegistry.getInstance().hasName(value)) {
                    score = 100;
                }
                else if (TermSets.getDefaultInstance().getUsrDictWords().contains(normalisedValue)
                        || TermSets.getDefaultInstance().getUsrDictWords().contains(value)) {
                    score = -100;
                }
                else {
                    score = nGram.testWord(value);
                }

                if (score > OscarProperties.getData().ngramThreshold) {
                    tokenRepresentations.addRepresentation("$" + uk.ac.cam.ch.wwmm.oscarrecogniser.tokenanalysis.TokenSuffixClassifier.classifyBySuffix(value).getName());
                    if (value.startsWith("-")) {
                        tokenRepresentations.addRepresentation("$-" + uk.ac.cam.ch.wwmm.oscarrecogniser.tokenanalysis.TokenSuffixClassifier.classifyBySuffix(value).getName());
                    }
                    if (value.endsWith("-")) {
                        tokenRepresentations.addRepresentation("$" + uk.ac.cam.ch.wwmm.oscarrecogniser.tokenanalysis.TokenSuffixClassifier.classifyBySuffix(value).getName() + "-");
                    }

                    String withoutLastBracket = value;
                    while(withoutLastBracket.endsWith(")") || withoutLastBracket.endsWith("]")) {
                        withoutLastBracket = withoutLastBracket.substring(0, withoutLastBracket.length()-1);
                    }
                    TermMaps termMaps = TermMaps.getInstance();
                    for (int i = 1; i < withoutLastBracket.length(); i++) {
                        if (termMaps.getSuffixes().contains(withoutLastBracket.substring(i))) {
                            tokenRepresentations.addRepresentation("$-" + withoutLastBracket.substring(i));
                        }
                    }

                    if (value.contains("(") && !value.contains(")")) {
                        tokenRepresentations.addRepresentation(REP_OPEN_BRACKET);
                    }
                    if (value.matches("[Pp]oly.+")) {
                        tokenRepresentations.addRepresentation(REP_POLY_);
                    }
                    if (value.matches("[Pp]oly[\\(\\[\\{].+")) {
                        tokenRepresentations.addRepresentation(REP_POLY_BRACKET_);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ChemNameDictRegistry.getInstance().hasName(value)) {
            tokenRepresentations.addRepresentation(REP_IN_CND);
        }
        if (OntologyTermIdIndex.getInstance().containsTerm(normalisedValue)) {
            tokenRepresentations.addRepresentation(REP_ONT_WORD);
        }
//        if(!TokenTypes.twoLowerPattern.matcher(t.getValue()).find() && TokenTypes.oneCapitalPattern.matcher(t.getValue()).find()) {
//			//System.out.println("Yay!");
//			if(Oscar3Props.getInstance().useWordShapeHeuristic) tokenReps.add("$CMNONWORD");
//			if(ExtractTrainingData.getInstance().chemicalNonWords.contains(t.getValue())) tokenReps.add("$CMNONWORD");
//		}
//      TODO why are these commented out?       
//        if (ExtractedTrainingData.getInstance().chemicalNonWords.contains(value)) {
//            tokenRepresentations.add("$CMNONWORD");
//        } else
        if (OscarProperties.getData().useWordShapeHeuristic) {
            if (!hasTwoAdjacentLowerCaseLetters(value) && hasCapitalLetter(value)) {
                tokenRepresentations.addRepresentation(REP_CM_NON_WORD);
            }
        }
        //SciXML dependent - removed 24/11/10 by dmj30
//		if (t.getDoc() != null) {
//			if (XMLStrings.getInstance().isCompoundReferenceUnderStyle(t.getDoc().getStandoffTable().getElemAtOffset(t.getEnd()-1)) 
//				&& !(XMLStrings.getInstance().isCompoundReferenceUnderStyle(t.getDoc().getStandoffTable().getElemAtOffset(t.getStart())))) {
//				tokenReps.add("$MODIFIEDCOMPREF");
//			}
//			if (!XMLStrings.getInstance().isCompoundReferenceUnderStyle(t.getDoc().getStandoffTable().getElemAtOffset(t.getEnd()-1)) 
//				&& (XMLStrings.getInstance().isCompoundReferenceUnderStyle(t.getDoc().getStandoffTable().getElemAtOffset(t.getStart())))) {
//				tokenReps.add("$MODIFIEDCOMPREF");
//			}			
//		}
        if (TermSets.getDefaultInstance().getStopWords().contains(normalisedValue) ||
                TermSets.getDefaultInstance().getClosedClass().contains(normalisedValue)){// ||
//      TODO why are these commented out?
//				ExtractTrainingData.getInstance().nonChemicalWords.contains(normValue) ||
//				ExtractTrainingData.getInstance().nonChemicalNonWords.contains(normValue)) {
            if (!isElement(normalisedValue)) {
                tokenRepresentations.addRepresentation(REP_STOP);
            }
        }

        return tokenRepresentations;
    }

    private boolean isChemicalFormula(String lastGroup) {
        return TokenClassifier.getInstance().isTokenLevelRegexMatch(lastGroup, "formulaRegex");
    }

    private boolean hasCapitalLetter(String value) {
        return P_UPPERCASE_LETTER.matcher(value).find();
    }

    private boolean hasTwoAdjacentLowerCaseLetters(String value) {
        return P_TWO_ADJACENT_LOWERCASE.matcher(value).find();
    }

    private boolean isEndingWithElementName(String value) {
        return TermSets.getDefaultInstance().getEndingInElementNamePattern().matcher(value).matches();
    }

    private boolean isElement(String normValue) {
        return TermSets.getDefaultInstance().getElements().contains(normValue);
    }

    private boolean isPrefixBody(String s) {
        Matcher m = PrefixFinder.prefixBody.matcher(s);
        return m.matches();
    }

}
