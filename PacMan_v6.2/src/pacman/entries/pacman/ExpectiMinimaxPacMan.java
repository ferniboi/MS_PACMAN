package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

import java.util.ArrayList;
import java.util.EnumMap;

public class ExpectiMinimaxPacMan extends Controller<MOVE> {
	private int timeLimit;
	private long startTime;
	private int maxUtility;
	private int maxDepth;
	private int maxIterativeDepth = 0;
	private MOVE nextMaxMove;
	private MOVE myMove = MOVE.NEUTRAL;

	private MOVE[] getNextMaxMoves(Game game) {
		int currentIndex = game.getPacmanCurrentNodeIndex();
		if (game.wasPowerPillEaten() || game.getTimeOfLastGlobalReversal() == game.getTotalTime() - 1)
			return game.getPossibleMoves(currentIndex);
		for (GHOST ghost : GHOST.values())
			if (game.wasGhostEaten(ghost))
				return game.getPossibleMoves(currentIndex);
		return game.getPossibleMoves(currentIndex, game.getPacmanLastMoveMade());
	}

	private ArrayList<EnumMap<GHOST, MOVE>> getNextGhostsMoves(Game game) {
		ArrayList<EnumMap<GHOST, MOVE>> nextMinMoves = new ArrayList<EnumMap<GHOST, MOVE>>();
		int currentIndex = game.getPacmanCurrentNodeIndex();
		if (game.doesGhostRequireAction(GHOST.SUE)) {
			int sueGhostIndex = game.getGhostCurrentNodeIndex(GHOST.SUE);
			MOVE sueLastMove = game.getGhostLastMoveMade(GHOST.SUE);
			MOVE[] nextMoves = game.getPossibleMoves(sueGhostIndex, sueLastMove);
			int nStates = nextMoves.length;
			for (int i = 0; i < nStates; i++) {
				nextMinMoves.add(i, new EnumMap<GHOST, MOVE>(GHOST.class));
				if (game.doesGhostRequireAction(GHOST.BLINKY)) {
					int ghostIndex = game.getGhostCurrentNodeIndex(GHOST.BLINKY);
					MOVE lastMove = game.getGhostLastMoveMade(GHOST.BLINKY);
					MOVE nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
							DM.PATH);
					nextMinMoves.get(i).put(GHOST.BLINKY, nextMove);
				}
				if (game.doesGhostRequireAction(GHOST.INKY)) {
					int ghostIndex = game.getGhostCurrentNodeIndex(GHOST.INKY);
					MOVE lastMove = game.getGhostLastMoveMade(GHOST.INKY);
					MOVE nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
							DM.MANHATTAN);
					nextMinMoves.get(i).put(GHOST.INKY, nextMove);
				}
				if (game.doesGhostRequireAction(GHOST.PINKY)) {
					int ghostIndex = game.getGhostCurrentNodeIndex(GHOST.PINKY);
					MOVE lastMove = game.getGhostLastMoveMade(GHOST.PINKY);
					MOVE nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
							DM.EUCLID);
					nextMinMoves.get(i).put(GHOST.PINKY, nextMove);
				}
				nextMinMoves.get(i).put(GHOST.SUE, nextMoves[i]);
			}
		} else {
			EnumMap<GHOST, MOVE> nextGhostsMoves = new EnumMap<GHOST, MOVE>(GHOST.class);
			if (game.doesGhostRequireAction(GHOST.BLINKY)) {
				int ghostIndex = game.getGhostCurrentNodeIndex(GHOST.BLINKY);
				MOVE lastMove = game.getGhostLastMoveMade(GHOST.BLINKY);
				MOVE nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove, DM.PATH);
				nextGhostsMoves.put(GHOST.BLINKY, nextMove);
			}
			if (game.doesGhostRequireAction(GHOST.INKY)) {
				int ghostIndex = game.getGhostCurrentNodeIndex(GHOST.INKY);
				MOVE lastMove = game.getGhostLastMoveMade(GHOST.INKY);
				MOVE nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
						DM.MANHATTAN);
				nextGhostsMoves.put(GHOST.INKY, nextMove);
			}
			if (game.doesGhostRequireAction(GHOST.PINKY)) {
				int ghostIndex = game.getGhostCurrentNodeIndex(GHOST.PINKY);
				MOVE lastMove = game.getGhostLastMoveMade(GHOST.PINKY);
				MOVE nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove, DM.EUCLID);
				nextGhostsMoves.put(GHOST.PINKY, nextMove);
			}
			nextMinMoves.add(nextGhostsMoves);
		}
		return nextMinMoves;
	}

	private int getUtility(Game game) {
		int utility = game.getScore() + game.getNumberOfPills() - game.getNumberOfActivePills()
				- game.getCurrentLevelTime();
		if (utility > this.maxUtility) {
			this.maxUtility = utility;
			this.myMove = this.nextMaxMove;
		}
		return utility;
	}

	private int quiescenceSearchExpectiMax(Game game) {
		if (game.wasPacManEaten())
			return 0;
		if (game.isJunction(game.getPacmanCurrentNodeIndex()))
			return this.getUtility(game);
		int maxUtility = Integer.MIN_VALUE;
		for (MOVE nextMaxMove : this.getNextMaxMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			maxUtility = Math.max(maxUtility, this.quiescenceSearchExpectiMean(game, nextMaxMove));
		}
		return maxUtility;
	}

	private int quiescenceSearchExpectiMean(Game game, MOVE nextMaxMove) {
		if (game.wasPacManEaten())
			return 0;
		if (game.isJunction(game.getPacmanCurrentNodeIndex()))
			return this.getUtility(game);
		int randomUtility = 0;
		ArrayList<EnumMap<GHOST, MOVE>> nextGhostsMoves = this.getNextGhostsMoves(game);
		for (EnumMap<GHOST, MOVE> nextRandomMove : nextGhostsMoves) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			Game gameCopy = game.copy();
			gameCopy.advanceGameWithPowerPillReverseOnly(nextMaxMove, nextRandomMove);
			randomUtility += this.quiescenceSearchExpectiMax(gameCopy);
		}
		return randomUtility / nextGhostsMoves.size();
	}

	private int expectiMax(Game game, int depth) {
		if (game.wasPacManEaten())
			return 0;
		if (depth == 0)
			return this.quiescenceSearchExpectiMax(game);
		int maxUtility = Integer.MIN_VALUE;
		for (MOVE nextMaxMove : this.getNextMaxMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			if (depth == this.maxDepth)
				this.nextMaxMove = nextMaxMove;
			maxUtility = Math.max(maxUtility, this.expectiMean(game, nextMaxMove, depth - 1));
		}
		return maxUtility;
	}

	private int expectiMean(Game game, MOVE nextMaxMove, int depth) {
		if (game.wasPacManEaten())
			return 0;
		if (depth == 0)
			return this.quiescenceSearchExpectiMean(game, nextMaxMove);
		int randomUtility = 0;
		ArrayList<EnumMap<GHOST, MOVE>> nextGhostsMoves = this.getNextGhostsMoves(game);
		for (EnumMap<GHOST, MOVE> nextRandomMove : nextGhostsMoves) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			Game gameCopy = game.copy();
			gameCopy.advanceGameWithPowerPillReverseOnly(nextMaxMove, nextRandomMove);
			randomUtility += this.expectiMax(gameCopy, depth - 1);
		}
		return randomUtility / nextGhostsMoves.size();
	}

	private void iterativeDeepeningSearch(Game game) {
		this.timeLimit = 5;
		this.startTime = System.currentTimeMillis();
		this.maxDepth = 100;
		this.maxUtility = Integer.MIN_VALUE;
		while (System.currentTimeMillis() - this.startTime < this.timeLimit && this.maxUtility != 0) {
			this.maxUtility = this.expectiMax(game, this.maxDepth);
			this.maxDepth += 20;
			if (this.maxDepth > this.maxIterativeDepth) {
				this.maxIterativeDepth = this.maxDepth;
//				System.out.println("iterative depth: " + this.maxIterativeDepth);
			}
		}
//		System.out.println("time: " + (System.currentTimeMillis() - this.startTime));
//		System.out.println("depth: " + this.maxDepth);
	}

	public MOVE getMove(Game game, long timeDue) {
		this.iterativeDeepeningSearch(game);
		return this.myMove;
	}
}