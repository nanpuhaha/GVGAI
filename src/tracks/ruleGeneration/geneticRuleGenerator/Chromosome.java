package tracks.ruleGeneration.geneticRuleGenerator;
import java.util.*;

import core.game.SLDescription;
import core.game.StateObservation;
import core.game.Event;
import core.game.GameDescription.SpriteData;
import core.player.AbstractPlayer;
import logging.Logger;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.LevelAnalyzer;
import tools.StepController;
import tools.Vector2d;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.awt.Dimension;

public class Chromosome implements Comparable<Chromosome>{
	/**
	 * current chromosome fitness if its a feasible
	 */
	private ArrayList<Double> fitness;
	/**
	 * Parameters to initialize constraints
	 */
	HashMap<String, Object> parameters;
	/**
	 * current chromosome fitness if its an infeasible
	 */
	private double constrainFitness;
	/**
	 * if the fitness is calculated before (no need to recalculate)
	 */
	private boolean calculated;
	/**
	 * the best automated agent
	 */
	private AbstractPlayer automatedAgent;
	/**
	 * the naive automated agent
	 */
	private AbstractPlayer naiveAgent;
	/**
	 * the do nothing automated agent
	 */
	private AbstractPlayer doNothingAgent;
	/**
	 * the random agent
	 */
	private AbstractPlayer randomAgent;
	/**
	 * The current stateObservation of the level
	 */
	private StateObservation stateObs;
	/**
	 * the ruleset this chromosome contains
	 */
	private String[][] ruleset;
	/**
	 * the SL description of this chromosome
	 */
	private SLDescription sl;
	/**
	 * elapsed time
	 */
	private ElapsedCpuTimer time;
	/**
	 * amount of steps allowed for the naive agent to sit around
	 */
	private int FEASIBILITY_STEP_LIMIT = 40;

	private int doNothingLength;
	StateObservation doNothingState;
	StateObservation bestState;
	ArrayList<Types.ACTIONS> bestSol;

	/**
	 * List of all useful sprites in the game
	 */
	private ArrayList<String> usefulSprites;
	/**
	 * Level analyzer for the game
	 */
	private LevelAnalyzer levAl;
	
	/**
	 * Array contains all interactions we want to mutate over
	 */
	private String[] interactions = new String[]{
			"killSprite", "killAll", "killIfHasMore", "killIfHasLess", "killIfFromAbove",
			"killIfOtherHasMore", "transformToSingleton", "spawnBehind",
			"spawnIfHasMore", "spawnIfHasLess", "cloneSprite", "transformTo", "transformIfCounts", 
			"transformToRandomChild", "updateSpawnType", "removeScore", 
			"addHealthPoints",  "addHealthPointsToMax", "subtractHealthPoints", "increaseSpeedToAll", 
			"decreaseSpeedToAll", "setSpeedForAll", "stepBack",  "undoAll", "flipDirection",  
			"reverseDirection", "attractGaze", "align", "turnAround", "wrapAround", "teleportToExit",
			"pullWithIt", "bounceForward", "collectResource", "changeResource"};
	/** 
	 * Array contains all terminations
	 */
	private String[] terminations = new String[] {
		"SpriteCounter", "SpriteCounterMore", "MultiSpriteCounter",
		"StopCounter", "Timeout"};
	/**
	 * Array contains all possible interaction parameter types
	 */
	private String[] interactionParams = new String[] {
		"scoreChange", "stype", "limit", "resource", "stype_other", "forceOrientation", "spawnPoint",
		"value", "geq", "leq"};
	private String[] terminationParams = new String[] {
		"stype", "stype1", "stype2", "stype3"
	};
	/**
	 * Chromosome constructor.  Holds the ruleset and initializes agents within
	 * @param ruleset	the ruleset the chromosome contains
	 * @param sl		the game description
	 * @param time		elapsed time
	 */

