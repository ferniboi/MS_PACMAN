package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.GameView;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;

public class ExpertPacMan extends Controller<MOVE> {
	private int escapeJunctions;
	private int safeDist;
	private int alignDist;
	private long computingTime;
	private int dist;
	private int currentIndex;
	private int nextIndex;
	private int exitJunction;
	private int forbiddenIndex;
	private boolean nextIndexIsSafe;
	private boolean goNearestPill;
	private boolean verbose;
	private boolean highlight;
	private ArrayList<Integer> edibleGhostsIndices;
	private ArrayList<Integer> ghostsIndices;
	private ArrayList<Integer> trueGhostsIndices;
	private MOVE myMove = MOVE.NEUTRAL;

	/**
	 * starts the computing time counter
	 * refreshes instance variables
	 * 
	 * @param game current game
	 */
	private void startComputing(Game game) {
		this.computingTime = System.currentTimeMillis();
		this.currentIndex = game.getPacmanCurrentNodeIndex();
		this.nextIndexIsSafe = false;
		this.nextIndex = this.currentIndex;
		this.edibleGhostsIndices = new ArrayList<Integer>();
		this.ghostsIndices = new ArrayList<Integer>();
		this.trueGhostsIndices = new ArrayList<Integer>();
	}

	/**
	 * enables next move for ms pacman, grants debug useful information about computation
	 * 
	 * @param game                    current game
	 * @param highlightFobriddenIndex whether to highlight or not forbiddenIndex
	 * @param color                   which color to use for shortest path highlight
	 * @param message                 debug message to print
	 * @return true if a new target has been found, otherwise false
	 */
	private boolean endComputing(Game game, boolean highlightFobriddenIndex, Color color, String message) {
		if (this.nextIndexIsSafe) {
			if (this.highlight) {
				GameView.addPoints(game, color, game.getShortestPath(this.currentIndex, this.nextIndex));
				if (highlightFobriddenIndex)
					GameView.addPoints(game, Color.white, game.getShortestPath(this.currentIndex, this.forbiddenIndex));
			}
			this.computingTime = System.currentTimeMillis() - this.computingTime;
			if (this.computingTime >= 40)
				System.out.println("time failure" + " -- " + this.computingTime);
			if (this.verbose)
				System.out.println(message + " -- " + this.computingTime);
		}
		return this.nextIndexIsSafe;
	}

	/**
	 * sets the next target for ms pacman if the selection criteria is satisfied
	 * 
	 * @param game        current game
	 * @param condition   min or max distance, or even more complex
	 * @param dist        new distance satisfying selection criteria
	 * @param targetIndex next target where ms pacman is heading to
	 */
	private void setNextIndex(Game game, boolean condition, int dist, int targetIndex) {
		if (this.highlight)
			GameView.addPoints(game, Color.gray, game.getShortestPath(this.currentIndex, targetIndex));
		if (condition) {
			this.dist = dist;
			this.nextIndexIsSafe = true;
			this.nextIndex = targetIndex;
		}
	}

	/**
	 * searches for neighbors of a target index
	 * 
	 * @param game        current game
	 * @param discovered  path's indices already discovered
	 * @param targetIndex index whose neighbors are requested
	 */
	private void getNeighbors(Game game, HashSet<Integer> discovered, int targetIndex) {
		if (game.isJunction(targetIndex)) {
			this.exitJunction = targetIndex;
			return;
		}
		for (int neighborIndex : game.getNeighbouringNodes(targetIndex))
			if (discovered.add(neighborIndex))
				this.getNeighbors(game, discovered, neighborIndex);
	}

	/**
	 * searches for the exit junction of a path by searching recursively along the
	 * path from target index
	 * 
	 * @param game        current game
	 * @param targetIndex index of the path from which to start the search for the
	 *                    exit junction
	 */
	private void getExit(Game game, int targetIndex) {
		HashSet<Integer> discovered = new HashSet<Integer>();
		for (int pathIndex : game.getShortestPath(this.currentIndex, targetIndex))
			discovered.add(pathIndex);
		this.getNeighbors(game, discovered, targetIndex);
	}

