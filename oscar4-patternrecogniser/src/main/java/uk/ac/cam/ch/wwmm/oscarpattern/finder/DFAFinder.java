package uk.ac.cam.ch.wwmm.oscarpattern.finder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import uk.ac.cam.ch.wwmm.oscarpattern.document.Token;
import uk.ac.cam.ch.wwmm.oscarpattern.document.TokenSequence;
import uk.ac.cam.ch.wwmm.oscarpattern.document.Tokeniser;
import uk.ac.cam.ch.wwmm.oscarpattern.terms.OntologyTerms;
import uk.ac.cam.ch.wwmm.oscarpattern.terms.TermMaps;
import uk.ac.cam.ch.wwmm.oscarpattern.tools.Oscar3Props;
import uk.ac.cam.ch.wwmm.oscarpattern.tools.RegExUtils;
import uk.ac.cam.ch.wwmm.oscarpattern.tools.StringTools;
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
	private static final long serialVersionUID = 6130629462990087075L;
	protected Map<String, List<Automaton>> autLists = new HashMap<String, List<Automaton>>();//mapping between token type and automata. Possibly these automata are only used when performing Pattern based entity recognition
	protected Map<String, SuffixTree> simpleAuts = new HashMap<String, SuffixTree>();
	protected Map<String, RunAutomaton> runAuts = new HashMap<String, RunAutomaton>();
	protected Map<String,String> tokenToRep = new HashMap<String,String>();//mapping between token strings and a unique representation code, usually an integer
	protected Set<String> literals = new HashSet<String>();
	protected int tokenId = 0;
	
	protected Map<String,Integer> dfaNumber = new HashMap<String,Integer>();//keeps track of the number of automata that have been generated for a certain token
	protected Map<String,Integer> dfaCount = new HashMap<String,Integer>();//keeps track of the number of tokens of a certain token type encountered
	
	protected Map<String,Integer> ontIdToIntId = new HashMap<String,Integer>();
	protected List<String> ontIds = new ArrayList<String>();;
	protected Map<String,Map<Integer,Set<String>>> runAutToStateToOntIds;
	
	protected Map<String,Pattern> subRes = new HashMap<String,Pattern>();
	
	protected boolean verbose;
	
	protected final static Pattern matchSubRe = Pattern.compile("\\$\\{.*\\}");//dl387: I'm not sure what this is actually supposed to be matching!
	protected final static Pattern digitOrSpace = Pattern.compile("[0-9 ]+");

	protected DFAFinder() {
		verbose = Oscar3Props.getInstance().verbose;
	}
	
	protected abstract void addTerms();
	
	protected void init() {
		literals.add("$(");
		literals.add("$)");
		literals.add("$+");
		literals.add("$*");
		literals.add("$|");
		literals.add("$?");
		literals.add("$^");
		addTerms();
		finishInit();		
		if(verbose) System.out.println("Finished initialising DFA Finder");
	}
	
	private String getRepForToken(String token) {
		//TODO canonicalise token
		if(literals.contains(token)){
			return token.substring(1);
		} else if(tokenToRep.containsKey(token)) {
			return tokenToRep.get(token);
		} else {
			String rep = Integer.toString(++tokenId) + " ";
			//String rep = StringTools.intToBase62(++tokenId) + " ";
			//System.out.println(rep);
			tokenToRep.put(token, rep);
			if(matchSubRe.matcher(token).matches()) {
				subRes.put(token, Pattern.compile(token.substring(2, token.length()-1)));
				//System.out.println("SubRe: " + token + subRes.get(token));
			}
			return rep;
		}
	}

	protected Set<String> getSubReRepsForToken(String token) {
		Set<String> reps = new HashSet<String>();
		for(String rex : subRes.keySet()) {
			if(subRes.get(rex).matcher(token).matches()) {
				reps.add(rex);
			}
		}
		return reps;
	}
	
	protected String getRepForTokenOrNull(String token) {
		//TODO canonicalise token
		if(tokenToRep.containsKey(token)) {
			return tokenToRep.get(token);
		} else {
			return null;
		}
	}
	
	protected void addNE(String ne, String type, boolean alwaysAdd) {
		//System.out.println(type + "\t" + ne);
		if(Oscar3Props.getInstance().dfaSize > 0) {
			if(!dfaCount.containsKey(type)) {
				dfaCount.put(type, 0);
				dfaNumber.put(type, 1);
			}
			int count = dfaCount.get(type) + 1;
			if(count > Oscar3Props.getInstance().dfaSize) {
				count = 0;
				String tmpType = type + "_" + dfaNumber.get(type);
				buildForType(tmpType);
				dfaNumber.put(type, dfaNumber.get(type) + 1);
				if(verbose) System.out.println(type + "_" + dfaNumber.get(type) + " started collecting at " + new GregorianCalendar().getTime());
			}
			dfaCount.put(type, count);
			type = type + "_" + dfaNumber.get(type);
			/*ontDFACount += 1;
			if(ontDFACount > Oscar3Props.getInstance().dfaSize) {
				ontDFACount = 0;
				ontDFANumber++;
			}
			type = type + "_" + ontDFANumber;*/
		}

		TokenSequence ts = Tokeniser.getInstance().tokenise(ne);
		List<String> tokens = ts.getTokenStringList();

		if(!alwaysAdd && tokens.size() == 1 && !ne.contains("$")) return;
		StringBuffer sb = new StringBuffer();
		for(String token : tokens) {
			sb.append(getRepForToken(token));
		}
		String preReStr = sb.toString();
		//List<String> tl = new ArrayList<String>();
		//tl.add(preReStr);
		//for(String reStr : tl) {
		for(String reStr : StringTools.expandRegex(preReStr)) {
			//System.out.println(type + "\t" + reStr);
			if(digitOrSpace.matcher(reStr).matches()) {
				if(simpleAuts.containsKey(type)) {
					simpleAuts.get(type).addContents(reStr);
				} else {
					simpleAuts.put(type, new SuffixTree(reStr));
				}
				if(type.startsWith("ONT") && OntologyTerms.hasTerm(ne)) {
					String ontIdsStr = OntologyTerms.idsForTerm(ne);
					List<String> neOntIds = StringTools.arrayToList(RegExUtils.P_WHITESPACE.split(ontIdsStr));
					for(String ontId : neOntIds) {
						simpleAuts.get(type).addContents(reStr + "X" + getNumberForOntId(ontId));
					}
				} else if(type.startsWith("CUST") && TermMaps.getCustEnt().containsKey(ne)) {
					String custStr = TermMaps.getCustEnt().get(ne);
					List<String> custTypes = StringTools.arrayToList(RegExUtils.P_WHITESPACE.split(custStr));
					for(String custType : custTypes) {
						simpleAuts.get(type).addContents(reStr + "X" + getNumberForOntId(custType));
					}
				}
			} else {
				//System.out.println(reStr);
				if(type.startsWith("ONT") && OntologyTerms.hasTerm(ne)) {
					String ontIdsStr = OntologyTerms.idsForTerm(ne);
					List<String> neOntIds = StringTools.arrayToList(RegExUtils.P_WHITESPACE.split(ontIdsStr));
					sb.append("(X(");
					boolean first = true;
					for(String ontId : neOntIds) {
						if(first) {
							first = false;
						} else {
							sb.append("|");
						}
						sb.append(Integer.toString(getNumberForOntId(ontId)));
					}
					sb.append("))?");
				} else if(type.startsWith("CUST") && TermMaps.getCustEnt().containsKey(ne)) {
					String custStr = TermMaps.getCustEnt().get(ne);
					List<String> custTypes = StringTools.arrayToList(RegExUtils.P_WHITESPACE.split(custStr));
					sb.append("(X(");
					boolean first = true;
					for(String custType : custTypes) {
						if(first) {
							first = false;
						} else {
							sb.append("|");
						}
						sb.append(Integer.toString(getNumberForOntId(custType)));
					}
					sb.append("))?");
				}
				//System.out.println(sb.toString());
				Automaton subAut = new RegExp(sb.toString()).toAutomaton();
				//System.out.println(subAut.isDeterministic());
				//mainAut = mainAut.union(subAut);
				List<Automaton> autList = autLists.get(type);
				if(autList == null) {
					autList = new ArrayList<Automaton>();
					autLists.put(type, autList);
				}
				autList.add(subAut);
			}
		}
	}
	
	private int getNumberForOntId(String ontId) {
		if(ontIdToIntId.containsKey(ontId)) {
			return ontIdToIntId.get(ontId);
		} else {
			int intId = ontIds.size();
			ontIds.add(ontId);
			ontIdToIntId.put(ontId, intId);
			return intId;
		}
	}
	
	private void buildForType(String type) {
		if(autLists.containsKey(type)) {
			if(verbose) System.out.print("Building DFA for: " + type + " at " + new GregorianCalendar().getTime() + "...");
			if(verbose) System.out.println();
			Automaton mainAut;
			//if(verbose) System.out.println("Building DFA from " + autLists.get(type).size() + " items.");
			mainAut = Automaton.union(autLists.get(type));
			System.gc();
			if(verbose) {
				System.out.println("Memory: " + Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().totalMemory() + " " + Runtime.getRuntime().maxMemory());
			}
			mainAut.determinize();
			if(verbose) {
				System.out.println("DFA initialised");
				System.out.println("Memory: " + Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().totalMemory() + " " + Runtime.getRuntime().maxMemory());
			}
			runAuts.put(type, new RunAutomaton(mainAut, false));
			autLists.remove(type);			
		}
		if(simpleAuts.containsKey(type)) {
			if(verbose) System.out.println("Building DFA for: " + type + "b at " + new GregorianCalendar().getTime() + "... ");
			Automaton mainAut = simpleAuts.get(type).toAutomaton();
			if(verbose) System.out.println("DFA initialised");
			runAuts.put(type + "b", new RunAutomaton(mainAut, false));			
			simpleAuts.remove(type);
		}
	}
	
	private void finishInit() {
		for(String type : new HashSet<String>(autLists.keySet())) {
			if(verbose) System.out.println("Building DFA for: " + type + " at " + new GregorianCalendar().getTime() + "...");
			Automaton mainAut;
			//if(verbose) System.out.println("Building DFA from " + autLists.get(type).size() + " items.");
			mainAut = Automaton.union(autLists.get(type));
			System.gc();
			if(verbose) {
				System.out.println("Memory: " + Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().totalMemory() + " " + Runtime.getRuntime().maxMemory());
			}
			mainAut.determinize();
			if(verbose) System.out.println("DFA initialised");
			if(verbose) {
				System.out.println("Memory: " + Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().totalMemory() + " " + Runtime.getRuntime().maxMemory());
			}
			runAuts.put(type, new RunAutomaton(mainAut, false));
			autLists.remove(type);
		}
		for(String type : new HashSet<String>(simpleAuts.keySet())) {
			if(verbose) System.out.println("Building DFA for: " + type + "b at " + new GregorianCalendar().getTime() + "... ");
			Automaton mainAut = simpleAuts.get(type).toAutomaton();
			if(verbose) System.out.println("DFA initialised");
			runAuts.put(type + "b", new RunAutomaton(mainAut, false));			
			simpleAuts.remove(type);
		}
		if(verbose) System.out.println("All DFAs built");
		if(verbose) System.out.println("Analysing DFAs...");
		runAutToStateToOntIds = new HashMap<String,Map<Integer,Set<String>>>();
		for(String s : runAuts.keySet()) {
			if(!(s.startsWith("ONT") || s.startsWith("CUST"))) continue;
			runAutToStateToOntIds.put(s, analyseAutomaton(runAuts.get(s), 'X'));
		}
	}
	
	private Set<String> readOffTags(RunAutomaton runAut, int state) {
		Set<String> tags = new HashSet<String>();
		readOffTags(runAut, state, "", tags);
		return tags;
	}
	
	private void readOffTags(RunAutomaton runAut, int state, String startOfTag, Set<String> tagsFound) {
		if(runAut.isAccept(state)) {
			String ontId = ontIds.get(Integer.parseInt(startOfTag));
			tagsFound.add(ontId);
		}
		for(int i=0;i<10;i++) {
			char c = Integer.toString(i).charAt(0);
			int newState = runAut.step(state, c);
			if(newState != -1) {
				readOffTags(runAut, newState, startOfTag + i, tagsFound);
			}
		}
	}
	
	private Map<Integer,Set<String>> analyseAutomaton(RunAutomaton runAut, char tagChar) {
		Map<Integer,Set<String>> tagMap = new HashMap<Integer,Set<String>>();
		for(int i=0;i<runAut.getSize();i++) {
			if(runAut.isAccept(i) && runAut.step(i, tagChar) != -1) {
				//System.out.println(i);
				tagMap.put(i, readOffTags(runAut, runAut.step(i, tagChar)));
			}
		}
		return tagMap;
	}
	
	//protected abstract List<String> getTokenReps(Token t);
	
	protected abstract void handleNe(AutomatonState a, int endToken, TokenSequence t, ResultsCollector collector);
	
	protected void handleTokenForPrefix(Token t, ResultsCollector collector) {
		
	}
	
	protected void findItems(TokenSequence t, List<List<String>> repsList, ResultsCollector collector) {
		findItems(t, repsList, 0, t.getTokens().size()-1, collector);
	}
	
	protected void findItems(TokenSequence t, List<List<String>> repsList, int startToken, int endToken, ResultsCollector collector) 
	{
		
		List<AutomatonState> autStates = new ArrayList<AutomatonState>();
		List<AutomatonState> newAutStates = new ArrayList<AutomatonState>();
		List<String> tokenReps = new ArrayList<String>();

		for(String type : runAuts.keySet()) {
			AutomatonState a = new AutomatonState(runAuts.get(type), type, 0);
			String tokenRep = getRepForToken("$^");
			for(int j=0;j<tokenRep.length();j++) {
				char c = tokenRep.charAt(j);
				a.step(c);
				if(a.state == -1) {
					break;
				}
			}
			if(a.state != -1) {
				a.addRep("$^");
				autStates.add(a);					
			}
		}
		int i = -1;
		for(Token token : t.getTokens()) {
			i++;
			if(i < startToken || i > endToken) continue;
			handleTokenForPrefix(token, collector);
			tokenReps = repsList.get(token.getId());
			if(tokenReps.size() == 0) {
				autStates.clear();
				continue;
			}
			for(String type : runAuts.keySet()) {
				autStates.add(new AutomatonState(runAuts.get(type), type, i));				
			}
			for(String tokenRep : tokenReps) {
				String tokenRepCode = getRepForTokenOrNull(tokenRep);
				if(tokenRepCode == null) continue;
				for(int k=0;k<autStates.size();k++) {
					AutomatonState a = autStates.get(k).clone();
					for(int j=0;j<tokenRepCode.length();j++) {
						char c = tokenRepCode.charAt(j);
						a.step(c);
						if(a.state == -1) break;
					}
					if(a.state != -1) {
						a.addRep(tokenRep);
						if(a.isAccept()) {
							handleNe(a, i, t, collector);
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
