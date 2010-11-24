package uk.ac.cam.ch.wwmm.oscarrecogniser.finder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import uk.ac.cam.ch.wwmm.oscar.document.ITokenSequence;
import uk.ac.cam.ch.wwmm.oscar.document.NamedEntity;
import uk.ac.cam.ch.wwmm.oscar.document.Token;
import uk.ac.cam.ch.wwmm.oscar.obo.OntologyTerms;
import uk.ac.cam.ch.wwmm.oscar.obo.TermMaps;
import uk.ac.cam.ch.wwmm.oscar.tools.RegExUtils;
import uk.ac.cam.ch.wwmm.oscar.tools.StringTools;
import uk.ac.cam.ch.wwmm.oscar.types.NamedEntityTypes;
import uk.ac.cam.ch.wwmm.oscarrecogniser.tokenanalysis.PrefixFinder;
import uk.ac.cam.ch.wwmm.oscartokeniser.Tokeniser;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;

/** A DFA-based method for finding multi-token items in text.
 * 
 * Note that there are several sub-classes of this class; however, Oscar3 users
 * should not subclass this.
 * 
 * @author ptc24
 *
 */
public abstract class DFAFinder implements Serializable {
	
	/* @dmj30
	 * 
	 * Logging has been disabled as Logger does not implement serialisable
	 * and prevents the serialisation of DFAONTCPRFinder.
	 */
//	private final Logger logger = Logger.getLogger(DFAFinder.class);

	private static final long serialVersionUID = 6130629462990087075L;
	protected Map<String, List<Automaton>> autLists = new HashMap<String, List<Automaton>>();//mapping between token type and automata. Possibly these automata are only used when performing Pattern based entity recognition
	protected Map<String, SuffixTree> simpleAuts = new HashMap<String, SuffixTree>();
	protected Map<String, RunAutomaton> runAuts = new HashMap<String, RunAutomaton>();
	protected final Map<String,String> tokenToRep = new HashMap<String,String>();//mapping between token strings and a unique representation code, usually an integer
	protected Set<String> literals = new HashSet<String>();
	protected int tokenId = 0;
	
//	protected Map<String,Integer> dfaNumber = new HashMap<String,Integer>();//keeps track of the number of automata that have been generated for a certain token
//	protected Map<String,Integer> dfaCount = new HashMap<String,Integer>();//keeps track of the number of tokens of a certain token type encountered
	
	protected Map<String,Integer> ontIdToIntId = new HashMap<String,Integer>();
	protected List<String> ontIds = new ArrayList<String>();
	protected Map<String,Map<Integer,Set<String>>> runAutToStateToOntIds;
	
	protected Map<String,Pattern> subRes = new HashMap<String,Pattern>();
	
	protected final static Pattern matchSubRe = Pattern.compile("\\$\\{.*\\}");//dl387: I'm not sure what this is actually supposed to be matching!
	protected final static Pattern digitOrSpace = Pattern.compile("[0-9 ]+");

	protected DFAFinder() {}

	protected abstract void loadTerms();
	
	protected void init() {
        initLiterals();
		loadTerms();
		finishInit();
//		logger.debug("Finished initialising DFA Finder");
	}

    private void initLiterals() {
        literals.add("$(");
        literals.add("$)");
        literals.add("$+");
        literals.add("$*");
        literals.add("$|");
        literals.add("$?");
        literals.add("$^");
    }

    private String generateTokenRepresentation(String token) {
		//TODO canonicalise token
		if (isLiteral(token)) {
			return token.substring(1);
		} else if (tokenToRep.containsKey(token)) {
			return tokenToRep.get(token);
		} else {
			String representation = Integer.toString(++tokenId) + " ";
			tokenToRep.put(token, representation);
			if (isSubRe(token)) {
				subRes.put(token, Pattern.compile(token.substring(2, token.length()-1)));
			}
			return representation;
		}
	}

    private boolean isSubRe(String token) {
        return matchSubRe.matcher(token).matches();
    }

    private boolean isLiteral(String token) {
        return literals.contains(token);
    }