	/**
	 * tries to anticipate ghost's next index, in order to save time for other
	 * edible ghosts, takes into account the shortest path from ms pacman to the
	 * ghost, and the shortest path from the ghost (with its last move made) to ms
	 * pacman, the shortest one is selected
	 * 
	 * @param game       current game
	 * @param ghost      edible ghost whose shortest path is uncertain
	 * @param ghostIndex edible ghost's next index
	 * @return the index of the next junction where the ghost is heading to, or the
	 *         ghost index, depending on which one is closer
	 */
	private int getEdibleGhostNextIndex(Game game, GHOST ghost, int ghostIndex) {
		MOVE lastMoveMade = game.getGhostLastMoveMade(ghost);
		int currentGhostDist = game.getShortestPathDistance(ghostIndex, this.currentIndex);
		int nextGhostDist = game.getShortestPathDistance(ghostIndex, this.currentIndex, lastMoveMade);
		if (nextGhostDist < currentGhostDist)
			for (int pathIndex : game.getShortestPath(ghostIndex, this.currentIndex, lastMoveMade))
				if (game.isJunction(pathIndex) && pathIndex != this.currentIndex)
					return pathIndex;
		return ghostIndex;
	}

	/**
	 * checks whether at least two junctions are reachable safely before taking a
	 * dangerous path, safety check is performed by taking into account the distance
	 * that ms pacman needs to cover in order to reach the target and escape from
	 * there
	 * 
	 * @param game        current game
	 * @param targetIndex the index ms pacman wants to reach safely
	 * @return true if the shortest path can be taken safely, otherwise false
	 */
	private boolean isNextIndexSafe(Game game, int targetIndex) {
		if (this.ghostsIndices.size() == 0)
			return true;
		int safeJunctions = 0;
		int targetDist = game.getShortestPathDistance(this.currentIndex, targetIndex);
		for (int junctionIndex : game.getJunctionIndices()) {
			boolean nextJunctionSafe = true;
			int junctionDist = targetDist + game.getShortestPathDistance(targetIndex, junctionIndex);
			for (int ghostIndex : this.ghostsIndices)
				if (game.getShortestPathDistance(ghostIndex, junctionIndex) - junctionDist < this.safeDist)
					nextJunctionSafe = false;
			if (nextJunctionSafe && ++safeJunctions >= this.escapeJunctions)
				return true;
		}
		return false;
	}

	/**
	 * checks whether ms pacman can reach the target before anyone else, if ms
	 * pacman shortest path to the target is the shortest one among the ghosts, no
	 * ghost can hurt ms pacman
	 * 
	 * @param game        current game
	 * @param targetIndex the index ms pacman wants to reach safely
	 * @return true if the shortest path can be taken safely, otherwise false
	 */
	private boolean isIndexSafe(Game game, int targetIndex) {
		if (this.ghostsIndices.size() == 0)
			return true;
		int targetDist = game.getShortestPathDistance(this.currentIndex, targetIndex);
		for (int ghostIndex : this.ghostsIndices)
			if (game.getShortestPathDistance(ghostIndex, targetIndex) - targetDist < this.safeDist)
				return false;
		return true;
	}