	public Chromosome(String[][] ruleset, SLDescription sl, ElapsedCpuTimer time) {
		this.ruleset = ruleset;
		this.sl = sl;
		this.fitness = new ArrayList<Double>();
		fitness.add(0.0);
		fitness.add(0.0);
		this.calculated = false;
		this.stateObs = null;
		this.usefulSprites = SharedData.usefulSprites;
	    this.levAl = new LevelAnalyzer(sl);
	    this.parameters = new HashMap<String, Object>();
		constructAgent();
	}
	/**
	 * Flips a coin to see if we mutate on termination or interaction
	 */
	public void mutate() {
		// loop through as many times as we want to mutate
		int mutationCount = SharedData.random.nextInt(SharedData.MUTATION_AMOUNT);
		for(int i = 0; i < mutationCount; i++) {
			int mutateR = SharedData.random.nextInt(2);
			if(mutateR == 0){
				//mutate interaction set
				mutateInteraction();
			} else {
				// mutate termination
				mutateTermination();
			}
		}
	}
	/**
	 * performs a mutation on a random interaction in the set
	 * 4 types of mutation: insert a new rule, delete an old rule, change a rule, and change rule parameters (but keep the rule)
	 * the interaction ruleset will shift back and forth between an array and an arraylist depending on the circumstances
	 * according to what is easiest to manipulate at the time
	 */
	public void mutateInteraction() {
		ArrayList<String> interactionSet = new ArrayList<>( Arrays.asList(ruleset[0]));
		double mutationType = SharedData.random.nextDouble();
		// we do an insertion
		if(mutationType < SharedData.INSERTION_PROB) {
			// roll dice to see if we will insert a new rule altogether or a new parameter into an existing rule
			double roll = SharedData.random.nextDouble();
			// insert a new parameter onto an existing rule
			if(roll < SharedData.INSERT_PARAM_PROB) {
				// grab a random existing rule
				int point = SharedData.random.nextInt(interactionSet.size());
				String addToMe = interactionSet.get(point);
				// insert a new parameter into it
				String nParam = interactionParams[SharedData.random.nextInt(interactionParams.length)];
				nParam += "=";
				// add either a number or a sprite to the parameter
				double roll1 = SharedData.random.nextDouble();
				// insert a sprite
				if(roll1 < SharedData.PARAM_NUM_OR_SPRITE_PROB) {
					String nSprite = usefulSprites.get(SharedData.random.nextInt(usefulSprites.size()));
					nParam += nSprite;
				}
				// insert a numerical value
				else {
					int val = SharedData.random.nextInt(SharedData.NUMERICAL_VALUE_PARAM);
					nParam += val;
				}
				addToMe += " " + nParam;
				// replace the old rule with the modified one
				ruleset[0][point] = addToMe;
			}
			// insert an entirely new rule, possibly with a parameter in it
			else {
				String nInteraction = interactions[SharedData.random.nextInt(interactions.length)];
				int i1 = SharedData.random.nextInt(usefulSprites.size());
			    int i2 = (i1 + 1 + SharedData.random.nextInt(usefulSprites.size() - 1)) % usefulSprites.size();
			    
			    String newInteraction = usefulSprites.get(i1) + " " + usefulSprites.get(i2) + " > " + nInteraction;
			    // roll to see if you insert a parameter into this interaction
			    roll = SharedData.random.nextDouble();
			    
			    if(roll < SharedData.INSERT_PARAM_PROB) {
			    	String nParam = interactionParams[SharedData.random.nextInt(interactionParams.length)];
					nParam += "=";
					// add either a number or a sprite to the parameter
					double roll1 = SharedData.random.nextDouble();
					// insert a sprite
					if(roll1 < SharedData.PARAM_NUM_OR_SPRITE_PROB) {
						String nSprite = usefulSprites.get(SharedData.random.nextInt(usefulSprites.size()));
						nParam += nSprite;
					}
					// insert a numerical value
					else {
						int val = SharedData.random.nextInt(SharedData.NUMERICAL_VALUE_PARAM);
						nParam += val;
					}
					newInteraction += " " + nParam;
			    }
			    // add the new interaction to the interaction set
			    interactionSet.add(newInteraction);
			    // remove weird space from the arrayList
			    interactionSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				interactionSet = (ArrayList<String>) interactionSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[interactionSet.size()];
				ruleset[0] = interactionSet.toArray(ruleset[0]);
			}
		} 
		// we do a deletion
		else if(mutationType < SharedData.DELETION_PROB + SharedData.INSERTION_PROB) {
			// roll dice to see if we will delete a rule altogether or a parameter of an existing rule
			double roll = SharedData.random.nextDouble();
			// delete a parameter from an existing rule
			if(roll < SharedData.DELETE_PARAM_PROB) {
				int point = SharedData.random.nextInt(interactionSet.size());
				String deleteFromMe = interactionSet.get(point);
				// find all parameters for this rule, note: there may be none.  In that case we do nothing.
				String[] splitDeleteFromMe = deleteFromMe.split("\\s+");
				ArrayList<String> params = new ArrayList<String>();
				for(String param : splitDeleteFromMe) {
					// we can assume that if one of the split strings contains an = sign that it is a parameter
					if(param.contains("=")){
						params.add(param);
					}
				}
				// if no params do nothing
				if(params.size() == 0) {
					
				} 
				// if one param, remove it
				else if(params.size() == 1) {
					String fixedRule = "";
					for(String part : splitDeleteFromMe) {
						if(!part.contains("=")) {
							fixedRule += part;
						}
					}
					interactionSet.set(point, fixedRule);
				}
				else {
					// pick one of the rules and don't include it, but include the others
					int rule = SharedData.random.nextInt(params.size());
					String fixedRule = "";
					for(String part : splitDeleteFromMe) {
						if(!part.equals(params.get(rule))) {
							fixedRule += part + " ";
						}
					}
					interactionSet.set(point, fixedRule);
				}
			    // remove weird space from the arrayList
			    interactionSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				interactionSet = (ArrayList<String>) interactionSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[interactionSet.size()];
				ruleset[0] = interactionSet.toArray(ruleset[0]);
			}
			// delete an entire rule from the interaction set
			else{
				int point = SharedData.random.nextInt(interactionSet.size());
				// dont try to delete from an empty interaction set
				if (interactionSet.size() > 1) {
					interactionSet.remove(point);
				}
			    // remove weird space from the arrayList
			    interactionSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				interactionSet = (ArrayList<String>) interactionSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[interactionSet.size()];
				ruleset[0] = interactionSet.toArray(ruleset[0]);
			}
		} 
		// modify a rule from the interaction set by changing its parameters
		else if (mutationType < SharedData.MODIFY_RULE_PROB + SharedData.DELETION_PROB + SharedData.INSERTION_PROB) {
			// pick our modified rule
			int point = SharedData.random.nextInt(interactionSet.size());
			
			// roll to see what kind of modification, either a rule change or a parameter change
			double roll = SharedData.random.nextDouble();
			// modify a parameter of a rule completely
			if(roll < SharedData.MODIFY_PARAM_PROB) {
				String modifyFromMe = interactionSet.get(point);
				// find all parameters for this rule, note: there may be none.  In that case we do nothing.
				String[] splitModifyFromMe = modifyFromMe.split("\\s+");
				ArrayList<String> ps = new ArrayList<String>();
				for(String param : splitModifyFromMe) {
					// we can assume that if one of the split strings contains an = sign that it is a parameter
					if(param.contains("=")){
						ps.add(param);
					}
				}
				// if no params do nothing
				if(ps.size() == 0) {
					
				} else {
					// pick one of the rules and don't include it, but include the others
					int rule = SharedData.random.nextInt(ps.size());
					String fixedRule = "";
					for(String part : splitModifyFromMe) {
						if(!part.equals(ps.get(rule))) {
							fixedRule += part + " ";
						} 
						// we are on the parameter we want to replace
						else {
							interactionSet.set(point, fixedRule);
							String nParam = interactionParams[SharedData.random.nextInt(interactionParams.length)];
							nParam += "=";
							// add either a number or a sprite to the parameter
							double roll1 = SharedData.random.nextDouble();
							// insert a sprite
							if(roll1 < SharedData.PARAM_NUM_OR_SPRITE_PROB) {
								String nSprite = usefulSprites.get(SharedData.random.nextInt(usefulSprites.size()));
								nParam += nSprite;
							}
							// insert a numerical value
							else {
								int val = SharedData.random.nextInt(SharedData.NUMERICAL_VALUE_PARAM);
								nParam += val;
							}
							fixedRule += nParam;
						}
					}
				}
			    // remove weird space from the arrayList
			    interactionSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				interactionSet = (ArrayList<String>) interactionSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[interactionSet.size()];
				ruleset[0] = interactionSet.toArray(ruleset[0]);
			} 
			// modify a rule, but leave the parameters and sprites
			else {
				String newRule = interactions[SharedData.random.nextInt(interactions.length)];
				String modRule = ruleset[0][point];
				
				String[] splitModRule = modRule.split("\\s+");
				// replace old rule with new one
				splitModRule[3] = newRule;
				newRule = splitModRule[0];
				for(String part : splitModRule) {
					newRule += part + " ";
				}
				ruleset[0][point] = newRule;
			}
		} 
		// we should never ever reach this point
		else {
			System.err.println("What?! Howd we even get here!?");
		}
	}
	/**
	 * performs a mutation on a random termination in the set
	 * 4 types of mutation: insert a new rule, delete an old rule, change a rule, and change rule parameters (but keep the rule)
	 * the termination ruleset will shift back and forth between an array and an arraylist depending on the circumstances
	 * according to what is easiest to manipulate at the time. 
	 */
	public void mutateTermination() {
		ArrayList<String> terminationSet = new ArrayList<>( Arrays.asList(ruleset[1]));
		double mutationType = SharedData.random.nextDouble();
		// we do an insertion
		if(mutationType < SharedData.INSERTION_PROB) {
			// roll dice to see if we will insert a new rule altogether or a new parameter into an existing rule
			double roll = SharedData.random.nextDouble();
			// insert a new parameter onto an existing rule
			if(roll < SharedData.INSERT_PARAM_PROB) {
				// grab a random existing rule
				int point = SharedData.random.nextInt(terminationSet.size());
				String addToMe = terminationSet.get(point);
				// insert a new parameter into it
				String nParam = terminationParams[SharedData.random.nextInt(terminationParams.length)];
				nParam += "=";
				// add either a number or a sprite to the parameter
				double roll1 = SharedData.random.nextDouble();
				// insert a sprite
				if(roll1 < SharedData.PARAM_NUM_OR_SPRITE_PROB) {
					String nSprite = usefulSprites.get(SharedData.random.nextInt(usefulSprites.size()));
					nParam += nSprite;
				}
				// insert a numerical value
				else {
					int val = SharedData.random.nextInt(SharedData.NUMERICAL_VALUE_PARAM);
					nParam += val;
				}
				addToMe += " " + nParam;
				// replace the old rule with the modified one
				ruleset[1][point] = addToMe;
			}
			// insert an entirely new rule, possibly with a parameter in it
			else {
				String nInteraction = terminations[SharedData.random.nextInt(terminations.length)];
				int i1 = SharedData.random.nextInt(usefulSprites.size());
			    int i2 = (i1 + 1 + SharedData.random.nextInt(usefulSprites.size() - 1)) % usefulSprites.size();
			    
			    String newInteraction = usefulSprites.get(i1) + " " + usefulSprites.get(i2) + " > " + nInteraction;

			    
			    // since all termination rules have at least one param from the set, we will include one
			    String nParam = interactionParams[SharedData.random.nextInt(interactionParams.length)];
				nParam += "=";
				// add either a number or a sprite to the parameter
				double roll1 = SharedData.random.nextDouble();
				// insert a sprite
				if(roll1 < SharedData.PARAM_NUM_OR_SPRITE_PROB) {
					String nSprite = usefulSprites.get(SharedData.random.nextInt(usefulSprites.size()));
					nParam += nSprite;
				}
				// insert a numerical value
				else {
					int val = SharedData.random.nextInt(SharedData.NUMERICAL_VALUE_PARAM);
					nParam += val;
				}
				newInteraction += " " + nParam;
				
				// add win and limit
				newInteraction += " win=";
				
				double roll2 = SharedData.random.nextDouble();
				if(roll2 < SharedData.WIN_PARAM_PROB){
					newInteraction += "True";
				} else {
					newInteraction += "False";
				}
				
				newInteraction += " limit=";
				
				
			    // add the new interaction to the interaction set
			    terminationSet.add(newInteraction);
			    // remove weird space from the arrayList
			    terminationSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				terminationSet = (ArrayList<String>) terminationSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[terminationSet.size()];
				ruleset[0] = terminationSet.toArray(ruleset[0]);
			}
		} 
		// we do a deletion
		else if(mutationType < SharedData.DELETION_PROB + SharedData.INSERTION_PROB) {
			// roll dice to see if we will delete a rule altogether or a parameter of an existing rule
			double roll = SharedData.random.nextDouble();
			// delete a parameter from an existing rule
			if(roll < SharedData.DELETE_PARAM_PROB) {
				int point = SharedData.random.nextInt(terminationSet.size());
				String deleteFromMe = terminationSet.get(point);
				// find all parameters for this rule, note: there may be none.  In that case we do nothing.
				String[] splitDeleteFromMe = deleteFromMe.split("\\s+");
				ArrayList<String> params = new ArrayList<String>();
				for(String param : splitDeleteFromMe) {
					// we can assume that if one of the split strings contains an = sign that it is a parameter
					if(param.contains("=")){
						params.add(param);
					}
				}
				// if no params do nothing
				if(params.size() == 0) {
					
				} 
				// if one param, remove it
				else if(params.size() == 1) {
					String fixedRule = "";
					for(String part : splitDeleteFromMe) {
						if(!part.contains("=")) {
							fixedRule += part;
						}
					}
					terminationSet.set(point, fixedRule);
				}
				else {
					// pick one of the rules and don't include it, but include the others
					int rule = SharedData.random.nextInt(params.size());
					String fixedRule = "";
					for(String part : splitDeleteFromMe) {
						if(!part.equals(params.get(rule))) {
							fixedRule += part + " ";
						}
					}
					terminationSet.set(point, fixedRule);
				}
			    // remove weird space from the arrayList
				terminationSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				terminationSet = (ArrayList<String>) terminationSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[terminationSet.size()];
				ruleset[0] = terminationSet.toArray(ruleset[0]);
			}
			// delete an entire rule from the interaction set
			else{
				int point = SharedData.random.nextInt(terminationSet.size());
				// dont try to delete from an empty interaction set
				if (terminationSet.size() > 1) {
					terminationSet.remove(point);
				}
			    // remove weird space from the arrayList
				terminationSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				terminationSet = (ArrayList<String>) terminationSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[terminationSet.size()];
				ruleset[0] = terminationSet.toArray(ruleset[0]);
			}
		} 
		// modify a rule from the interaction set by changing its parameters
		else if (mutationType < SharedData.MODIFY_RULE_PROB + SharedData.DELETION_PROB + SharedData.INSERTION_PROB) {
			// pick our modified rule
			int point = SharedData.random.nextInt(terminationSet.size());
			
			// roll to see what kind of modification, either a rule change or a parameter change
			double roll = SharedData.random.nextDouble();
			// modify a parameter of a rule completely
			if(roll < SharedData.MODIFY_PARAM_PROB) {
				String modifyFromMe = terminationSet.get(point);
				// find all parameters for this rule, note: there may be none.  In that case we do nothing.
				String[] splitModifyFromMe = modifyFromMe.split("\\s+");
				ArrayList<String> ps = new ArrayList<String>();
				for(String param : splitModifyFromMe) {
					// we can assume that if one of the split strings contains an = sign that it is a parameter
					if(param.contains("=")){
						ps.add(param);
					}
				}
				// if no params do nothing
				if(ps.size() == 0) {
					
				} else {
					// pick one of the rules and don't include it, but include the others
					int rule = SharedData.random.nextInt(ps.size());
					String fixedRule = "";
					for(String part : splitModifyFromMe) {
						if(!part.equals(ps.get(rule))) {
							fixedRule += part + " ";
						} 
						// we are on the parameter we want to replace
						else {
							terminationSet.set(point, fixedRule);
							String nParam = interactionParams[SharedData.random.nextInt(interactionParams.length)];
							nParam += "=";
							// add either a number or a sprite to the parameter
							double roll1 = SharedData.random.nextDouble();
							// insert a sprite
							if(roll1 < SharedData.PARAM_NUM_OR_SPRITE_PROB) {
								String nSprite = usefulSprites.get(SharedData.random.nextInt(usefulSprites.size()));
								nParam += nSprite;
							}
							// insert a numerical value
							else {
								int val = SharedData.random.nextInt(SharedData.NUMERICAL_VALUE_PARAM);
								nParam += val;
							}
							fixedRule += nParam;
						}
					}
				}
			    // remove weird space from the arrayList
				terminationSet.removeIf(s -> s == null);
			    // stream the list back into itself to avoid duplicate rules from having been created
				terminationSet = (ArrayList<String>) terminationSet.stream().distinct().collect(Collectors.toList());
				// redefine the interaction array with the interaction array list
				ruleset[0] = new String[terminationSet.size()];
				ruleset[0] = terminationSet.toArray(ruleset[0]);
			} 
			// modify a rule, but leave the parameters and sprites
			else {
				String newRule = interactions[SharedData.random.nextInt(interactions.length)];
				String modRule = ruleset[0][point];
				
				String[] splitModRule = modRule.split("\\s+");
				// replace old rule with new one
				splitModRule[3] = newRule;
				newRule = splitModRule[0];
				for(String part : splitModRule) {
					newRule += part + " ";
				}
				ruleset[0][point] = newRule;
			}
		} 
		// we should never ever reach this point
		else {
			System.err.println("What?! Howd we even get here!?");
		}
	}
	/**
	 * clone the chromosome data
	 */
	public Chromosome clone(){
		// copy ruleset into nRuleset. Two for loops, in case 2d array is jagged
		String[][] nRuleset = new String[ruleset.length][];
		nRuleset[0] = new String[ruleset[0].length];
		nRuleset[1] = new String[ruleset[1].length];
		for(int i = 0; i < ruleset[0].length; i++) {
			nRuleset[0][i] = ruleset[0][i];
		}
		for(int i = 0; i < ruleset[1].length; i++) {
			nRuleset[1][i] = ruleset[1][i];
		}
		Chromosome c = new Chromosome(nRuleset, sl, time);
		return c;
	}
	/**
	 * calculates the fitness of the chromosome.  If the chromosome causes errors in build, then it will
	 * be considered infeasible
	 */
	public void calculateFitness(long time) {
		boolean isFeasible = false;
		isFeasible = feasibilityTest();
		constrainFitness = 0;
	}
	/**
	 * first checks to see if there are no build errors, if there are, this is infeasible. 
	 * Otherwise, it will check to see if a do nothing agent dies within the first 40 steps of playing. 
	 * if it does, this is infeasible.
	 * @return
	 */
	private boolean feasibilityTest() {
		Logger log = Logger.getInstance();
		sl.testRules(this.getRuleset()[0], this.getRuleset()[1]);
		if(log.getMessageCount() > 0) {
			// then there must have been an error
			log.flushMessages();
			return false;
		}
		doNothingLength = Integer.MAX_VALUE;
		for(int i = 0; i < SharedData.REPETITION_AMOUNT; i++) {
			int temp = this.getAgentResult(getStateObservation().copy(), FEASIBILITY_STEP_LIMIT, this.naiveAgent);
			if(temp < doNothingLength){
				doNothingLength = temp;
			}
		}
		if(doNothingLength < 40) {
			return false;
		}
		return true;
	}
	