    protected Set<String> getSubReRepsForToken(String token) {
		Set<String> representations = new HashSet<String>();
		for(String regex : subRes.keySet()) {
			if (subRes.get(regex).matcher(token).matches()) {
				representations.add(regex);
			}
		}
		return representations;
	}
	
	protected String getCachedTokenRepresentation(String token) {
		//TODO canonicalise token
        if (tokenToRep.containsKey(token)) {
			return tokenToRep.get(token);
		} else {
			return null;
		}
	}
	
	protected void addNamedEntity(String namedEntity, String namedEntityType, boolean alwaysAdd) {
//		if (OscarProperties.getData().dfaSize > 0) {
//			if (!dfaCount.containsKey(namedEntityType)) {
//				dfaCount.put(namedEntityType, 0);
//				dfaNumber.put(namedEntityType, 1);
//			}
//			int count = dfaCount.get(namedEntityType) + 1;
//			if (count > OscarProperties.getData().dfaSize) {
//				count = 0;
//				String tmpType = namedEntityType + "_" + dfaNumber.get(namedEntityType);
//				buildForType(tmpType);
//				dfaNumber.put(namedEntityType, dfaNumber.get(namedEntityType) + 1);
//			}
//			dfaCount.put(namedEntityType, count);
//			namedEntityType = namedEntityType + "_" + dfaNumber.get(namedEntityType);
//		}

		ITokenSequence tokenSequence = Tokeniser.getInstance().tokenise(namedEntity);
		List<String> tokens = tokenSequence.getTokenStringList();

		if (!alwaysAdd && tokens.size() == 1 && !namedEntity.contains("$")) {
            return;
        }
		StringBuffer sb = new StringBuffer();
		for(String token : tokens) {
			sb.append(generateTokenRepresentation(token));
		}
		String preReStr = sb.toString();
		for(String reStr : StringTools.expandRegex(preReStr)) {
			if (digitOrSpace.matcher(reStr).matches()) {
				if (simpleAuts.containsKey(namedEntityType)) {
					simpleAuts.get(namedEntityType).addContents(reStr);
				} else {
					simpleAuts.put(namedEntityType, new SuffixTree(reStr));
				}
				if (isOntologyTerm(namedEntity, namedEntityType)) {
					String ontologyIdString = OntologyTerms.idsForTerm(namedEntity);
					List<String> ontologyIds = Arrays.asList(RegExUtils.P_WHITESPACE.split(ontologyIdString));
					for(String ontologyId : ontologyIds) {
						simpleAuts.get(namedEntityType).addContents(reStr + "X" + getNumberForOntologyId(ontologyId));
					}
				} else if (isCustomTerm(namedEntity, namedEntityType)) {
					String customTypeString = TermMaps.getCustEnt().get(namedEntity);
					List<String> customTypes = Arrays.asList(RegExUtils.P_WHITESPACE.split(customTypeString));
					for(String customType : customTypes) {
						simpleAuts.get(namedEntityType).addContents(reStr + "X" + getNumberForOntologyId(customType));
					}
				}
			} else {
				if (isOntologyTerm(namedEntity, namedEntityType)) {
					String ontologyIdString = OntologyTerms.idsForTerm(namedEntity);
					List<String> ontologyIds = Arrays.asList(RegExUtils.P_WHITESPACE.split(ontologyIdString));
					sb.append("(X(");
					for (Iterator<String> it = ontologyIds.iterator(); it.hasNext();) {
                        String ontologyId = it.next();
                        sb.append(Integer.toString(getNumberForOntologyId(ontologyId)));
                        if (it.hasNext()) {
                            sb.append('|');
                        }
                    }
					sb.append("))?");
				} else if (isCustomTerm(namedEntity, namedEntityType)) {
					String customTypeString = TermMaps.getCustEnt().get(namedEntity);
					List<String> customTypes = Arrays.asList(RegExUtils.P_WHITESPACE.split(customTypeString));
					sb.append("(X(");
					for (Iterator<String> it = customTypes.iterator(); it.hasNext();) {
                        String customType = it.next();
                        sb.append(Integer.toString(getNumberForOntologyId(customType)));
                        if (it.hasNext()) {
                            sb.append('|');
                        }
                    }
					sb.append("))?");
				}
				Automaton subAut = new RegExp(sb.toString()).toAutomaton();
                getAutomatonList(namedEntityType).add(subAut);
			}
		}
	}