	/**
	 * checks whether ghosts are close enough to ms pacman to follow her along a
	 * dangerous path, if at least one ghost is following ms pacman, she can't turn
	 * back, to see if at least two junctions are reachable or not, we have to take
	 * into account both conditions of close and far ghosts, with close ghosts we
	 * need first to reach safely the exit of this path, otherwise ms pacman can
	 * turn back and find an escape directly from the target index
	 * 
	 * @param game        current game
	 * @param targetIndex the index ms pacman wants to reach safely
	 * @return true if the shortest path can be taken safely, otherwise false
	 */
	private boolean isIndexClear(Game game, int targetIndex) {
		if (this.ghostsIndices.size() == 0)
			return true;
		this.getExit(game, targetIndex);
		boolean followingGhosts = false;
		HashSet<Integer> unsafeJunctions = new HashSet<Integer>();
		int targetDist = game.getShortestPathDistance(this.currentIndex, targetIndex);
		for (int ghostIndex : this.ghostsIndices)
			if (targetDist > game.getShortestPathDistance(ghostIndex, this.currentIndex))
				followingGhosts = true;
		if (followingGhosts) {
			int exitDist = targetDist + game.getShortestPathDistance(targetIndex, this.exitJunction);
			for (int ghostIndex : this.ghostsIndices)
				if (targetDist <= game.getShortestPathDistance(ghostIndex, this.currentIndex))
					for (int junctionIndex : game.getJunctionIndices()) {
						int junctionDist = exitDist + game.getShortestPathDistance(this.exitJunction, junctionIndex);
						int ghostJunctionDist = game.getShortestPathDistance(ghostIndex, junctionIndex);
						if (ghostJunctionDist - junctionDist < this.safeDist)
							unsafeJunctions.add(junctionIndex);
					}
		} else {
			for (int ghostIndex : this.ghostsIndices)
				for (int junctionIndex : game.getJunctionIndices()) {
					int junctionDist = targetDist + game.getShortestPathDistance(targetIndex, junctionIndex);
					int ghostJunctionDist = game.getShortestPathDistance(ghostIndex, junctionIndex);
					if (ghostJunctionDist - junctionDist < this.safeDist)
						unsafeJunctions.add(junctionIndex);
				}
		}
		return game.getJunctionIndices().length - unsafeJunctions.size() >= this.escapeJunctions;
	}

	/**
	 * check whether ms pacman is heading to an edible ghost safely by passing in
	 * front of the ghosts liar
	 * 
	 * @param game        current game
	 * @param targetIndex the index ms pacman wants to reach safely
	 * @return true if no ghosts are in the liar or the path to an edible ghost do
	 *         not pass through the liar index, otherwise false
	 */
	private boolean isLiarIndexSafe(Game game, int targetIndex) {
		if (this.trueGhostsIndices.size() + this.edibleGhostsIndices.size() == 4)
			return true;
		int currentDist = game.getShortestPathDistance(this.currentIndex, targetIndex);
		int liarDist = game.getShortestPathDistance(targetIndex, game.getGhostInitialNodeIndex());
		return currentDist - liarDist < this.safeDist;
	}

	/**
	 * checks whether all remaining pills are reachable safely
	 * 
	 * @param game current game
	 * @return true if all remaining pills are reachable safely, otherwise false
	 */
	private boolean lastPills(Game game) {
		int safePills = 0;
		for (int pillIndex : game.getActivePillsIndices())
			if (this.isIndexSafe(game, pillIndex))
				safePills++;
		return safePills == game.getActivePillsIndices().length;
	}

	/**
	 * checks if reaching target index grants ghosts alignment, ghosts are going to
	 * align them if their path to the target follows ms pacman
	 * 
	 * @param game        current game
	 * @param targetIndex candidate index for ghosts alignment
	 * @return true if tragetIndex grants ghosts alignment, otherwise false
	 */
	private boolean indexAlignGhosts(Game game, int targetIndex) {
		if (this.trueGhostsIndices.size() == 0)
			return true;
		int ghostCounter = 0;
		for (int ghostIndex : this.trueGhostsIndices)
			for (int pathIndex : game.getShortestPath(ghostIndex, targetIndex))
				if (pathIndex == this.currentIndex && ++ghostCounter == this.trueGhostsIndices.size())
					return true;
		return false;
	}

