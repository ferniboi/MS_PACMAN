package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

import java.util.ArrayList;
import java.util.EnumMap;

public class AlphaBetaPacMan extends Controller<MOVE> {
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

	private ArrayList<EnumMap<GHOST, MOVE>> getNextMinMoves(Game game) {
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

	private int quiescenceSearchMax(Game game, int alpha, int beta) {
		if (game.wasPacManEaten())
			return 0;
		if (game.isJunction(game.getPacmanCurrentNodeIndex()))
			return this.getUtility(game);
		for (MOVE nextMaxMove : this.getNextMaxMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			alpha = Math.max(alpha, this.quiescenceSearchMin(game, nextMaxMove, alpha, beta));
			if (beta <= alpha)
				return alpha;
		}
		return alpha;
	}

	private int quiescenceSearchMin(Game game, MOVE nextMaxMove, int alpha, int beta) {
		if (game.wasPacManEaten())
			return 0;
		if (game.isJunction(game.getPacmanCurrentNodeIndex()))
			return this.getUtility(game);
		for (EnumMap<GHOST, MOVE> nextMinMoves : this.getNextMinMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			Game gameCopy = game.copy();
			gameCopy.advanceGameWithPowerPillReverseOnly(nextMaxMove, nextMinMoves);
			beta = Math.min(beta, this.quiescenceSearchMax(gameCopy, alpha, beta));
			if (beta <= alpha)
				return beta;
		}
		return beta;
	}

	private int alphaBetaMax(Game game, int alpha, int beta, int depth) {
		if (game.wasPacManEaten())
			return 0;
		if (depth == 0)
			return this.quiescenceSearchMax(game, alpha, beta);
		for (MOVE nextMaxMove : this.getNextMaxMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			if (depth == this.maxDepth)
				this.nextMaxMove = nextMaxMove;
			alpha = Math.max(alpha, this.alphaBetaMin(game, nextMaxMove, alpha, beta, depth - 1));
			if (beta <= alpha)
				return alpha;
		}
		return alpha;
	}

	private int alphaBetaMin(Game game, MOVE nextMaxMove, int alpha, int beta, int depth) {
		if (game.wasPacManEaten())
			return 0;
		if (depth == 0)
			return this.quiescenceSearchMin(game, nextMaxMove, alpha, beta);
		for (EnumMap<GHOST, MOVE> nextMinMoves : this.getNextMinMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			Game gameCopy = game.copy();
			gameCopy.advanceGameWithPowerPillReverseOnly(nextMaxMove, nextMinMoves);
			beta = Math.min(beta, this.alphaBetaMax(gameCopy, alpha, beta, depth - 1));
			if (beta <= alpha)
				return beta;
		}
		return beta;
	}

	private void iterativeDeepeningSearch(Game game) {
		this.timeLimit = 5;
		this.startTime = System.currentTimeMillis();
		int alpha = Integer.MIN_VALUE;
		int beta = Integer.MAX_VALUE;
		this.maxDepth = 100;
		this.maxUtility = Integer.MIN_VALUE;
		while (System.currentTimeMillis() - this.startTime < this.timeLimit && this.maxUtility != 0) {
			this.maxUtility = this.alphaBetaMax(game, alpha, beta, this.maxDepth);
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