    private boolean isCustomTerm(String namedEntity, String namedEntityType) {
        return namedEntityType.startsWith(NamedEntityTypes.CUSTOM) && TermMaps.getCustEnt().containsKey(namedEntity);
    }

    private boolean isOntologyTerm(String namedEntity, String namedEntityType) {
        return namedEntityType.startsWith(NamedEntityTypes.ONTOLOGY) && OntologyTerms.hasTerm(namedEntity);
    }

    private List<Automaton> getAutomatonList(String namedEntityType) {
        if (!autLists.containsKey(namedEntityType)) {
            autLists.put(namedEntityType, new ArrayList<Automaton>());
        }
        return autLists.get(namedEntityType);        
    }

    private int getNumberForOntologyId(String ontId) {
		if (ontIdToIntId.containsKey(ontId)) {
			return ontIdToIntId.get(ontId);
		} else {
			int intId = ontIds.size();
			ontIds.add(ontId);
			ontIdToIntId.put(ontId, intId);
			return intId;
		}
	}
	
//	private void buildForType(String type) {
//		if (autLists.containsKey(type)) {
//			Automaton mainAut = Automaton.union(autLists.get(type));
//			mainAut.determinize();
//			runAuts.put(type, new RunAutomaton(mainAut, false));
//			autLists.remove(type);
//		}
//		if (simpleAuts.containsKey(type)) {
//			Automaton mainAut = simpleAuts.get(type).toAutomaton();
//			runAuts.put(type + "b", new RunAutomaton(mainAut, false));
//			simpleAuts.remove(type);
//		}
//	}
	
	private void finishInit() {
		for(String type : new HashSet<String>(autLists.keySet())) {
			Automaton mainAut = Automaton.union(autLists.get(type));
			mainAut.determinize();
			runAuts.put(type, new RunAutomaton(mainAut, false));
			autLists.remove(type);
		}
		for(String type : new HashSet<String>(simpleAuts.keySet())) {
			Automaton mainAut = simpleAuts.get(type).toAutomaton();
			runAuts.put(type + "b", new RunAutomaton(mainAut, false));
			simpleAuts.remove(type);
		}
		runAutToStateToOntIds = new HashMap<String,Map<Integer,Set<String>>>();
		for (String type : runAuts.keySet()) {
			if (type.startsWith(NamedEntityTypes.ONTOLOGY) || type.startsWith(NamedEntityTypes.CUSTOM)) {
			    runAutToStateToOntIds.put(type, analyseAutomaton(runAuts.get(type), 'X'));
            }
		}
	}
	
	private Set<String> readOffTags(RunAutomaton runAut, int state) {
		Set<String> tags = new HashSet<String>();
		readOffTags(runAut, state, "", tags);
		return tags;
	}
	
	private void readOffTags(RunAutomaton runAut, int state, String startOfTag, Set<String> tagsFound) {
		if (runAut.isAccept(state)) {
			String ontId = ontIds.get(Integer.parseInt(startOfTag));
			tagsFound.add(ontId);
		}
		for(int i = 0; i < 10; i++) {
			char c = Integer.toString(i).charAt(0);
			int newState = runAut.step(state, c);
			if (newState != -1) {
				readOffTags(runAut, newState, startOfTag + i, tagsFound);
			}
		}
	}
	
	private Map<Integer,Set<String>> analyseAutomaton(RunAutomaton runAut, char tagChar) {
		Map<Integer,Set<String>> tagMap = new HashMap<Integer,Set<String>>();
		for(int i=0;i<runAut.getSize();i++) {
			if (runAut.isAccept(i) && runAut.step(i, tagChar) != -1) {
				//System.out.println(i);
				tagMap.put(i, readOffTags(runAut, runAut.step(i, tagChar)));
			}
		}
		return tagMap;
	}
	