	/**
	 * checks whether the ghosts are aligned or not, ghosts are considered aligned
	 * if all ghosts are in the shortest path from the farthest ghost to ms pacman
	 * 
	 * @param game current game
	 * @return true if ghosts are aligned, otherwise false
	 */
	private boolean alignedGhosts(Game game) {
		if (this.trueGhostsIndices.size() == 0)
			return true;
		this.dist = Integer.MIN_VALUE;
		int farthestGhostIndex = this.currentIndex;
		for (int trueGhostIndex : this.trueGhostsIndices)
			if (trueGhostIndex != game.getGhostInitialNodeIndex()) {
				int dist = game.getShortestPathDistance(trueGhostIndex, this.currentIndex);
				if (dist > this.dist) {
					this.dist = dist;
					farthestGhostIndex = trueGhostIndex;
				}
			}
		int ghostCounter = 0;
		for (int trueGhostIndex : this.trueGhostsIndices)
			if (trueGhostIndex == farthestGhostIndex)
				ghostCounter++;
		for (int pathIndex : game.getShortestPath(farthestGhostIndex, this.currentIndex))
			for (int trueGhostIndex : this.trueGhostsIndices)
				if (pathIndex == trueGhostIndex && ++ghostCounter == this.trueGhostsIndices.size())
					return true;
		return false;
	}

