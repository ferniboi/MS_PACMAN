package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;

import java.util.EnumMap;

public class MinimaxPacMan extends Controller<MOVE> {
	private int timeLimit;
	private long startTime;
	private int maxUtility;
	private int maxDepth;
	private int maxIterativeDepth = 0;
	private boolean isLegacy;
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

	private EnumMap<GHOST, MOVE> getNextGhostsMoves(Game game) {
		EnumMap<GHOST, MOVE> nextGhostsMoves = new EnumMap<GHOST, MOVE>(GHOST.class);
		nextGhostsMoves.clear();
		int currentIndex = game.getPacmanCurrentNodeIndex();
		for (GHOST ghost : GHOST.values()) {
			if (game.doesGhostRequireAction(ghost)) {
				int ghostIndex = game.getGhostCurrentNodeIndex(ghost);
				MOVE lastMove = game.getGhostLastMoveMade(ghost);
				MOVE nextMove = null;
				if (this.isLegacy) {
					if (ghost == GHOST.BLINKY)
						nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
								DM.PATH);
					else if (ghost == GHOST.INKY)
						nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
								DM.MANHATTAN);
					else if (ghost == GHOST.PINKY)
						nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
								DM.EUCLID);
					else if (ghost == GHOST.SUE)
						nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove,
								DM.PATH);
				} else
					nextMove = game.getApproximateNextMoveTowardsTarget(ghostIndex, currentIndex, lastMove, DM.PATH);
				nextGhostsMoves.put(ghost, nextMove);
			}
		}
		return nextGhostsMoves;
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

	private int quiescenceSearchMaxi(Game game) {
		if (game.wasPacManEaten())
			return 0;
		if (game.isJunction(game.getPacmanCurrentNodeIndex()))
			return this.getUtility(game);
		int maxUtility = Integer.MIN_VALUE;
		EnumMap<GHOST, MOVE> nextGhostsMoves = this.getNextGhostsMoves(game);
		for (MOVE nextMaxMove : this.getNextMaxMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			Game gameCopy = game.copy();
			gameCopy.advanceGameWithPowerPillReverseOnly(nextMaxMove, nextGhostsMoves);
			maxUtility = Math.max(maxUtility, this.quiescenceSearchMaxi(gameCopy));
		}
		return maxUtility;
	}

	private int maxi(Game game, int depth) {
		if (game.wasPacManEaten())
			return 0;
		if (depth == 0)
			return this.quiescenceSearchMaxi(game);
		int maxUtility = Integer.MIN_VALUE;
		EnumMap<GHOST, MOVE> nextGhostsMoves = this.getNextGhostsMoves(game);
		for (MOVE nextMaxMove : this.getNextMaxMoves(game)) {
			if (System.currentTimeMillis() - this.startTime >= this.timeLimit)
				break;
			if (depth == this.maxDepth)
				this.nextMaxMove = nextMaxMove;
			Game gameCopy = game.copy();
			gameCopy.advanceGameWithPowerPillReverseOnly(nextMaxMove, nextGhostsMoves);
			maxUtility = Math.max(maxUtility, this.maxi(gameCopy, depth - 1));
		}
		return maxUtility;
	}

	private void iterativeDeepeningSearch(Game game) {
		this.timeLimit = 5;
		this.startTime = System.currentTimeMillis();
		this.maxDepth = 100;
		this.maxUtility = Integer.MIN_VALUE;
		while (System.currentTimeMillis() - this.startTime < this.timeLimit && this.maxUtility != 0) {
			this.maxUtility = this.maxi(game, this.maxDepth);
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
		this.isLegacy = false;
		this.iterativeDeepeningSearch(game);
		return this.myMove;
	}
}