	protected void handleNamedEntity(AutomatonState a, int endToken, ITokenSequence t, NECollector collector) {
		String surface = t.getSubstring(a.getStartToken(), endToken);
		String type = a.getType();
		NamedEntity namedEntity = new NamedEntity(t.getTokens(a.getStartToken(), endToken), surface, type);
        collector.collect(namedEntity);
        if (a.getType().startsWith(NamedEntityTypes.ONTOLOGY)) {
			Set<String> ontologyIds = runAutToStateToOntIds.get(a.getType()).get(a.getState());
			String s = OntologyTerms.idsForTerm(StringTools.normaliseName(surface));
			if (s != null && s.length() > 0) {
				if (ontologyIds == null) {
                    ontologyIds = new HashSet<String>();
                }
				ontologyIds.addAll(Arrays.asList(s.split("\\s+")));
			}
			namedEntity.addOntIds(ontologyIds);
		}
		if (a.getType().startsWith(NamedEntityTypes.CUSTOM)) {
			Set<String> customTypes = runAutToStateToOntIds.get(a.getType()).get(a.getState());
			namedEntity.addCustTypes(customTypes);
		}
	}
	
	protected void handleTokenForPrefix(Token t, NECollector collector) {
		String prefix = PrefixFinder.getPrefix(t.getValue());
        if (prefix != null) {
            collector.collect(NamedEntity.forPrefix(t, prefix));
        }
	}
	
	protected void findItems(ITokenSequence tokenSequence, List<List<String>> repsList, NECollector collector) {
		findItems(tokenSequence, repsList, 0, tokenSequence.getTokens().size()-1, collector);
	}
	
	protected void findItems(ITokenSequence tokenSequence, List<List<String>> repsList, int startToken, int endToken, NECollector collector) {
		
		List<AutomatonState> autStates = new ArrayList<AutomatonState>();
		List<AutomatonState> newAutStates = new ArrayList<AutomatonState>();

		for(String type : runAuts.keySet()) {
			AutomatonState a = new AutomatonState(runAuts.get(type), type, 0);
			String tokenRepresentation = generateTokenRepresentation("$^");
			for (int j = 0; j < tokenRepresentation.length(); j++) {
				char c = tokenRepresentation.charAt(j);
				a.step(c);
				if (a.getState() == -1) {
					break;
				}
			}
			if (a.getState() != -1) {
				a.addRep("$^");
				autStates.add(a);					
			}
		}
		int i = -1;
		for(Token token : tokenSequence.getTokens()) {
			i++;
			if (i < startToken || i > endToken) {
                continue;
            }
			handleTokenForPrefix(token, collector);
			List<String> tokenRepresentations = repsList.get(token.getId());
			if (tokenRepresentations.isEmpty()) {
				autStates.clear();
				continue;
			}
			for (String type : runAuts.keySet()) {
				autStates.add(new AutomatonState(runAuts.get(type), type, i));				
			}
			for (String tokenRep : tokenRepresentations) {
                String tokenRepCode = getCachedTokenRepresentation(tokenRep);
                if (tokenRepCode == null) {
                    continue;
                }
				for (int k = 0; k < autStates.size(); k++) {
					AutomatonState a = autStates.get(k).clone();
					for(int j = 0; j < tokenRepCode.length(); j++) {
						char c = tokenRepCode.charAt(j);
						a.step(c);
						if (a.getState() == -1) {
                            break;
                        }
					}
					if (a.getState() != -1) {
						a.addRep(tokenRep);
						if (a.isAccept()) {
							handleNamedEntity(a, i, tokenSequence, collector);
						}
						newAutStates.add(a);
					}
				}
			}
			List<AutomatonState> tmp = autStates;
			autStates = newAutStates;
			tmp.clear();
			newAutStates = tmp;
		}		
	}
		
}