	/**
	 * tries to align ghosts and facilitate the game, in this case ms pacman tries
	 * to stay in the middle of the ghosts and attract them, an escape is always
	 * granted, so ms pacman can reach safely at least two junctions
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean activeAlignGhosts(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int junctionIndex : game.getJunctionIndices())
			if (junctionIndex != forbiddenIndex && this.isNextIndexSafe(game, junctionIndex)) {
				int dist = 0;
				int ghostShortestDist = Integer.MAX_VALUE;
				for (int trueGhostIndex : this.trueGhostsIndices) {
					int ghostDist = game.getShortestPathDistance(trueGhostIndex, junctionIndex);
					dist += ghostDist;
					if (ghostDist < ghostShortestDist)
						ghostShortestDist = ghostDist;
				}
				this.setNextIndex(game, dist < this.dist && ghostShortestDist > this.alignDist, dist, junctionIndex);
			}
		return this.endComputing(game, true, Color.green, "activeAlignGhosts");
	}

	/**
	 * tries to align ghosts and facilitate the game, in this case ms pacman tries
	 * to reach a safe target for which ghosts share a portion of their shortest
	 * path, again, an escape is always granted, and so ms pacman can reach safely
	 * at least two junctions
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean passiveAlignGhosts(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int junctionIndex : game.getJunctionIndices())
			if (junctionIndex != forbiddenIndex)
				if (this.indexAlignGhosts(game, junctionIndex) && this.isNextIndexSafe(game, junctionIndex)) {
					int dist = game.getShortestPathDistance(this.currentIndex, junctionIndex);
					this.setNextIndex(game, dist < this.dist, dist, junctionIndex);
				}
		return this.endComputing(game, true, Color.green, "passiveAlignGhosts");
	}

	/**
	 * tries to reach safely an edible ghost, but does not need an escape, this is
	 * adopted when ghosts are aligned, ghosts are considered edible if they can be
	 * reached in time and in respect of a safe distance, in order to avoid of being
	 * trapped once edible time expires
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean activeFindEdibleGhost(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (GHOST ghost : GHOST.values()) {
			int edibleGhostIndex = game.getGhostCurrentNodeIndex(ghost);
			int dist = game.getShortestPathDistance(this.currentIndex, edibleGhostIndex);
			if (game.getGhostEdibleTime(ghost) - dist > this.safeDist) {
				edibleGhostIndex = this.getEdibleGhostNextIndex(game, ghost, edibleGhostIndex);
				if (this.isIndexSafe(game, edibleGhostIndex) && this.isLiarIndexSafe(game, edibleGhostIndex)) {
					dist = game.getShortestPathDistance(this.currentIndex, edibleGhostIndex);
					this.setNextIndex(game, dist < this.dist, dist, edibleGhostIndex);
				}
			}
		}
		return this.endComputing(game, false, Color.green, "activeFindEdibleGhost");
	}

	/**
	 * tries to reach safely an edible ghost, but this time we need an escape, this
	 * is adopted when all ghosts are edible, or when the ghosts are not aligned,
	 * ghosts are considered edible if they can be reached in time and in respect of
	 * a safe distance, in order to avoid of being trapped once edible time expires
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean passiveFindEdibleGhost(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (GHOST ghost : GHOST.values()) {
			int edibleGhostIndex = game.getGhostCurrentNodeIndex(ghost);
			int dist = game.getShortestPathDistance(this.currentIndex, edibleGhostIndex);
			if (game.getGhostEdibleTime(ghost) - dist > this.safeDist) {
				edibleGhostIndex = this.getEdibleGhostNextIndex(game, ghost, edibleGhostIndex);
				if (this.isLiarIndexSafe(game, edibleGhostIndex) && this.isNextIndexSafe(game, edibleGhostIndex)) {
					dist = game.getShortestPathDistance(this.currentIndex, edibleGhostIndex);
					this.setNextIndex(game, dist < this.dist, dist, edibleGhostIndex);
				}
			}
		}
		return this.endComputing(game, false, Color.green, "passiveFindEdibleGhost");
	}

	/**
	 * tries to reach nearest pill, does not need safety or an escape, this is
	 * adopted right after eating a power pill, until the next junction is reached,
	 * in order to clear dangerous path, hard to complete without a power pill
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean findNearestPill(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int pillIndex : game.getActivePillsIndices()) {
			int dist = game.getShortestPathDistance(this.currentIndex, pillIndex);
			this.setNextIndex(game, dist < this.dist, dist, pillIndex);
		}
		return this.endComputing(game, false, Color.green, "findNearestPill");
	}

	/**
	 * tries to reach safely the nearest pill, does not need an escape, this is
	 * adopted all pills remaining are reachable safely or when ghosts are aligned,
	 * useful to fast clearing
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean activeFindPill(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int pillIndex : game.getActivePillsIndices())
			if (this.isIndexSafe(game, pillIndex)) {
				int dist = game.getShortestPathDistance(this.currentIndex, pillIndex);
				this.setNextIndex(game, dist < this.dist, dist, pillIndex);
			}
		return this.endComputing(game, false, Color.green, "activeFindPill");
	}

	/**
	 * tries to reach safely the nearest pill, at least two junction for escape are
	 * granted, this is the standard movement logic for ms pacman
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean findPill(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int pillIndex : game.getActivePillsIndices())
			if (this.isIndexSafe(game, pillIndex) && this.isIndexClear(game, pillIndex)) {
				int dist = game.getShortestPathDistance(this.currentIndex, pillIndex);
				this.setNextIndex(game, dist < this.dist, dist, pillIndex);
			}
		return this.endComputing(game, false, Color.green, "findPill");
	}

	/**
	 * tries to reach safely the nearest pill, at least two junction for escape are
	 * granted, this is safer than the standard movement logic and it's adopted when
	 * no risks can be taken
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean passiveFindPill(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int pillIndex : game.getActivePillsIndices())
			if (this.isNextIndexSafe(game, pillIndex)) {
				int dist = game.getShortestPathDistance(this.currentIndex, pillIndex);
				this.setNextIndex(game, dist < this.dist, dist, pillIndex);
			}
		return this.endComputing(game, false, Color.green, "passiveFindPill");
	}

	/**
	 * tries to reach the nearest junction heading to the nearest pill,
	 * this is adopted when no pills or power pills can be reached safely, 
	 * this may create loops in order to wait for a global reverse or a ghost random move
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean findJunction(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int pillIndex : game.getActivePillsIndices()) {
			for (int junctionIndex : game.getJunctionIndices())
				if (junctionIndex != this.forbiddenIndex && this.isIndexSafe(game, junctionIndex)) {
					int dist = game.getShortestPathDistance(this.currentIndex, junctionIndex);
					dist += game.getShortestPathDistance(junctionIndex, pillIndex);
					this.setNextIndex(game, dist < this.dist, dist, junctionIndex);
				}
		}
		return this.endComputing(game, false, Color.green, "findJunction");
	}

	/**
	 * tries to reach safely the nearest power pill, does not need an escape, this
	 * is adopted when no other option other than escape is available, ms pacman
	 * does not wait for ghosts, because they may not get close according to their
	 * unknown strategy
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean findPowerPill(Game game) {
		this.dist = Integer.MAX_VALUE;
		for (int powerPillIndex : game.getActivePowerPillsIndices())
			if (this.isIndexSafe(game, powerPillIndex)) {
				int dist = game.getShortestPathDistance(this.currentIndex, powerPillIndex);
				this.setNextIndex(game, dist < this.dist, dist, powerPillIndex);
			}
		return this.endComputing(game, false, Color.green, "findPowerPill");
	}

	/**
	 * tries to reach safely the farthest junction from ms pacman current index,
	 * this is adopted when no search option is available
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean activeFindEscape(Game game) {
		this.dist = Integer.MIN_VALUE;
		for (int junctionIndex : game.getJunctionIndices())
			if (junctionIndex != forbiddenIndex && this.isIndexSafe(game, junctionIndex)) {
				int dist = game.getShortestPathDistance(this.currentIndex, junctionIndex);
				this.setNextIndex(game, dist > this.dist, dist, junctionIndex);
			}
		return this.endComputing(game, true, Color.blue, "activeFindEscape");
	}

	/**
	 * tries to reach the farthest index from ghosts, this is the last chance for ms
	 * pacman, if this option is not available, it's the end my friend
	 * 
	 * @param game current game
	 * @return true if a new target matching the condition has been found, otherwise
	 *         false
	 */
	private boolean passiveFindEscape(Game game) {
		this.dist = Integer.MIN_VALUE;
		for (int pillIndex : game.getPillIndices())
			if (isIndexSafe(game, pillIndex)) {
				int dist = game.getShortestPathDistance(this.currentIndex, pillIndex);
				this.setNextIndex(game, dist > this.dist, dist, pillIndex);
			}
		return this.endComputing(game, false, Color.blue, "passiveFindEscape");
	}

//	/**
//	 * stays away from the upper zone before the ghosts are out of the liar, this is
//	 * adopted to avoid surprise attack from legacy2 ghosts, bad move for the others
//	 * 
//	 * @param game current game
//	 * @return true if a new target matching the condition has been found, otherwise
//	 *         false
//	 */
//	private boolean findPillAwayFromLair(Game game) {
//		this.dist = Integer.MIN_VALUE;
//		for (int pillIndex : game.getActivePillsIndices()) {
//			int dist = game.getShortestPathDistance(game.getGhostInitialNodeIndex(), pillIndex);
//			this.setNextIndex(game, dist > this.dist, dist, pillIndex);
//		}
//		return this.endComputing(game, false, Color.green, "findPillAwayFromLair");
//	}