	/**
	 * Play the current level using the naive player
	 * @param stateObs	the current stateObservation object that represent the level
	 * @param steps		the maximum amount of steps that it shouldn't exceed it
	 * @param agent		current agent to play the level
	 * @return			the number of steps that the agent stops playing after (<= steps)
	 */
	private int getAgentResult(StateObservation stateObs, int steps, AbstractPlayer agent){
		int i =0;
		for(i=0;i<steps;i++){
			if(stateObs.isGameOver()){
				break;
			}
			ElapsedCpuTimer timer = new ElapsedCpuTimer();
			timer.setMaxTimeMillis(SharedData.EVALUATION_STEP_TIME);
			Types.ACTIONS bestAction = agent.act(stateObs, timer);
			stateObs.advance(bestAction);
		}
		return i;
	}
	/**
	 * initialize the agents used during evaluating the chromosome
	 */
	private void constructAgent(){
		try{
			Class agentClass = Class.forName(SharedData.BEST_AGENT_NAME);
			Constructor agentConst = agentClass.getConstructor(new Class[]{StateObservation.class, ElapsedCpuTimer.class});
			automatedAgent = (AbstractPlayer)agentConst.newInstance(getStateObservation().copy(), null);
		}
		catch(Exception e){
			e.printStackTrace();
		}

		try{
			Class agentClass = Class.forName(SharedData.NAIVE_AGENT_NAME);
			Constructor agentConst = agentClass.getConstructor(new Class[]{StateObservation.class, ElapsedCpuTimer.class});
			naiveAgent = (AbstractPlayer)agentConst.newInstance(getStateObservation().copy(), null);
		}
		catch(Exception e){
			e.printStackTrace();
		}

		try{
			Class agentClass = Class.forName(SharedData.DO_NOTHING_AGENT_NAME);
			Constructor agentConst = agentClass.getConstructor(new Class[]{StateObservation.class, ElapsedCpuTimer.class});
			doNothingAgent = (AbstractPlayer)agentConst.newInstance(getStateObservation().copy(), null);
		}
		catch(Exception e){
			e.printStackTrace();
		}
		try{
			Class agentClass = Class.forName(SharedData.RANDOM_AGENT_NAME);
			Constructor agentConst = agentClass.getConstructor(new Class[]{StateObservation.class, ElapsedCpuTimer.class});
			randomAgent = (AbstractPlayer)agentConst.newInstance(getStateObservation().copy(), null);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * get game state observation for the current level
	 * @return	StateObservation for the current level
	 */
	private StateObservation getStateObservation(){
		if(stateObs != null){
			return stateObs;
		}
		stateObs = sl.testRules(ruleset[0], ruleset[1]);
		return stateObs;
	}
	
	/**
	 * Compare two chromosome with each other based on their
	 * constrained fitness and normal fitness
	 */
	@Override
	public int compareTo(Chromosome o) {
		if(this.constrainFitness < 1 || o.constrainFitness < 1){
			if(this.constrainFitness < o.constrainFitness){
				return 1;
			}
			if(this.constrainFitness > o.constrainFitness){
				return -1;
			}
			return 0;
		}

		double firstFitness = 0;
		double secondFitness = 0;
		for(int i=0; i<this.fitness.size(); i++){
			firstFitness += this.fitness.get(i);
			secondFitness += o.fitness.get(i);
		}

		if(firstFitness > secondFitness){
			return -1;
		}

		if(firstFitness < secondFitness){
			return 1;
		}

		return 0;
	}
	/**
	 * Returns the fitness of the chromosome
	 * @return fitness the fitness of the chromosome
	 */
	public ArrayList<Double> getFitness() {
		return fitness;
	}
	/**
	 * Get constraint fitness for infeasible chromosome
	 * @return	1 if its feasible and less than 1 if not
	 */
	public double getConstrainFitness(){
		return constrainFitness;
	}
	/**
	 * returns the ruleset of this chromosome
	 * @return
	 */
	public String[][] getRuleset() {
		return ruleset;
	}
	/**
	 * sets the ruleset
	 * @param nRuleset	the new ruleset
	 */
	public void setRuleset(String[][] nRuleset) {
		this.ruleset = nRuleset;
	}
}