	/**
	 * this is a rule based agent, 13 rules manages the actions of ms pacman through
	 * different game states with the aid of 7 different heuristics, since time is
	 * part of the threat against some strategies, actually this agent selects the
	 * next target according to min or max distance from ms pacman, in the next
	 * steps this logic may change in order to give ms pacman a better reasoning
	 * mechanism this agent is a risky one, it stays really close to the ghosts,
	 * which is actually the best option to maximize the score
	 */
	public MOVE getMove(Game game, long timeDue) {
		this.startComputing(game);

//		the minimum number of junctions needed to evaluate an escape plan
		this.escapeJunctions = 2;
//		the minimum distance (and the ideal one) that ms pacman has to keep from the closest ghost, considering that an eat event happens at distance 2
		this.safeDist = 3;
//		the minimum distance that ms pacman has to keep from the closest ghost when she's trying to align them
		this.alignDist = 5;
//		for debug use, to watch in real time which decision has been taken
		this.verbose = false;
//		for debug use, to watch in real time all ms pacman decisions
		this.highlight = false;

		for (GHOST ghost : GHOST.values()) {
			int ghostIndex = game.getGhostCurrentNodeIndex(ghost);
			int dist = game.getShortestPathDistance(this.currentIndex, ghostIndex);
			int edibleTime = game.getGhostEdibleTime(ghost);
//			ghosts are considered edible if they can be reached in time and in respect of a safe distance,
//			in order to avoid of being trapped once edible time expires
			if (edibleTime - dist > this.safeDist) {
				this.edibleGhostsIndices.add(ghostIndex);
//				highlights the shortest path from the edible ghost to ms pacman
				if (this.highlight)
					GameView.addPoints(game, Color.blue, game.getShortestPath(ghostIndex, this.currentIndex));
			} else if (game.getGhostLairTime(ghost) == 0) {
				this.ghostsIndices.add(ghostIndex);
				this.trueGhostsIndices.add(ghostIndex);
//				highlights the shortest path from the ghost to ms pacman
				if (this.highlight)
					GameView.addPoints(game, Color.red, game.getShortestPath(ghostIndex, this.currentIndex));
			} else if (game.getGhostLairTime(ghost) > 0) {
//				fake ghosts to manage traps after ghost respawn
				ghostIndex = game.getGhostInitialNodeIndex();
				this.ghostsIndices.add(ghostIndex);
//				highlights the shortest path from the fake ghost to ms pacman
				if (this.highlight)
					GameView.addPoints(game, Color.red, game.getShortestPath(ghostIndex, this.currentIndex));
			}
		}

//		necessary with legacy2, works good with every strategy except for aggressive ghosts
//		boolean noGhosts = this.trueGhostsIndices.size() + this.edibleGhostsIndices.size() == 0;
//		if (noGhosts && this.findPillAwayFromLair(game))
//			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

		boolean noGhostsInLair = this.trueGhostsIndices.size() + this.edibleGhostsIndices.size() == 4;
		boolean powerPillsAvailable = game.getActivePowerPillsIndices().length > 0;
		if (powerPillsAvailable && game.wasPowerPillEaten())
			this.goNearestPill = true;
		else if (game.isJunction(this.currentIndex)) {
			this.goNearestPill = false;
			this.forbiddenIndex = this.currentIndex;
		}

//		clear a difficult path right after a power pill has been eaten, until the first junction is reached
//		dramatically improves the score
		if (this.goNearestPill && this.findNearestPill(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if there's at least one edible ghost, catch him without taking any risk
		if (this.edibleGhostsIndices.size() > 0 && this.passiveFindEdibleGhost(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if there's at least one edible ghost and all other non-fake ghosts are aligned, catch him careless of the risks
		if (this.edibleGhostsIndices.size() > 0 && this.alignedGhosts(game) && this.activeFindEdibleGhost(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if all remaining pills are reachable safely go eat them to end the level
		if (this.lastPills(game) && this.activeFindPill(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if all ghosts are aligned, eat every pill careless of the risks
		if (this.alignedGhosts(game) && this.activeFindPill(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		as standard movement logic, eat pills by risking only when ghost are close, so they will follow ms pacman
		if (this.findPill(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		as backup movement logic, eat pills without taking any risk at all
		if (this.passiveFindPill(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if the ghosts are out of the liar, find the nearest power pill, to save ms pacman from trouble
		if (noGhostsInLair && powerPillsAvailable && this.findPowerPill(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		less pills are available and the ghosts are not going to make it easy,
//		stay close to the pills and eat them as soon it is possible
		if (this.findJunction(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if the ghosts are a great trouble, ms pacman tries to get close and align them
		if (this.activeAlignGhosts(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if the first option is not available, tries to run to a safe index making the ghost follow her
		if (this.passiveAlignGhosts(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		when no other option is available, ms pacman has to run to the farthest junction
		if (this.activeFindEscape(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		if no junction is available, then we are done, the only way is to go as far as possible from the ghosts
		if (this.passiveFindEscape(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

//		"Close your eyes, go straight and pray for a global reverse or a random move..."
//		Ms.Pac-Man last words.
		return this.myMove;
	}
}